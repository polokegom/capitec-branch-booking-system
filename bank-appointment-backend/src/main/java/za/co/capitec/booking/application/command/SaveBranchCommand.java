package za.co.capitec.booking.application.command;

import java.time.LocalTime;

public record SaveBranchCommand(
  String code,
  String name,
  String city,
  String province,
  String address,
  String country,
  LocalTime openingTime,
  LocalTime closingTime,
  String adminEmail
) {
}
