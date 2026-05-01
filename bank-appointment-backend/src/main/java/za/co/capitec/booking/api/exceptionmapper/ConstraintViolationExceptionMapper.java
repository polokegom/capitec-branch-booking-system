package za.co.capitec.booking.api.exceptionmapper;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import za.co.capitec.booking.api.dto.ApiError;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
  @Override
  public Response toResponse(ConstraintViolationException exception) {
    String message = exception.getConstraintViolations()
      .stream()
      .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
      .collect(Collectors.joining("; "));

    return Response.status(Response.Status.BAD_REQUEST)
      .entity(new ApiError("validation_error", message, OffsetDateTime.now()))
      .build();
  }
}
