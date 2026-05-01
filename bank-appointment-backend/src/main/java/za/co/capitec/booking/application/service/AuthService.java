package za.co.capitec.booking.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import za.co.capitec.booking.api.dto.AuthSessionRequest;
import za.co.capitec.booking.api.dto.CreateAuthUserRequest;
import za.co.capitec.booking.api.dto.CreateEmailVerificationRequest;
import za.co.capitec.booking.infrastructure.restclient.FusionAuthRestClient;

@ApplicationScoped
public class AuthService {
  private static final String VERIFY_EMAIL_MESSAGE =
    "Please verify your email address before signing in. Check your inbox for the verification link.";

  @ConfigProperty(name = "app.fusionauth.api-key")
  String fusionAuthApiKey;

  private final FusionAuthRestClient fusionAuthRestClient;
  private final AuthPayloadFactory authPayloadFactory;
  private final AuthResponseMapper authResponseMapper;
  private final EmailVerificationWorkflow emailVerificationWorkflow;

  public AuthService(
    @RestClient FusionAuthRestClient fusionAuthRestClient,
    AuthPayloadFactory authPayloadFactory,
    AuthResponseMapper authResponseMapper,
    EmailVerificationWorkflow emailVerificationWorkflow
  ) {
    this.fusionAuthRestClient = fusionAuthRestClient;
    this.authPayloadFactory = authPayloadFactory;
    this.authResponseMapper = authResponseMapper;
    this.emailVerificationWorkflow = emailVerificationWorkflow;
  }

  public Response createSession(AuthSessionRequest request) {
    Response response = sendToFusionAuth(() -> fusionAuthRestClient.login(fusionAuthApiKey, authPayloadFactory.session(request)));
    String body = readBody(response);

    return switch (response.getStatus()) {
      case 200 -> authResponseMapper.isEmailVerified(body)
        ? Response.ok(authResponseMapper.toAuthResponse(body)).build()
        : errorResponse(403, VERIFY_EMAIL_MESSAGE);
      case 212 -> errorResponse(403, VERIFY_EMAIL_MESSAGE);
      case 404 -> errorResponse(401, "Invalid email or password.");
      case 410 -> errorResponse(403, "This account is locked. Please contact support.");
      default -> errorResponse(response.getStatus() >= 400 ? response.getStatus() : 401, "Sign in failed. Please try again.");
    };
  }

  public Response createUser(CreateAuthUserRequest request) {
    Response response = sendToFusionAuth(() -> fusionAuthRestClient.registerUser(fusionAuthApiKey, authPayloadFactory.registration(request)));
    String body = readBody(response);

    if (response.getStatus() == 200) {
      if (authResponseMapper.hasUser(body) && authResponseMapper.isEmailVerified(body)) {
        return createSession(new AuthSessionRequest(request.email(), request.password()));
      }
      return emailVerificationWorkflow.sendVerificationEmail(request.email(), request.firstName())
        ? verificationRequiredResponse()
        : errorResponse(502, "Registration was created, but we could not send the verification email. Please use resend verification in a few minutes.");
    }
    if (response.getStatus() == 400) {
      String message = authResponseMapper.extractError(body);
      return errorResponse(400, message == null ? "Please check the details you entered." : message);
    }
    return errorResponse(response.getStatus() >= 400 ? response.getStatus() : 500, "Registration failed. Please try again.");
  }

  public Response createEmailVerification(CreateEmailVerificationRequest request) {
    return emailVerificationWorkflow.createEmailVerification(request);
  }

  public Response findEmailVerification(String verificationId, String tenantId) {
    return emailVerificationWorkflow.findEmailVerification(verificationId, tenantId);
  }

  private Response verificationRequiredResponse() {
    return Response.accepted(Map.of(
      "verificationRequired", true,
      "message", "Registration created. Please verify your email address before signing in."
    )).build();
  }

  private Response sendToFusionAuth(FusionAuthRequest request) {
    try {
      return request.send();
    } catch (WebApplicationException exception) {
      Response response = exception.getResponse();
      if (response != null && response.getStatus() >= 400 && response.getStatus() < 500) {
        return response;
      }
      throw authUnavailable();
    } catch (Exception exception) {
      throw authUnavailable();
    }
  }

  private WebApplicationException authUnavailable() {
    return new WebApplicationException(
      Response.status(Response.Status.BAD_GATEWAY)
        .entity(Map.of("message", "Authentication service unavailable."))
        .build()
    );
  }

  private String readBody(Response response) {
    return response.hasEntity() ? response.readEntity(String.class) : "";
  }

  private Response errorResponse(int status, String message) {
    return Response.status(status).entity(Map.of("message", message)).build();
  }

  @FunctionalInterface
  private interface FusionAuthRequest {
    Response send();
  }
}
