package za.co.capitec.booking.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;

class BookingPolicyTest {
  private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-20T08:00:00Z"), BookingDateTimes.UTC_ZONE);

  @Test
  void shouldAllowWeekdayInsideWindow() {
    BookingPolicy bookingPolicy = new BookingPolicy(45, FIXED_CLOCK);

    assertThatCode(() -> bookingPolicy.validateBookingDate(LocalDate.of(2026, 4, 21)))
      .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectWeekendDate() {
    BookingPolicy bookingPolicy = new BookingPolicy(45, FIXED_CLOCK);

    assertThatThrownBy(() -> bookingPolicy.validateBookingDate(LocalDate.of(2026, 4, 25)))
      .isInstanceOf(InvalidBookingRequestException.class)
      .hasMessageContaining("weekends");
  }

  @Test
  void shouldRejectDateBeyondLookaheadWindow() {
    BookingPolicy bookingPolicy = new BookingPolicy(45, FIXED_CLOCK);

    assertThatThrownBy(() -> bookingPolicy.validateBookingDate(LocalDate.of(2026, 6, 20)))
      .isInstanceOf(InvalidBookingRequestException.class)
      .hasMessageContaining("45 days ahead");
  }
}
