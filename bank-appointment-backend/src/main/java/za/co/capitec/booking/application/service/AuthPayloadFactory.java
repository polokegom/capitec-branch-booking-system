package za.co.capitec.booking.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import za.co.capitec.booking.api.dto.AuthSessionRequest;
import za.co.capitec.booking.api.dto.CreateAuthUserRequest;

@ApplicationScoped
class AuthPayloadFactory {
  @ConfigProperty(name = "app.fusionauth.application-id")
  String fusionAuthApplicationId;

  String session(AuthSessionRequest request) {
    return Json.createObjectBuilder()
      .add("loginId", request.email())
      .add("password", request.password())
      .add("applicationId", fusionAuthApplicationId)
      .build()
      .toString();
  }

  String registration(CreateAuthUserRequest request) {
    return Json.createObjectBuilder()
      .add("user", Json.createObjectBuilder()
        .add("email", request.email())
        .add("firstName", request.firstName())
        .add("lastName", request.lastName())
        .add("password", request.password()))
      .add("registration", Json.createObjectBuilder()
        .add("applicationId", fusionAuthApplicationId)
        .add("roles", Json.createArrayBuilder().add("customer")))
      .build()
      .toString();
  }
}
