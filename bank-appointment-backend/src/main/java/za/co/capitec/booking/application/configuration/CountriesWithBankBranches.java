package za.co.capitec.booking.application.configuration;

import io.smallrye.config.ConfigMapping;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ConfigMapping(prefix = "app.countries-with-bank-branches")
public interface CountriesWithBankBranches {

  Map<String, Market> markets();

  interface Market {
    String timezone();

    List<String> provinces();
  }

  default List<String> names() {
    return markets().keySet().stream()
      .sorted()
      .toList();
  }

  default boolean isSupported(String country) {
    return country != null && markets().containsKey(country.trim());
  }

  default Map<String, List<String>> provinces() {
    return markets().entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().provinces()));
  }

  default ZoneId zoneIdFor(String country) {
    if (country == null || !markets().containsKey(country.trim())) {
      return ZoneId.of("UTC");
    }
    return ZoneId.of(markets().get(country.trim()).timezone());
  }
}
