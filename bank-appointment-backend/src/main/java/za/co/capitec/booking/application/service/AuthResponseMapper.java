package za.co.capitec.booking.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
class AuthResponseMapper {
  @ConfigProperty(name = "app.fusionauth.application-id")
  String fusionAuthApplicationId;

  Map<String, Object> toAuthResponse(String body) {
    JsonObject login = parseJson(body);
    Map<String, Object> response = new HashMap<>();
    addIfPresent(response, "token", login, "token");
    addIfPresent(response, "refreshToken", login, "refreshToken");
    if (login.containsKey("user")) {
      response.put("profile", profile(login.getJsonObject("user")));
    }
    return response;
  }

  boolean hasUser(String body) {
    return parseJson(body).containsKey("user");
  }

  boolean isEmailVerified(String body) {
    JsonObject login = parseJson(body);
    return !login.containsKey("user")
      || !login.getJsonObject("user").containsKey("verified")
      || login.getJsonObject("user").getBoolean("verified");
  }

  String verificationId(String body) {
    return body == null || body.isBlank() ? null : parseJson(body).getString("verificationId", null);
  }

  String extractError(String body) {
    try {
      JsonObject json = parseJson(body);
      if (json.containsKey("fieldErrors")) {
        return firstFieldError(json.getJsonObject("fieldErrors"));
      }
      if (json.containsKey("generalErrors")) {
        JsonArray errors = json.getJsonArray("generalErrors");
        return errors.isEmpty() ? null : errors.getJsonObject(0).getString("message", null);
      }
    } catch (Exception ignoredException) {
      return null;
    }
    return null;
  }

  private Map<String, Object> profile(JsonObject user) {
    Map<String, Object> profile = new HashMap<>();
    addIfPresent(profile, "email", user, "email");
    addIfPresent(profile, "firstName", user, "firstName");
    addIfPresent(profile, "lastName", user, "lastName");
    profile.put("roles", roles(user));
    return profile;
  }

  private List<String> roles(JsonObject user) {
    List<String> roles = new ArrayList<>();
    if (!user.containsKey("registrations")) {
      return roles;
    }
    for (JsonObject registration : user.getJsonArray("registrations").getValuesAs(JsonObject.class)) {
      if (fusionAuthApplicationId.equals(registration.getString("applicationId", null)) && registration.containsKey("roles")) {
        JsonArray registrationRoles = registration.getJsonArray("roles");
        for (int roleIndex = 0; roleIndex < registrationRoles.size(); roleIndex++) {
          roles.add(registrationRoles.getString(roleIndex));
        }
      }
    }
    return roles;
  }

  private String firstFieldError(JsonObject fieldErrors) {
    for (String fieldName : fieldErrors.keySet()) {
      JsonArray messages = fieldErrors.getJsonArray(fieldName);
      if (!messages.isEmpty()) {
        return messages.getJsonObject(0).getString("message", null);
      }
    }
    return null;
  }

  private void addIfPresent(Map<String, Object> target, String targetKey, JsonObject source, String sourceKey) {
    if (source.containsKey(sourceKey)) {
      target.put(targetKey, source.getString(sourceKey));
    }
  }

  private JsonObject parseJson(String body) {
    try (JsonReader reader = Json.createReader(new StringReader(body))) {
      return reader.readObject();
    }
  }
}
