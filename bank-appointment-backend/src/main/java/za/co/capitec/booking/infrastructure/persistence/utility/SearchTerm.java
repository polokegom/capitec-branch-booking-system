package za.co.capitec.booking.infrastructure.persistence.utility;

import java.util.Locale;

public record SearchTerm(String text) {
  public static SearchTerm from(String rawSearchText) {
    if (rawSearchText == null || rawSearchText.isBlank()) {
      return new SearchTerm(null);
    }
    return new SearchTerm(rawSearchText.trim().toLowerCase(Locale.ROOT));
  }

  public boolean isBlank() {
    return text == null;
  }

  public String likePattern() {
    return "%" + text + "%";
  }
}
