package za.co.capitec.booking.application;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import za.co.capitec.booking.application.configuration.CountriesWithBankBranches;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;

@ApplicationScoped
@DefaultBean
public class BookingPolicy {
  private final int lookaheadDays;
  private final Clock clock;
  private final CountriesWithBankBranches countries;

  @Inject
  public BookingPolicy(
    @ConfigProperty(name = "app.booking.lookahead-days", defaultValue = "45") int lookaheadDays,
    CountriesWithBankBranches countries
  ) {
    this(lookaheadDays, Clock.systemDefaultZone(), countries);
  }

  BookingPolicy(int lookaheadDays, Clock clock) {
    this(lookaheadDays, clock, null);
  }

  BookingPolicy(int lookaheadDays, Clock clock, CountriesWithBankBranches countries) {
    this.lookaheadDays = lookaheadDays;
    this.clock = clock;
    this.countries = countries;
  }

  private ZoneId zoneFor(String country) {
    return countries != null ? countries.zoneIdFor(country) : clock.getZone();
  }

  public void validateAvailabilityDate(LocalDate appointmentDate) {
    validateBookingDate(appointmentDate);
  }

  /**
   * Ensures that business rules around booking dates are followed
   */
  public void validateBookingDate(LocalDate appointmentDate) {
    
    if (appointmentDate == null) {
      throw new InvalidBookingRequestException("Appointment date is required.");
    }

    LocalDate earliestDate = LocalDate.now(clock);
    LocalDate latestDate = LocalDate.now(clock).plusDays(lookaheadDays);

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

  public void validateBookingSlot(LocalDate appointmentDate, LocalTime bookingSlotStartTime, String country) {
    if (appointmentDate == null || bookingSlotStartTime == null) {
      return;
    }
    ZoneId zone = zoneFor(country);
    if (appointmentDate.equals(today(zone))) {
      if (!bookingSlotStartTime.isAfter(currentTime(zone))) {
        throw new InvalidBookingRequestException("The selected timeslot has already started or passed. Please choose a later slot.");
      }
    }
  }

  public boolean isBookingSlotInPast(LocalDate appointmentDate, LocalTime bookingSlotStartTime, String country) {
    if (appointmentDate == null || bookingSlotStartTime == null) {
      return false;
    }
    ZoneId zone = zoneFor(country);
    if (!appointmentDate.equals(today(zone))) {
      return false;
    }
    return !bookingSlotStartTime.isAfter(currentTime(zone));
  }

  public LocalDate today(ZoneId zone) {
    return LocalDate.now(clock.withZone(zone));
  }

  public LocalTime currentTime(ZoneId zone) {
    return LocalTime.now(clock.withZone(zone));
  }

  public LocalDateTime currentDateTime(String country) {
    return LocalDateTime.now(clock.withZone(zoneFor(country)));
  }

  public OffsetDateTime currentTimestamp() {
    return OffsetDateTime.now(clock);
  }
}

