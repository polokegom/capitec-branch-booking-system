package za.co.capitec.booking.application.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateBookingCommand(
  UUID branchId,
  OffsetDateTime startDateTime,
  OffsetDateTime endDateTime,
  String customerName,
  String customerEmail,
  String preferredLanguage,
  String idempotencyKey
) {
}
