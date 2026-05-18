package za.co.capitec.booking.api.controller;

import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import za.co.capitec.booking.api.dto.AuthSessionRequest;
import za.co.capitec.booking.api.dto.CreateAuthUserRequest;
import za.co.capitec.booking.api.dto.CreateEmailVerificationRequest;
import za.co.capitec.booking.api.utility.ReactiveResourceSupport;
import za.co.capitec.booking.application.service.AuthService;

@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;

  @POST
  @Path("/sessions")
  public Uni<Response> createSession(@NotNull @Valid AuthSessionRequest request) {
    return ReactiveResourceSupport.fromWorker(() -> authService.createSession(request));
  }

  @POST
  @Path("/users")
  public Uni<Response> createUser(@NotNull @Valid CreateAuthUserRequest request) {
    return ReactiveResourceSupport.fromWorker(() -> authService.createUser(request));
  }


  @POST
  @Path("/email-verifications")
  public Uni<Response> createEmailVerification(@NotNull @Valid CreateEmailVerificationRequest request) {
    return ReactiveResourceSupport.fromWorker(() -> authService.createEmailVerification(request));
  }

  @GET
  @Path("/email-verifications/{verificationId}")
  public Uni<Response> findEmailVerification(
    @PathParam("verificationId") @NotBlank String verificationId,
    @QueryParam("tenantId") @NotBlank String tenantId
  ) {
    return ReactiveResourceSupport.fromWorker(() -> authService.findEmailVerification(verificationId, tenantId));
  }
}
