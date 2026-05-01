package za.co.capitec.booking.application.utility;

import java.util.Locale;

public final class TextSanitizer {
  private TextSanitizer() {
  }

  public static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public static String trimToLower(String value) {
    String trimmed = trimToNull(value);
    return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
  }

  public static boolean containsIgnoreCase(String value, String search) {
    return value != null && search != null && value.toLowerCase(Locale.ROOT).contains(search);
  }
}
