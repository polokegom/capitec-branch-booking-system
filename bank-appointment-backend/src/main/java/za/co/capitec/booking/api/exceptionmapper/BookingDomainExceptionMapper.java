package za.co.capitec.booking.api.exceptionmapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;
import za.co.capitec.booking.api.dto.ApiError;
import za.co.capitec.booking.domain.exception.BookingDomainException;

@Provider
public class BookingDomainExceptionMapper implements ExceptionMapper<BookingDomainException> {
  @Override
  public Response toResponse(BookingDomainException exception) {
    return Response.status(exception.statusCode())
      .entity(new ApiError(exception.errorCode(), exception.getMessage(), OffsetDateTime.now()))
      .build();
  }
}
