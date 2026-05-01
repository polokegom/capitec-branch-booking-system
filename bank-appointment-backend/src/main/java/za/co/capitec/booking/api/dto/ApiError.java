package za.co.capitec.booking.api.dto;

import java.time.OffsetDateTime;

public record ApiError(
  String errorCode,
  String message,
  OffsetDateTime timestamp
) {
}
