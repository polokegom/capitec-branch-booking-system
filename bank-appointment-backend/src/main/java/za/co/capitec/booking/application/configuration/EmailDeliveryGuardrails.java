package za.co.capitec.booking.application.configuration;

import io.quarkus.mailer.Mail;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EmailDeliveryGuardrails {
  private static final String SES_CONFIGURATION_SET_HEADER = "X-SES-CONFIGURATION-SET";

  @ConfigProperty(name = "app.mail.ses.configuration-set")
  Optional<String> sesConfigurationSet;

  public Mail apply(Mail mail) {
    if (mail == null) {
      return null;
    }
    String configurationSet = sesConfigurationSet.map(String::trim).orElse("");
    if (!configurationSet.isEmpty()) {
      mail.addHeader(SES_CONFIGURATION_SET_HEADER, configurationSet);
    }
    return mail;
  }
}
