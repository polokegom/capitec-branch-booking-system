package za.co.capitec.booking.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookingSlotAvailabilityResponse(
  UUID branchId,
  LocalDateTime startDateTime,
  LocalDateTime endDateTime
) {}
