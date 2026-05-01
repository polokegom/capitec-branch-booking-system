package za.co.capitec.booking.application;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;

@ApplicationScoped
@DefaultBean
public class BookingPolicy {
  private final int lookaheadDays;
  private final Clock clock;

  @Inject
  public BookingPolicy(@ConfigProperty(name = "app.booking.lookahead-days", defaultValue = "45") int lookaheadDays) {
    this(lookaheadDays, Clock.systemUTC());
  }

  BookingPolicy(int lookaheadDays, Clock clock) {
    this.lookaheadDays = lookaheadDays;
    this.clock = clock;
  }

  public void validateAvailabilityDate(LocalDate appointmentDate) {
    validateBookingDate(appointmentDate, BookingDateTimes.UTC_ZONE);
  }

  public void validateAvailabilityDate(LocalDate appointmentDate, ZoneId zoneId) {
    validateBookingDate(appointmentDate, zoneId);
  }

  public void validateBookingDate(LocalDate appointmentDate) {
    validateBookingDate(appointmentDate, BookingDateTimes.UTC_ZONE);
  }

  public void validateBookingDate(LocalDate appointmentDate, ZoneId zoneId) {
    if (appointmentDate == null) {
      throw new InvalidBookingRequestException("Appointment date is required.");
    }

    LocalDate earliestDate = today(zoneId);
    LocalDate latestDate = today(zoneId).plusDays(lookaheadDays);

    if (appointmentDate.isBefore(earliestDate)) {
      throw new InvalidBookingRequestException("Appointments cannot be booked for a past date.");
    }

    if (appointmentDate.isAfter(latestDate)) {
      throw new InvalidBookingRequestException("Appointments cannot be booked more than %s days ahead.".formatted(lookaheadDays));
    }

    DayOfWeek dayOfWeek = appointmentDate.getDayOfWeek();
    if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
      throw new InvalidBookingRequestException("Appointments are not available on weekends.");
    }
  }

  public void validateBookingSlot(LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
    validateBookingSlot(appointmentDate, bookingSlotStartTime, BookingDateTimes.UTC_ZONE);
  }

  public void validateBookingSlot(LocalDate appointmentDate, LocalTime bookingSlotStartTime, ZoneId zoneId) {
    if (appointmentDate == null || bookingSlotStartTime == null) {
      return;
    }
    if (appointmentDate.equals(today(zoneId))) {
      if (!bookingSlotStartTime.isAfter(currentTime(zoneId))) {
        throw new InvalidBookingRequestException("The selected timeslot has already started or passed. Please choose a later slot.");
      }
    }
  }

  public boolean isBookingSlotInPast(LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
    return isBookingSlotInPast(appointmentDate, bookingSlotStartTime, BookingDateTimes.UTC_ZONE);
  }

  public boolean isBookingSlotInPast(LocalDate appointmentDate, LocalTime bookingSlotStartTime, ZoneId zoneId) {
    if (appointmentDate == null || bookingSlotStartTime == null) {
      return false;
    }
    if (!appointmentDate.equals(today(zoneId))) {
      return false;
    }
    return !bookingSlotStartTime.isAfter(currentTime(zoneId));
  }

  public LocalDate today() {
    return today(BookingDateTimes.UTC_ZONE);
  }

  public LocalDate today(ZoneId zoneId) {
    return LocalDate.now(clock.withZone(zoneId == null ? BookingDateTimes.UTC_ZONE : zoneId));
  }

  public LocalTime currentTime() {
    return currentTime(BookingDateTimes.UTC_ZONE);
  }

  public LocalTime currentTime(ZoneId zoneId) {
    return LocalTime.now(clock.withZone(zoneId == null ? BookingDateTimes.UTC_ZONE : zoneId));
  }

  public OffsetDateTime currentDateTime() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }
}

