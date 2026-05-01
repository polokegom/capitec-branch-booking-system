package za.co.capitec.booking.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Proxy;
import java.net.URI;
import org.junit.jupiter.api.Test;
import za.co.capitec.booking.api.dto.CreateEmailVerificationRequest;
import za.co.capitec.booking.application.configuration.EmailDeliveryGuardrails;
import za.co.capitec.booking.infrastructure.restclient.FusionAuthRestClient;

class EmailVerificationWorkflowTest {
  private static final String APPLICATION_ID = "85a03867-dccf-4882-adde-1a79aeec50df";

  @Test
  void shouldAcceptVerificationRequestsForUnknownAccountsWithoutSendingEmail() {
    CapturingVerificationEmailService emailService = new CapturingVerificationEmailService();
    EmailVerificationWorkflow workflow = workflowWith(
      withVerificationRequestFailure(notFoundResponse()),
      emailService
    );

    Response response = workflow.createEmailVerification(new CreateEmailVerificationRequest("unknown@example.co.za"));

    assertThat(response.getStatus()).isEqualTo(Response.Status.ACCEPTED.getStatusCode());
    assertThat(emailService.sentEmailCount).isZero();
  }

  @Test
  void shouldSendVerificationEmailWhenFusionAuthCreatesVerificationId() {
    CapturingVerificationEmailService emailService = new CapturingVerificationEmailService();
    EmailVerificationWorkflow workflow = workflowWith(
      withVerificationRequest(Response.ok("{\"verificationId\":\"verification-123\"}").build()),
      emailService
    );

    Response response = workflow.createEmailVerification(new CreateEmailVerificationRequest("customer@example.co.za"));

    assertThat(response.getStatus()).isEqualTo(Response.Status.ACCEPTED.getStatusCode());
    assertThat(emailService.sentEmailCount).isEqualTo(1);
    assertThat(emailService.lastVerificationId).isEqualTo("verification-123");
  }

  @Test
  void shouldRedirectExpiredVerificationLinksWhenFusionAuthReturnsClientError() {
    EmailVerificationWorkflow workflow = workflowWith(
      withEmailVerificationFailure(goneResponse()),
      new CapturingVerificationEmailService()
    );

    Response response = workflow.findEmailVerification("expired-verification-id", "tenant-123");

    assertThat(response.getStatus()).isEqualTo(Response.Status.SEE_OTHER.getStatusCode());
    assertThat(response.getLocation()).isEqualTo(URI.create("https://app.example.co.za/login?emailVerification=expired"));
  }

  @Test
  void shouldTreatVerificationConnectivityFailuresAsAuthenticationOutages() {
    EmailVerificationWorkflow workflow = workflowWith(
      withVerificationRequestFailure(new ProcessingException("Connection refused")),
      new CapturingVerificationEmailService()
    );

    assertThatThrownBy(() -> workflow.createEmailVerification(new CreateEmailVerificationRequest("customer@example.co.za")))
      .isInstanceOf(WebApplicationException.class)
      .satisfies(throwable -> {
        Response response = ((WebApplicationException) throwable).getResponse();
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_GATEWAY.getStatusCode());
      });
  }

  private static WebApplicationException notFoundResponse() {
    return new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity("{}").build());
  }

  private static WebApplicationException goneResponse() {
    return new WebApplicationException(Response.status(Response.Status.GONE).build());
  }

  private static EmailVerificationWorkflow workflowWith(
    FusionAuthRestClient fusionAuthRestClient,
    CapturingVerificationEmailService emailService
  ) {
    AuthResponseMapper responseMapper = new AuthResponseMapper();
    responseMapper.fusionAuthApplicationId = APPLICATION_ID;

    EmailVerificationWorkflow workflow = new EmailVerificationWorkflow(fusionAuthRestClient, emailService, responseMapper);
    workflow.fusionAuthApiKey = "test-api-key";
    workflow.fusionAuthApplicationId = APPLICATION_ID;
    workflow.publicBaseUrl = "https://app.example.co.za";
    return workflow;
  }

  private static FusionAuthRestClient withVerificationRequest(Response response) {
    return fusionAuthRestClient(response, null, null);
  }

  private static FusionAuthRestClient withVerificationRequestFailure(RuntimeException failure) {
    return fusionAuthRestClient(null, failure, null);
  }

  private static FusionAuthRestClient withEmailVerificationFailure(RuntimeException failure) {
    return fusionAuthRestClient(null, null, failure);
  }

  private static FusionAuthRestClient fusionAuthRestClient(
    Response verificationRequestResponse,
    RuntimeException verificationRequestFailure,
    RuntimeException emailVerificationFailure
  ) {
    return (FusionAuthRestClient) Proxy.newProxyInstance(
      FusionAuthRestClient.class.getClassLoader(),
      new Class<?>[] {FusionAuthRestClient.class},
      (proxy, method, arguments) -> switch (method.getName()) {
        case "requestVerificationId" -> {
          if (verificationRequestFailure != null) {
            throw verificationRequestFailure;
          }
          yield verificationRequestResponse;
        }
        case "verifyEmail" -> {
          if (emailVerificationFailure != null) {
            throw emailVerificationFailure;
          }
          yield Response.ok().build();
        }
        default -> throw new UnsupportedOperationException();
      }
    );
  }

  private static final class CapturingVerificationEmailService extends VerificationEmailService {
    private int sentEmailCount;
    private String lastVerificationId;

    private CapturingVerificationEmailService() {
      super(null, new EmailDeliveryGuardrails());
    }

    @Override
    public void sendVerificationEmail(String email, String firstName, String verificationId) {
      sentEmailCount++;
      lastVerificationId = verificationId;
    }
  }
}
