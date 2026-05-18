package za.co.capitec.booking.application.command;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateBookingCommand(
  UUID branchId,
  LocalDateTime startDateTime,
  LocalDateTime endDateTime,
  String customerName,
  String customerEmail,
  String preferredLanguage,
  String idempotencyKey
) {
}
