package za.co.capitec.booking.domain.model;

import java.time.LocalTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record Branch(
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
) {
}
