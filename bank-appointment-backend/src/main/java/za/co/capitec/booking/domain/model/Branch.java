package za.co.capitec.booking.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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
  public static final int SLOT_MINUTES = 30;

  public List<BookingSlotAvailability> bookingSlots(LocalDate appointmentDate) {
    List<BookingSlotAvailability> slots = new ArrayList<>();
    LocalTime cursor = openingTime;
    while (cursor.plusMinutes(SLOT_MINUTES).compareTo(closingTime) <= 0) {
      slots.add(new BookingSlotAvailability(id, appointmentDate, cursor));
      cursor = cursor.plusMinutes(SLOT_MINUTES);
    }
    return slots;
  }
}
