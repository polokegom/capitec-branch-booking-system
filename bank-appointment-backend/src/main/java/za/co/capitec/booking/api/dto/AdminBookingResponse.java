package za.co.capitec.booking.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminBookingResponse(
  UUID id,
  String bookingReference,
  UUID branchId,
  String branchName,
  String branchCity,
  String branchCountry,
  OffsetDateTime startDateTime,
  OffsetDateTime endDateTime,
  String customerName,
  String customerEmail,
  String preferredLanguage,
  String status,
  OffsetDateTime createdAt
) {}
