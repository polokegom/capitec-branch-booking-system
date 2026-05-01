package za.co.capitec.booking.api.exceptionmapper;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;
import za.co.capitec.booking.api.dto.ApiError;

@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {
  @Override
  public Response toResponse(Throwable exception) {
    if (exception instanceof AuthenticationFailedException || exception instanceof UnauthorizedException) {
      return Response.status(Response.Status.UNAUTHORIZED)
        .entity(new ApiError("unauthorized", "Authentication is required to access this resource.", OffsetDateTime.now()))
        .build();
    }
    if (exception instanceof ForbiddenException) {
      return Response.status(Response.Status.FORBIDDEN)
        .entity(new ApiError("forbidden", "You do not have permission to access this resource.", OffsetDateTime.now()))
        .build();
    }
    if (exception instanceof WebApplicationException webApplicationException) {
      Response existingResponse = webApplicationException.getResponse();
      Response.Status status = Response.Status.fromStatusCode(existingResponse.getStatus());
      if (status == null) {
        status = Response.Status.INTERNAL_SERVER_ERROR;
      }
      String errorCode = status.name().toLowerCase().replace('_', '-');
      String errorMessage = webApplicationException.getMessage() == null
        ? status.getReasonPhrase()
        : webApplicationException.getMessage();
      return Response.status(status)
        .entity(new ApiError(errorCode, errorMessage, OffsetDateTime.now()))
        .build();
    }
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
      .entity(new ApiError("internal_error", "An unexpected error occurred.", OffsetDateTime.now()))
      .build();
  }
}
