package za.co.capitec.booking.api.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingDetailResponse(
  UUID id,
  String bookingReference,
  UUID branchId,
  String branchName,
  String branchCity,
  String branchCountry,
  LocalDateTime startDateTime,
  LocalDateTime endDateTime,
  String customerName,
  String customerEmail,
  String preferredLanguage,
  String status,
  OffsetDateTime createdAt
) {}
