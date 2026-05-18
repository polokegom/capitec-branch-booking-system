package za.co.capitec.booking.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingSlotAvailability(
  UUID branchId,
  LocalDate appointmentDate,
  LocalTime bookingSlotStartTime
) {}
