package za.co.capitec.booking.application;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import za.co.capitec.booking.application.utility.BookingDateTimes;

@ApplicationScoped
public class SecureRandomBookingReferenceGenerator implements BookingReferenceGenerator {
  private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public String nextReference() {
    StringBuilder builder = new StringBuilder("BKG-")
      .append(LocalDate.now(BookingDateTimes.UTC_ZONE).format(DATE_FORMATTER))
      .append('-');

    for (int index = 0; index < 8; index++) {
      builder.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
    }

    return builder.toString();
  }
}
