package za.co.capitec.booking.domain.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record Booking(
  UUID id,
  String bookingReference,
  String idempotencyKey,
  UUID branchId,
  LocalDateTime startDateTime,
  LocalDateTime endDateTime,
  String customerName,
  String customerEmail,
  String preferredLanguage,
  BookingStatus status,
  OffsetDateTime createdAt
) {
}
