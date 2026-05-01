package za.co.capitec.booking.infrastructure.restclient;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "fusionauth")
@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface FusionAuthRestClient {

  @POST
  @Path("/login")
  Response login(@HeaderParam("Authorization") String apiKey, String payload);

  @POST
  @Path("/user/registration")
  Response registerUser(@HeaderParam("Authorization") String apiKey, String payload);

  @GET
  @Path("/user")
  Response findUserByEmail(@HeaderParam("Authorization") String apiKey, @QueryParam("email") String email);

  @GET
  @Path("/user/{userId}")
  Response findUserById(@HeaderParam("Authorization") String apiKey, @PathParam("userId") String userId);

  @PUT
  @Path("/user/registration/{userId}")
  Response updateUserRegistration(
    @HeaderParam("Authorization") String apiKey,
    @PathParam("userId") String userId,
    String payload
  );

  @POST
  @Path("/user/verify-email/{verificationId}")
  Response verifyEmail(
    @HeaderParam("Authorization") String apiKey,
    @HeaderParam("X-FusionAuth-TenantId") String tenantId,
    @PathParam("verificationId") String verificationId
  );

  @PUT
  @Path("/user/verify-email")
  Response requestVerificationId(
    @HeaderParam("Authorization") String apiKey,
    @QueryParam("applicationId") String applicationId,
    @QueryParam("email") String email,
    @QueryParam("sendVerifyEmail") boolean sendVerifyEmail
  );
}
