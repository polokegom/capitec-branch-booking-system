package za.co.capitec.booking.application.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import za.co.capitec.booking.application.BookingPolicy;
import za.co.capitec.booking.application.configuration.CountriesWithBankBranches;
import za.co.capitec.booking.application.port.BranchCatalog;
import za.co.capitec.booking.application.port.BookingSlotInventoryRepository;
import za.co.capitec.booking.domain.exception.BranchNotFoundException;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;

@ApplicationScoped
@RequiredArgsConstructor
public class AvailabilityQueryService {
  private final BranchCatalog branchCatalog;
  private final BookingSlotInventoryRepository bookingSlotInventoryRepository;
  private final BookingPolicy bookingPolicy;
  private final CountriesWithBankBranches countriesWithBankBranches;

  public Uni<BookingSlotAvailabilityResult> findAvailability(UUID branchId, LocalDate appointmentDate) {
    if (branchId == null) {
      throw new InvalidBookingRequestException("Branch id is required.");
    }

    return branchCatalog.findById(branchId)
      .map(branch -> branch.orElseThrow(() -> new BranchNotFoundException(branchId)))
      .chain(branch -> {
        ZoneId branchZone = countriesWithBankBranches.zoneIdFor(branch.country());
        bookingPolicy.validateAvailabilityDate(appointmentDate, branchZone);
        return bookingSlotInventoryRepository.ensureInventory(branchId, appointmentDate)
          .chain(() -> bookingSlotInventoryRepository.findAvailability(branchId, appointmentDate))
          .map(bookingSlots -> bookingSlots.stream()
            .filter(bookingSlot -> !bookingPolicy.isBookingSlotInPast(
              appointmentDate,
              bookingSlot.bookingSlotStartTime(),
              branchZone
            ))
            .toList())
          .map(bookingSlots -> new BookingSlotAvailabilityResult(branchZone, bookingSlots));
      });
  }

  public record BookingSlotAvailabilityResult(ZoneId branchZone, List<BookingSlotAvailability> bookingSlots) {
  }
}
