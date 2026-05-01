package za.co.capitec.booking.application.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import za.co.capitec.booking.application.BookingPolicy;
import za.co.capitec.booking.application.BookingReferenceGenerator;
import za.co.capitec.booking.application.command.CreateBookingCommand;
import za.co.capitec.booking.application.configuration.CountriesWithBankBranches;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.application.port.BranchCatalog;
import za.co.capitec.booking.application.port.BookingSlotInventoryRepository;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.exception.BookingNotFoundException;
import za.co.capitec.booking.domain.exception.BookingReferenceCollisionException;
import za.co.capitec.booking.domain.exception.BranchNotFoundException;
import za.co.capitec.booking.domain.exception.CustomerHasActiveBookingException;
import za.co.capitec.booking.domain.exception.DuplicateBookingRequestException;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;
import za.co.capitec.booking.domain.exception.BookingSlotUnavailableException;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingStatus;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;

@ApplicationScoped
@RequiredArgsConstructor
public class BookingCommandService {
  private final BookingRepository bookingRepository;
  private final BranchCatalog branchCatalog;
  private final BookingSlotInventoryRepository bookingSlotInventoryRepository;
  private final BookingPolicy bookingPolicy;
  private final BookingReferenceGenerator bookingReferenceGenerator;
  private final BookingNotificationService bookingNotificationService;
  private final CountriesWithBankBranches countriesWithBankBranches;

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
      .chain(persisted -> branchCatalog.findById(persisted.branchId())
        .map(branch -> cancelledBooking(persisted, branch.orElse(null))))
      .call(cancelledBooking -> bookingSlotInventoryRepository.releaseBookingSlot(
          cancelledBooking.booking().branchId(),
          BookingDateTimes.toBookingDate(cancelledBooking.booking().startDateTime(), cancelledBooking.branchZone()),
          BookingDateTimes.toBookingTime(cancelledBooking.booking().startDateTime(), cancelledBooking.branchZone())
        )
        .replaceWithVoid())
      .invoke(cancelledBooking -> bookingNotificationService.sendCancellationEmail(cancelledBooking.booking(), cancelledBooking.branch()))
      .map(CancelledBooking::booking);
  }

  private Uni<Booking> createNewBooking(CreateBookingCommand command) {
    return ensureCustomerHasNoActiveBooking(command)
      .chain(() -> branchCatalog.findById(command.branchId()))
      .map(branch -> branch.orElseThrow(() -> new BranchNotFoundException(command.branchId())))
      .chain(branch -> {
        ZoneId branchZone = branchZone(branch);
        LocalDate appointmentDate = BookingDateTimes.toBookingDate(command.startDateTime(), branchZone);
        LocalTime bookingSlotStartTime = BookingDateTimes.toBookingTime(command.startDateTime(), branchZone);
        bookingPolicy.validateBookingDate(appointmentDate, branchZone);
        bookingPolicy.validateBookingSlot(appointmentDate, bookingSlotStartTime, branchZone);
        return bookingSlotInventoryRepository.ensureInventory(command.branchId(), appointmentDate)
          .chain(() -> bookingSlotInventoryRepository.findBookingSlot(command.branchId(), appointmentDate, bookingSlotStartTime))
          .map(bookingSlot -> bookingSlot.orElseThrow(() -> new BookingSlotUnavailableException("The requested booking slot does not exist for the selected branch and date.")))
          .chain(bookingSlotAvailability -> reserveBookingSlotAndPersist(command, bookingSlotAvailability, branch, branchZone));
      });
  }

  private Uni<Void> ensureCustomerHasNoActiveBooking(CreateBookingCommand command) {
    if (command.customerEmail() == null || command.customerEmail().isBlank()) {
      return Uni.createFrom().voidItem();
    }
    return bookingRepository.findUpcomingByCustomerEmail(
        command.customerEmail(),
        bookingPolicy.currentDateTime()
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

  private Uni<Booking> reserveBookingSlotAndPersist(
    CreateBookingCommand command,
    BookingSlotAvailability bookingSlotAvailability,
    Branch branch,
    ZoneId branchZone
  ) {
    validateRequestedDateTimes(command, bookingSlotAvailability, branchZone);
    return bookingSlotInventoryRepository.reserveBookingSlot(command.branchId(), bookingSlotAvailability.appointmentDate(), bookingSlotAvailability.bookingSlotStartTime())
      .chain(reserved -> {
        if (!reserved) {
          return bookingRepository.findByIdempotencyKey(command.idempotencyKey())
            .map(existing -> existing.orElseThrow(() -> new BookingSlotUnavailableException("The requested booking slot is no longer available.")));
        }
        return saveWithReferenceRetry(command, branch, 0);
      });
  }

  private Uni<Booking> saveWithReferenceRetry(CreateBookingCommand command, Branch branch, int attempt) {
    Booking booking = Booking.builder()
      .id(UUID.randomUUID())
      .bookingReference(bookingReferenceGenerator.nextReference())
      .idempotencyKey(command.idempotencyKey())
      .branchId(command.branchId())
      .startDateTime(BookingDateTimes.toUtc(command.startDateTime()))
      .endDateTime(BookingDateTimes.toUtc(command.endDateTime()))
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

    OffsetDateTime appointmentStart = BookingDateTimes.toUtc(booking.startDateTime());
    if (!appointmentStart.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
      throw new InvalidBookingRequestException("This appointment has already started or passed and cannot be cancelled.");
    }
  }

  private Booking cancelledCopy(Booking booking) {
    return booking.toBuilder().status(BookingStatus.CANCELLED).build();
  }

  private void validateRequestedDateTimes(CreateBookingCommand command, BookingSlotAvailability bookingSlotAvailability, ZoneId branchZone) {
    OffsetDateTime expectedStart = BookingDateTimes.toUtc(
      bookingSlotAvailability.appointmentDate(),
      bookingSlotAvailability.bookingSlotStartTime(),
      branchZone
    );
    OffsetDateTime expectedEnd = BookingDateTimes.toUtc(
      bookingSlotAvailability.appointmentDate(),
      bookingSlotAvailability.bookingSlotEndTime(),
      branchZone
    );

    if (!BookingDateTimes.sameInstant(command.startDateTime(), expectedStart)
      || !BookingDateTimes.sameInstant(command.endDateTime(), expectedEnd)) {
      throw new InvalidBookingRequestException("Requested start and end date times do not match the selected slot.");
    }
  }

  private ZoneId branchZone(Branch branch) {
    return branch == null ? BookingDateTimes.UTC_ZONE : countriesWithBankBranches.zoneIdFor(branch.country());
  }

  private CancelledBooking cancelledBooking(Booking booking, Branch branch) {
    return new CancelledBooking(booking, branch, branchZone(branch));
  }

  private record CancelledBooking(Booking booking, Branch branch, ZoneId branchZone) {
  }
}
