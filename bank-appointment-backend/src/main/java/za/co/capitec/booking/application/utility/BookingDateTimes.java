package za.co.capitec.booking.application.utility;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public final class BookingDateTimes {
  public static final ZoneId UTC_ZONE = ZoneOffset.UTC;

  private BookingDateTimes() {
  }

  public static OffsetDateTime toUtc(LocalDate appointmentDate, LocalTime bookingSlotTime) {
    return toUtc(appointmentDate, bookingSlotTime, UTC_ZONE);
  }

  public static OffsetDateTime toUtc(LocalDate appointmentDate, LocalTime bookingSlotTime, ZoneId sourceZone) {
    if (appointmentDate == null || bookingSlotTime == null) {
      return null;
    }
    ZoneId effectiveSourceZone = sourceZone == null ? UTC_ZONE : sourceZone;
    return ZonedDateTime.of(appointmentDate, bookingSlotTime, effectiveSourceZone)
      .withZoneSameInstant(ZoneOffset.UTC)
      .toOffsetDateTime();
  }

  public static OffsetDateTime toUtc(OffsetDateTime value) {
    return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
  }

  public static LocalDate toBookingDate(OffsetDateTime value) {
    return toBookingDate(value, UTC_ZONE);
  }

  public static LocalDate toBookingDate(OffsetDateTime value, ZoneId targetZone) {
    ZoneId effectiveTargetZone = targetZone == null ? UTC_ZONE : targetZone;
    return value == null ? null : value.atZoneSameInstant(effectiveTargetZone).toLocalDate();
  }

  public static LocalTime toBookingTime(OffsetDateTime value) {
    return toBookingTime(value, UTC_ZONE);
  }

  public static LocalTime toBookingTime(OffsetDateTime value, ZoneId targetZone) {
    ZoneId effectiveTargetZone = targetZone == null ? UTC_ZONE : targetZone;
    return value == null ? null : value.atZoneSameInstant(effectiveTargetZone).toLocalTime();
  }

  public static boolean sameInstant(OffsetDateTime first, OffsetDateTime second) {
    if (first == null || second == null) {
      return first == second;
    }
    return first.toInstant().equals(second.toInstant());
  }
}
