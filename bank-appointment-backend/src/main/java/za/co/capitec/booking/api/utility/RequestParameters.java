package za.co.capitec.booking.api.utility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;

public final class RequestParameters {
  private RequestParameters() {
  }

  public static DateTimeRange optionalDateRange(String startDate, String endDate) {
    boolean hasStartDate = hasText(startDate);
    boolean hasEndDate = hasText(endDate);
    if (!hasStartDate && !hasEndDate) {
      return DateTimeRange.empty();
    }
    if (!hasStartDate || !hasEndDate) {
      throw new InvalidBookingRequestException("Both startDate and endDate are required for date range filters.");
    }
    LocalDate parsedStartDate = LocalDate.parse(startDate);
    LocalDate parsedEndDate = LocalDate.parse(endDate);
    if (parsedEndDate.isBefore(parsedStartDate)) {
      throw new InvalidBookingRequestException("endDate must be on or after startDate.");
    }
    return new DateTimeRange(
      BookingDateTimes.toDateTime(parsedStartDate, LocalTime.MIN),
      BookingDateTimes.toDateTime(parsedEndDate, LocalTime.of(23, 59))
    );
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record DateTimeRange(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    public static DateTimeRange empty() {
      return new DateTimeRange(null, null);
    }
  }
}