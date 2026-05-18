package za.co.capitec.booking.application.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import za.co.capitec.booking.application.BookingPolicy;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.application.port.BranchRepository;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.exception.BranchNotFoundException;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;
import za.co.capitec.booking.domain.model.Branch;

@ApplicationScoped
@RequiredArgsConstructor
public class AvailabilityQueryService {
  private final BranchRepository branchRepository;
  private final BookingRepository bookingRepository;
  private final BookingPolicy bookingPolicy;

  public Uni<List<BookingSlotAvailability>> findAvailability(UUID branchId, LocalDate appointmentDate) {
    // perform Business validations
    if (branchId == null) {
      throw new InvalidBookingRequestException("Branch id is required.");
    }
    bookingPolicy.validateAvailabilityDate(appointmentDate);

    // Implement business logic
    return branchRepository.findById(branchId)
      .map(branch -> branch.orElseThrow(() -> new BranchNotFoundException(branchId)))
      .chain(branch -> {
        return findBookedStartTimes(branch, appointmentDate)
          .map(bookedStartTimes -> availableSlots(branch, appointmentDate, bookedStartTimes));
      });
  }

  private Uni<Set<LocalTime>> findBookedStartTimes(Branch branch, LocalDate appointmentDate) {
    LocalDateTime startInclusive = BookingDateTimes.toDateTime(appointmentDate, LocalTime.MIDNIGHT);
    LocalDateTime endExclusive = BookingDateTimes.toDateTime(appointmentDate.plusDays(1), LocalTime.MIDNIGHT);
    return bookingRepository.findConfirmedStartDateTimes(branch.id(), startInclusive, endExclusive)
      .map(times -> times.stream().map(BookingDateTimes::toBookingTime).collect(Collectors.toSet()));
  }

  private List<BookingSlotAvailability> availableSlots(
    Branch branch,
    LocalDate appointmentDate,
    Set<LocalTime> alreadyBookedStartTimes
  ) {
    return branch.bookingSlots(appointmentDate).stream()
      .filter(slot -> !bookingPolicy.isBookingSlotInPast(appointmentDate, slot.bookingSlotStartTime(), branch.country()))
      .filter(slot -> !alreadyBookedStartTimes.contains(slot.bookingSlotStartTime()))
      .toList();
  }

}
