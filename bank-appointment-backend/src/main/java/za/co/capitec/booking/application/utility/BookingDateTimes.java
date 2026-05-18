package za.co.capitec.booking.application.utility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class BookingDateTimes {
  public static final ZoneId UTC_ZONE = ZoneOffset.UTC;

  private BookingDateTimes() {
  }

  public static LocalDateTime toDateTime(LocalDate appointmentDate, LocalTime bookingSlotTime) {
    if (appointmentDate == null || bookingSlotTime == null) {
      return null;
    }
    return LocalDateTime.of(appointmentDate, bookingSlotTime);
  }

  public static LocalDate toBookingDate(LocalDateTime value) {
    return value == null ? null : value.toLocalDate();
  }

  public static LocalTime toBookingTime(LocalDateTime value) {
    return value == null ? null : value.toLocalTime();
  }

  public static boolean sameDateTime(LocalDateTime first, LocalDateTime second) {
    if (first == null || second == null) {
      return first == second;
    }
    return first.equals(second);
  }
}
