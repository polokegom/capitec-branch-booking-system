package za.co.capitec.booking.application.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import za.co.capitec.booking.application.configuration.EmailDeliveryGuardrails;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class VerificationEmailService {
  private static final String TEMPLATE_RESOURCE = "email/emailVerificationTemplate.html";
  private static final String TEMPLATE_DIRECTIVE = "[#setting url_escaping_charset=\"UTF-8\"]";
  private static final String EMAIL_ASSETS_BASE_URL_TOKEN = "{{emailAssetsBaseUrl}}";
  private static final String VERIFY_BASE_TOKEN = "__EMAIL_VERIFY_BASE_URL__";
  private static final String FIRST_NAME_TOKEN = "${user.firstName!'there'}";
  private static final String VERIFICATION_ID_TOKEN = "${verificationId}";
  private static final String TENANT_ID_TOKEN = "${tenant.id}";

  private final Mailer mailer;
  private final EmailDeliveryGuardrails deliveryGuardrails;

  @ConfigProperty(name = "app.public-base-url", defaultValue = "http://localhost:4200")
  String publicBaseUrl;

  @ConfigProperty(name = "app.email-assets.base-url")
  String emailAssetsBaseUrl;

  @ConfigProperty(name = "app.fusionauth.tenant-id", defaultValue = "30663132-6464-6665-3032-326466613934")
  String tenantId;

  private String template;

  @PostConstruct
  void loadTemplate() {
    this.template = readClasspathString(TEMPLATE_RESOURCE);
    if (template == null) {
      throw new IllegalStateException("Email verification template not found on classpath: " + TEMPLATE_RESOURCE);
    }
  }

  public void sendVerificationEmail(String email, String firstName, String verificationId) {
    String displayName = firstName == null || firstName.isBlank() ? "there" : firstName.trim();
    String verificationUrl = verificationUrl(verificationId);
    String html = renderHtml(displayName, verificationId, verificationUrl);
    String text = """
      Hello %s,

      Please verify your email address before signing in to the booking system.

      %s

      If you did not create this account, you can ignore this email.
      """.formatted(displayName, verificationUrl);

    Mail mail = deliveryGuardrails.apply(Mail.withHtml(email, "Verify your booking email address", html).setText(text));
    mailer.send(mail);
    log.info("Sent verification email to {}", email);
  }

  private String renderHtml(String firstName, String verificationId, String verificationUrl) {
    return template
      .replace(TEMPLATE_DIRECTIVE, "")
      .replace(EMAIL_ASSETS_BASE_URL_TOKEN, escapeHtml(emailAssetsBaseUrlWithoutTrailingSlash()))
      .replace(VERIFY_BASE_TOKEN + "/" + VERIFICATION_ID_TOKEN + "?tenantId=" + TENANT_ID_TOKEN, escapeHtml(verificationUrl))
      .replace(VERIFY_BASE_TOKEN, escapeHtml(verifyBaseUrl()))
      .replace(VERIFICATION_ID_TOKEN, urlEncode(verificationId))
      .replace(TENANT_ID_TOKEN, urlEncode(tenantId))
      .replace(FIRST_NAME_TOKEN, escapeHtml(firstName));
  }

  private String verificationUrl(String verificationId) {
    return verifyBaseUrl() + "/" + urlEncode(verificationId) + "?tenantId=" + urlEncode(tenantId);
  }

  private String verifyBaseUrl() {
    return publicBaseUrlWithoutTrailingSlash() + "/api/v1/auth/email-verifications";
  }

  private String publicBaseUrlWithoutTrailingSlash() {
    return publicBaseUrl.replaceAll("/+$", "");
  }

  private String emailAssetsBaseUrlWithoutTrailingSlash() {
    return emailAssetsBaseUrl == null ? "" : emailAssetsBaseUrl.replaceAll("/+$", "");
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String escapeHtml(String value) {
    return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }

  private static String readClasspathString(String resource) {
    try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ioException) {
      log.warn("Could not load classpath resource {}", resource, ioException);
      return null;
    }
  }
}
