package za.co.capitec.booking.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingSlotAvailabilityResponse(
  UUID branchId,
  OffsetDateTime startDateTime,
  OffsetDateTime endDateTime,
  int capacity,
  int reservedCount,
  int remainingCapacity
) {}
