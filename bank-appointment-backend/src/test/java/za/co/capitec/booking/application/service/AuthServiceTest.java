package za.co.capitec.booking.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import za.co.capitec.booking.api.dto.AuthSessionRequest;
import za.co.capitec.booking.infrastructure.restclient.FusionAuthRestClient;

class AuthServiceTest {
  private static final String APPLICATION_ID = "85a03867-dccf-4882-adde-1a79aeec50df";

  @Test
  void shouldMapInvalidFusionAuthCredentialsToUnauthorized() {
    AuthService authService = authServiceWith(fusionAuthRestClient(
      new NotFoundException(Response.status(Response.Status.NOT_FOUND).entity("{}").build())
    ));

    Response response = authService.createSession(new AuthSessionRequest("customer@capitec.co.za", "wrong-password"));

    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(Map.of("message", "Invalid email or password."));
  }

  @Test
  void shouldMapFusionAuthConnectivityFailuresToBadGateway() {
    AuthService authService = authServiceWith(fusionAuthRestClient(new ProcessingException("Connection refused")));

    assertThatThrownBy(() -> authService.createSession(new AuthSessionRequest("customer@capitec.co.za", "password123")))
      .isInstanceOf(jakarta.ws.rs.WebApplicationException.class)
      .satisfies(throwable -> {
        Response response = ((jakarta.ws.rs.WebApplicationException) throwable).getResponse();
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_GATEWAY.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("message", "Authentication service unavailable."));
      });
  }

  private static AuthService authServiceWith(FusionAuthRestClient fusionAuthRestClient) {
    AuthPayloadFactory payloadFactory = new AuthPayloadFactory();
    payloadFactory.fusionAuthApplicationId = APPLICATION_ID;

    AuthResponseMapper responseMapper = new AuthResponseMapper();
    responseMapper.fusionAuthApplicationId = APPLICATION_ID;

    AuthService authService = new AuthService(fusionAuthRestClient, payloadFactory, responseMapper, null);
    authService.fusionAuthApiKey = "test-api-key";
    return authService;
  }

  private static FusionAuthRestClient fusionAuthRestClient(RuntimeException loginFailure) {
    return (FusionAuthRestClient) Proxy.newProxyInstance(
      FusionAuthRestClient.class.getClassLoader(),
      new Class<?>[] {FusionAuthRestClient.class},
      (proxy, method, arguments) -> {
        if ("login".equals(method.getName())) {
          throw loginFailure;
        }
        throw new UnsupportedOperationException();
      }
    );
  }
}
