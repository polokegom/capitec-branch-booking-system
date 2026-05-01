package za.co.capitec.booking.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import za.co.capitec.booking.api.dto.CreateEmailVerificationRequest;
import za.co.capitec.booking.infrastructure.restclient.FusionAuthRestClient;

@ApplicationScoped
@Slf4j
class EmailVerificationWorkflow {
  @ConfigProperty(name = "app.fusionauth.api-key")
  String fusionAuthApiKey;

  @ConfigProperty(name = "app.fusionauth.application-id")
  String fusionAuthApplicationId;

  @ConfigProperty(name = "app.public-base-url", defaultValue = "http://localhost:4200")
  String publicBaseUrl;

  private final FusionAuthRestClient fusionAuthRestClient;
  private final VerificationEmailService verificationEmailService;
  private final AuthResponseMapper authResponseMapper;

  EmailVerificationWorkflow(
    @RestClient FusionAuthRestClient fusionAuthRestClient,
    VerificationEmailService verificationEmailService,
    AuthResponseMapper authResponseMapper
  ) {
    this.fusionAuthRestClient = fusionAuthRestClient;
    this.verificationEmailService = verificationEmailService;
    this.authResponseMapper = authResponseMapper;
  }

  Response createEmailVerification(CreateEmailVerificationRequest request) {
    VerificationRequestResult verificationRequest = requestVerificationId(request.email());
    if (verificationRequest.status() == 404) {
      return accepted("If this account needs verification, a new email has been sent.");
    }
    if (verificationRequest.status() == 400) {
      return errorResponse(400, verificationRequest.message() == null ? "Please enter a valid email address." : verificationRequest.message());
    }
    if (!verificationRequest.created()) {
      return errorResponse(502, "We could not create a verification link right now. Please try again in a few minutes.");
    }
    return sendVerificationEmail(request.email(), null, verificationRequest.verificationId())
      ? accepted("A new verification email has been sent.")
      : errorResponse(502, "We could not send a verification email right now. Please try again in a few minutes.");
  }

  Response findEmailVerification(String verificationId, String tenantId) {
    if (verificationId == null || verificationId.isBlank()) {
      return verificationRedirect("missing");
    }
    Response response = verifyEmail(verificationId, tenantId);
    if (response.getStatus() >= 200 && response.getStatus() < 300) {
      return verificationRedirect("verified");
    }
    if (response.getStatus() == 404 || response.getStatus() == 410) {
      return verificationRedirect("expired");
    }
    return verificationRedirect("unavailable");
  }

  boolean sendVerificationEmail(String email, String firstName) {
    VerificationRequestResult verificationRequest = requestVerificationId(email);
    if (!verificationRequest.created()) {
      log.warn("FusionAuth did not create a verification id for {}. Status {}", email, verificationRequest.status());
      return false;
    }
    return sendVerificationEmail(email, firstName, verificationRequest.verificationId());
  }

  private boolean sendVerificationEmail(String email, String firstName, String verificationId) {
    try {
      verificationEmailService.sendVerificationEmail(email, firstName, verificationId);
      return true;
    } catch (RuntimeException exception) {
      log.warn("Failed to send verification email to {}", email, exception);
      return false;
    }
  }

  private VerificationRequestResult requestVerificationId(String email) {
    try {
      Response response = fusionAuthRestClient.requestVerificationId(fusionAuthApiKey, fusionAuthApplicationId, email, false);
      return verificationRequestResult(response);
    } catch (WebApplicationException exception) {
      Response response = exception.getResponse();
      if (isClientError(response)) {
        return verificationRequestResult(response);
      }
      throw authUnavailable();
    } catch (Exception exception) {
      throw authUnavailable();
    }
  }

  private Response verifyEmail(String verificationId, String tenantId) {
    try {
      return fusionAuthRestClient.verifyEmail(fusionAuthApiKey, tenantId, verificationId);
    } catch (WebApplicationException exception) {
      Response response = exception.getResponse();
      if (isClientError(response)) {
        return response;
      }
      log.warn("Email verification failed for verification id {}", verificationId, exception);
      return Response.status(Response.Status.BAD_GATEWAY).build();
    } catch (Exception exception) {
      log.warn("Email verification failed for verification id {}", verificationId, exception);
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }

  private VerificationRequestResult verificationRequestResult(Response response) {
    String body = readBody(response);
    int status = response.getStatus();
    String verificationId = status >= 200 && status < 300 ? authResponseMapper.verificationId(body) : null;
    String message = status >= 400 ? authResponseMapper.extractError(body) : null;
    return new VerificationRequestResult(status, verificationId, message);
  }

  private boolean isClientError(Response response) {
    return response != null && response.getStatus() >= 400 && response.getStatus() < 500;
  }

  private String readBody(Response response) {
    return response.hasEntity() ? response.readEntity(String.class) : "";
  }

  private Response verificationRedirect(String status) {
    String encodedStatus = URLEncoder.encode(status, StandardCharsets.UTF_8);
    URI location = URI.create(publicBaseUrlWithoutTrailingSlash() + "/login?emailVerification=" + encodedStatus);
    return Response.seeOther(location).build();
  }

  private String publicBaseUrlWithoutTrailingSlash() {
    return publicBaseUrl == null || publicBaseUrl.isBlank() ? "http://localhost:4200" : publicBaseUrl.replaceAll("/+$", "");
  }

  private Response accepted(String message) {
    return Response.accepted(Map.of("message", message)).build();
  }

  private Response errorResponse(int status, String message) {
    return Response.status(status).entity(Map.of("message", message)).build();
  }

  private WebApplicationException authUnavailable() {
    return new WebApplicationException(
      Response.status(Response.Status.BAD_GATEWAY)
        .entity(Map.of("message", "Authentication service unavailable."))
        .build()
    );
  }

  private record VerificationRequestResult(int status, String verificationId, String message) {
    private boolean created() {
      return status >= 200 && status < 300 && verificationId != null && !verificationId.isBlank();
    }
  }
}
