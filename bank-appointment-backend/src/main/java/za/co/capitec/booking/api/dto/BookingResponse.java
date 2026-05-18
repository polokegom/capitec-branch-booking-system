package za.co.capitec.booking.api.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingResponse(
  UUID bookingId,
  String bookingReference,
  UUID branchId,
  LocalDateTime startDateTime,
  LocalDateTime endDateTime,
  String customerName,
  String customerEmail,
  String preferredLanguage,
  String status,
  OffsetDateTime createdAt
) {}
