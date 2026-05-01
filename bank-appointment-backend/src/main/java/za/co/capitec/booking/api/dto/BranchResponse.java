package za.co.capitec.booking.api.dto;

import java.time.LocalTime;
import java.util.UUID;

public record BranchResponse(
  UUID id,
  String code,
  String name,
  String city,
  String province,
  String country,
  String address,
  LocalTime openingTime,
  LocalTime closingTime,
  boolean active,
  String adminEmail
) {}
