package za.co.capitec.booking.application.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import za.co.capitec.booking.infrastructure.restclient.FusionAuthRestClient;

@ApplicationScoped
@Slf4j
public class FusionAuthAdminClient {

  @ConfigProperty(name = "app.fusionauth.api-key")
  String apiKey;

  @ConfigProperty(name = "app.fusionauth.application-id")
  String applicationId;

  private final FusionAuthRestClient fusionAuthRestClient;

  public FusionAuthAdminClient(@RestClient FusionAuthRestClient fusionAuthRestClient) {
    this.fusionAuthRestClient = fusionAuthRestClient;
  }

  public record UserRoles(String userId, List<String> roles) {}

  public Optional<UserRoles> findUserRolesByEmail(String email) {
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    Response response = send(() -> fusionAuthRestClient.findUserByEmail(apiKey, email.trim()), "GET /api/user");
    if (response == null || response.getStatus() != 200) {
      return Optional.empty();
    }
    try (response) {
      JsonObject body = parse(readBody(response));
      if (body == null || !body.containsKey("user")) {
        return Optional.empty();
      }
      JsonObject user = body.getJsonObject("user");
      if (!user.containsKey("id")) {
        return Optional.empty();
      }
      String userId = user.getString("id");
      List<String> roles = extractApplicationRoles(user);
      return Optional.of(new UserRoles(userId, roles));
    }
  }

  public Optional<String> findEmailByUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      return Optional.empty();
    }
    Response response = send(() -> fusionAuthRestClient.findUserById(apiKey, userId.trim()), "GET /api/user/{userId}");
    if (response == null || response.getStatus() != 200) {
      return Optional.empty();
    }
    try (response) {
      JsonObject body = parse(readBody(response));
      if (body == null || !body.containsKey("user")) {
        return Optional.empty();
      }
      JsonObject user = body.getJsonObject("user");
      if (!user.containsKey("email")) {
        return Optional.empty();
      }
      String email = user.getString("email");
      return email == null || email.isBlank() ? Optional.empty() : Optional.of(email);
    }
  }

  private List<String> extractApplicationRoles(JsonObject user) {
    List<String> applicationRoles = new ArrayList<>();
    if (!user.containsKey("registrations")) {
      return applicationRoles;
    }
    JsonArray registrations = user.getJsonArray("registrations");
    for (int registrationIndex = 0; registrationIndex < registrations.size(); registrationIndex++) {
      JsonObject registration = registrations.getJsonObject(registrationIndex);
      if (!registration.containsKey("applicationId") || !applicationId.equals(registration.getString("applicationId"))) {
        continue;
      }
      if (registration.containsKey("roles")) {
        JsonArray roles = registration.getJsonArray("roles");
        for (int roleIndex = 0; roleIndex < roles.size(); roleIndex++) {
          applicationRoles.add(roles.getString(roleIndex));
        }
      }
    }
    return applicationRoles;
  }

  private Response send(FusionAuthRequest request, String operation) {
    try {
      return request.send();
    } catch (ProcessingException exception) {
      log.warn("FusionAuth {} failed", operation, exception);
      return null;
    }
  }

  private String readBody(Response response) {
    return response.hasEntity() ? response.readEntity(String.class) : "";
  }

  private JsonObject parse(String body) {
    try (JsonReader reader = Json.createReader(new StringReader(body))) {
      return reader.readObject();
    } catch (Exception ignoredException) {
      return null;
    }
  }

  @FunctionalInterface
  private interface FusionAuthRequest {
    Response send();
  }
}
