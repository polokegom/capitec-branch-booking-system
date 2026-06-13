package za.co.capitec.booking.application.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import za.co.capitec.booking.application.BookingPolicy;
import za.co.capitec.booking.application.BookingReferenceGenerator;
import za.co.capitec.booking.application.command.CreateBookingCommand;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.application.port.BranchRepository;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.exception.BookingNotFoundException;
import za.co.capitec.booking.domain.exception.BookingReferenceCollisionException;
import za.co.capitec.booking.domain.exception.BookingSlotUnavailableException;
import za.co.capitec.booking.domain.exception.BranchNotFoundException;
import za.co.capitec.booking.domain.exception.CustomerHasActiveBookingException;
import za.co.capitec.booking.domain.exception.DuplicateBookingRequestException;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;
import za.co.capitec.booking.domain.model.BookingStatus;
import za.co.capitec.booking.domain.model.Branch;

@ApplicationScoped
@RequiredArgsConstructor
public class BookingCommandService {
  private final BookingRepository bookingRepository;
  private final BranchRepository branchRepository;
  private final BookingPolicy bookingPolicy;
  private final BookingReferenceGenerator bookingReferenceGenerator;
  private final BookingNotificationService bookingNotificationService;

  public Uni<Booking> createBooking(CreateBookingCommand command) {
    return bookingRepository.findByIdempotencyKey(command.idempotencyKey())
      .chain(existingBooking -> existingBooking
        .map(Uni.createFrom()::item)
        .orElseGet(() -> createNewBooking(command)));
  }

  public Uni<Booking> cancelBooking(String bookingReference, String callerEmail) {
    return bookingRepository.findByReference(bookingReference)
      .map(booking -> booking.orElseThrow(() -> new BookingNotFoundException(bookingReference)))
      .invoke(booking -> validateCancellation(booking, bookingReference, callerEmail))
      .map(this::cancelledCopy)
      .chain(bookingRepository::update)
      .chain(persisted -> branchRepository.findById(persisted.branchId())
        .map(branch -> new CancelledBooking(persisted, branch.orElse(null))))
      .invoke(cancelledBooking -> bookingNotificationService.sendCancellationEmail(cancelledBooking.booking(), cancelledBooking.branch()))
      .map(CancelledBooking::booking);
  }

  private Uni<Booking> createNewBooking(CreateBookingCommand command) {
    return branchRepository.findById(command.branchId())
      .map(branch -> branch.orElseThrow(() -> new BranchNotFoundException(command.branchId())))
      .chain(branch -> ensureCustomerHasNoActiveBooking(command, branch)
        .chain(() -> {
          LocalDate appointmentDate = BookingDateTimes.toBookingDate(command.startDateTime());
          LocalTime bookingSlotStartTime = BookingDateTimes.toBookingTime(command.startDateTime());
          bookingPolicy.validateBookingDate(appointmentDate);
          bookingPolicy.validateBookingSlot(appointmentDate, bookingSlotStartTime, branch.country());
          BookingSlotAvailability requestedSlot = branch.bookingSlots(appointmentDate).stream()
            .filter(slot -> slot.bookingSlotStartTime().equals(bookingSlotStartTime))
            .findFirst()
            .orElseThrow(() -> new BookingSlotUnavailableException("The requested booking slot does not exist for the selected branch and date."));
          validateRequestedDateTimes(command, requestedSlot);
          return bookingRepository.existsConfirmedBookingAt(command.branchId(), command.startDateTime())
            .chain(taken -> taken
              ? Uni.createFrom().failure(new BookingSlotUnavailableException("The requested booking slot is no longer available."))
              : saveWithReferenceRetry(command, branch, 0));
        }));
  }

  private Uni<Void> ensureCustomerHasNoActiveBooking(CreateBookingCommand command, Branch branch) {
    if (command.customerEmail() == null || command.customerEmail().isBlank()) {
      return Uni.createFrom().voidItem();
    }
    return bookingRepository.findUpcomingByCustomerEmail(
        command.customerEmail(),
        bookingPolicy.currentDateTime(branch.country())
      )
      .invoke(activeBooking -> activeBooking.ifPresent(active -> {
        throw new CustomerHasActiveBookingException(
          "You already have an upcoming appointment ("
            + active.bookingReference()
            + " on "
            + BookingDateTimes.toBookingDate(active.startDateTime())
            + " at "
            + BookingDateTimes.toBookingTime(active.startDateTime())
            + "). Please cancel it before booking another timeslot."
        );
      }))
      .replaceWithVoid();
  }

  private Uni<Booking> saveWithReferenceRetry(CreateBookingCommand command, Branch branch, int attempt) {
    Booking booking = Booking.builder()
      .id(UUID.randomUUID())
      .bookingReference(bookingReferenceGenerator.nextReference())
      .idempotencyKey(command.idempotencyKey())
      .branchId(command.branchId())
      .startDateTime(command.startDateTime())
      .endDateTime(command.endDateTime())
      .customerName(command.customerName())
      .customerEmail(command.customerEmail())
      .preferredLanguage(command.preferredLanguage())
      .status(BookingStatus.CONFIRMED)
      .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
      .build();

    return bookingRepository.save(booking)
      .invoke(savedBooking -> bookingNotificationService.sendConfirmationEmail(savedBooking, branch))
      .onFailure(DuplicateBookingRequestException.class)
      .recoverWithUni(duplicateRequestException -> bookingRepository.findByIdempotencyKey(command.idempotencyKey())
        .map(existing -> existing.orElseThrow(() -> (DuplicateBookingRequestException) duplicateRequestException)))
      .onFailure(BookingReferenceCollisionException.class)
      .recoverWithUni(collisionException -> attempt >= 2
        ? Uni.createFrom().failure(collisionException)
        : saveWithReferenceRetry(command, branch, attempt + 1));
  }

  private void validateCancellation(Booking booking, String bookingReference, String callerEmail) {
    if (booking.customerEmail() == null || !booking.customerEmail().equalsIgnoreCase(callerEmail)) {
      throw new BookingNotFoundException(bookingReference);
    }
    if (booking.status() != BookingStatus.CONFIRMED) {
      throw new InvalidBookingRequestException("This booking is not active and cannot be cancelled.");
    }

    LocalDateTime appointmentStart = booking.startDateTime();
    if (!appointmentStart.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
      throw new InvalidBookingRequestException("This appointment has already started or passed and cannot be cancelled.");
    }
  }

  private Booking cancelledCopy(Booking booking) {
    return booking.toBuilder().status(BookingStatus.CANCELLED).build();
  }

  private void validateRequestedDateTimes(CreateBookingCommand command, BookingSlotAvailability requestedSlot) {
    LocalDateTime expectedStart = BookingDateTimes.toDateTime(
      requestedSlot.appointmentDate(),
      requestedSlot.bookingSlotStartTime()
    );
    LocalDateTime expectedEnd = BookingDateTimes.toDateTime(
      requestedSlot.appointmentDate(),
      requestedSlot.bookingSlotStartTime().plusMinutes(Branch.SLOT_MINUTES)
    );

    if (!BookingDateTimes.sameDateTime(command.startDateTime(), expectedStart)
      || !BookingDateTimes.sameDateTime(command.endDateTime(), expectedEnd)) {
      throw new InvalidBookingRequestException("Requested start and end date times do not match the selected slot.");
    }
  }

  private record CancelledBooking(Booking booking, Branch branch) {
  }
}
