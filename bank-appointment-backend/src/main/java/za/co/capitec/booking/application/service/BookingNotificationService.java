
package za.co.capitec.booking.application.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import za.co.capitec.booking.application.configuration.EmailDeliveryGuardrails;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.Branch;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class BookingNotificationService {
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  private static final String CONFIRMATION_TEMPLATE_RESOURCE = "email/emailConfirmationTemplate.html";
  private static final String CANCELLATION_TEMPLATE_RESOURCE = "email/emailCancellationTemplate.html";
  private static final String ADMIN_ROLE_ASSIGNED_TEMPLATE_RESOURCE = "email/adminRoleAssignedTemplate.html";
  private static final String ADMIN_ROLE_REMOVED_TEMPLATE_RESOURCE = "email/adminRoleRemovedTemplate.html";

  private static final String TOKEN_EMAIL_ASSETS_BASE_URL = "{{emailAssetsBaseUrl}}";
  private static final String TOKEN_CUSTOMER_NAME = "{{customerName}}";
  private static final String TOKEN_BOOKING_REFERENCE = "{{bookingReference}}";
  private static final String TOKEN_BRANCH_LABEL = "{{branchLabel}}";
  private static final String TOKEN_APPOINTMENT_DATE = "{{appointmentDate}}";
  private static final String TOKEN_APPOINTMENT_TIME = "{{appointmentTime}}";
  private static final String TOKEN_ADMIN_NAME = "{{adminName}}";

  private final ReactiveMailer reactiveMailer;
  private final EmailDeliveryGuardrails deliveryGuardrails;

  @ConfigProperty(name = "app.email-assets.base-url")
  String emailAssetsBaseUrl;

  private String confirmationTemplate;
  private String cancellationTemplate;
  private String adminRoleAssignedTemplate;
  private String adminRoleRemovedTemplate;

  @PostConstruct
  void loadAssets() {
    this.confirmationTemplate = requireTemplate(CONFIRMATION_TEMPLATE_RESOURCE);
    this.cancellationTemplate = requireTemplate(CANCELLATION_TEMPLATE_RESOURCE);
    this.adminRoleAssignedTemplate = requireTemplate(ADMIN_ROLE_ASSIGNED_TEMPLATE_RESOURCE);
    this.adminRoleRemovedTemplate = requireTemplate(ADMIN_ROLE_REMOVED_TEMPLATE_RESOURCE);
  }

  private static String requireTemplate(String resource) {
    String template = readClasspathString(resource);
    if (template == null) {
      throw new IllegalStateException("Email template not found on classpath: " + resource);
    }
    return template;
  }

  public void sendCancellationEmail(Booking booking, Branch branch) {
    String branchLabel = resolveBranchLabel(booking, branch);
    String subject = "Your Capitec branch appointment is cancelled - " + booking.bookingReference();
    AppointmentDisplay appointment = appointmentDisplay(booking);
    String html = renderCancellationHtml(booking, branchLabel, appointment);
    sendMail(
      Mail.withHtml(booking.customerEmail(), subject, html),
      () -> log.info("Sent booking cancellation email to {} for reference {}", booking.customerEmail(), booking.bookingReference()),
      exception -> log.warn("Failed to send cancellation email for booking {}", booking.bookingReference(), exception)
    );
  }

  private String renderCancellationHtml(Booking booking, String branchLabel, AppointmentDisplay appointment) {
    return renderTemplate(cancellationTemplate, Map.of(
      TOKEN_CUSTOMER_NAME, escape(booking.customerName()),
      TOKEN_BOOKING_REFERENCE, escape(booking.bookingReference()),
      TOKEN_BRANCH_LABEL, escape(branchLabel),
      TOKEN_APPOINTMENT_DATE, appointment.date(),
      TOKEN_APPOINTMENT_TIME, appointment.time()
    ));
  }

  public void sendAdminRoleAssignedEmail(String adminEmail, String adminName, String branchLabel) {
    String subject = "You have been assigned as a Capitec branch admin";
    String html = renderAdminRoleAssignedHtml(adminName, branchLabel);
    sendMail(
      Mail.withHtml(adminEmail, subject, html),
      () -> log.info("Sent admin role assigned email to {}", adminEmail),
      exception -> log.warn("Failed to send admin role assigned email to {}", adminEmail, exception)
    );
  }

  private String renderAdminRoleAssignedHtml(String adminName, String branchLabel) {
    return renderTemplate(adminRoleAssignedTemplate, Map.of(
      TOKEN_ADMIN_NAME, escape(adminName),
      TOKEN_BRANCH_LABEL, escape(branchLabel)
    ));
  }

  public void sendAdminRoleRemovedEmail(String adminEmail, String adminName, String branchLabel) {
    String subject = "Your Capitec branch admin role has been removed";
    String html = renderAdminRoleRemovedHtml(adminName, branchLabel);
    sendMail(
      Mail.withHtml(adminEmail, subject, html),
      () -> log.info("Sent admin role removed email to {}", adminEmail),
      exception -> log.warn("Failed to send admin role removed email to {}", adminEmail, exception)
    );
  }

  private String renderAdminRoleRemovedHtml(String adminName, String branchLabel) {
    return renderTemplate(adminRoleRemovedTemplate, Map.of(
      TOKEN_ADMIN_NAME, escape(adminName),
      TOKEN_BRANCH_LABEL, escape(branchLabel)
    ));
  }

  public void sendConfirmationEmail(Booking booking, Branch branch) {
    String branchLabel = resolveBranchLabel(booking, branch);
    String subject = "Your Capitec branch appointment is confirmed - " + booking.bookingReference();
    AppointmentDisplay appointment = appointmentDisplay(booking);
    String html = renderConfirmationHtml(booking, branchLabel, appointment);

    sendMail(
      Mail.withHtml(booking.customerEmail(), subject, html),
      () -> log.info("Sent booking confirmation email to {} for reference {}", booking.customerEmail(), booking.bookingReference()),
      exception -> log.warn("Failed to send confirmation email for booking {}", booking.bookingReference(), exception)
    );
  }

  private void sendMail(Mail mail, Runnable onSuccess, Consumer<Throwable> onFailure) {
    if (reactiveMailer == null) {
      onFailure.accept(new IllegalStateException("Reactive mailer is not configured."));
      return;
    }
    try {
      reactiveMailer.send(deliveryGuardrails.apply(mail))
        .subscribe()
        .with(ignored -> onSuccess.run(), onFailure);
    } catch (RuntimeException exception) {
      onFailure.accept(exception);
    }
  }

  private String resolveBranchLabel(Booking booking, Branch branch) {
    if (branch == null) {
      return booking.branchId().toString();
    }
    return branch.name() + ", " + branch.city();
  }

  private String renderConfirmationHtml(Booking booking, String branchLabel, AppointmentDisplay appointment) {
    return renderTemplate(confirmationTemplate, Map.of(
      TOKEN_CUSTOMER_NAME, escape(booking.customerName()),
      TOKEN_BOOKING_REFERENCE, escape(booking.bookingReference()),
      TOKEN_BRANCH_LABEL, escape(branchLabel),
      TOKEN_APPOINTMENT_DATE, appointment.date(),
      TOKEN_APPOINTMENT_TIME, appointment.time()
    ));
  }

  private AppointmentDisplay appointmentDisplay(Booking booking) {
    return new AppointmentDisplay(
      booking.startDateTime().format(DATE_FORMAT),
      booking.startDateTime().format(TIME_FORMAT)
    );
  }

  private String renderTemplate(String template, Map<String, String> values) {
    String rendered = template.replace(TOKEN_EMAIL_ASSETS_BASE_URL, emailAssetsBaseUrlWithoutTrailingSlash());
    for (Map.Entry<String, String> value : values.entrySet()) {
      rendered = rendered.replace(value.getKey(), value.getValue());
    }
    return rendered;
  }

  private String emailAssetsBaseUrlWithoutTrailingSlash() {
    return emailAssetsBaseUrl == null ? "" : emailAssetsBaseUrl.replaceAll("/+$", "");
  }

  private static String readClasspathString(String resource) {
    try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ioException) {
      log.warn("Could not load classpath resource {}", resource, ioException);
      return null;
    }
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }

  private record AppointmentDisplay(String date, String time) {
  }
}
