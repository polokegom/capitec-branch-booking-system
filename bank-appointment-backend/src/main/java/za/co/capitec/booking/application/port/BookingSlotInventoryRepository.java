package za.co.capitec.booking.application.port;

import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;

public interface BookingSlotInventoryRepository {
  Uni<Void> ensureInventory(UUID branchId, LocalDate appointmentDate);

  Uni<List<BookingSlotAvailability>> findAvailability(UUID branchId, LocalDate appointmentDate);

  Uni<Optional<BookingSlotAvailability>> findBookingSlot(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime);

  Uni<Boolean> reserveBookingSlot(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime);

  Uni<Boolean> releaseBookingSlot(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime);
}
