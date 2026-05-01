package za.co.capitec.booking.api.controller;

import io.smallrye.mutiny.Uni;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import za.co.capitec.booking.api.dto.BookingSlotAvailabilityResponse;
import za.co.capitec.booking.api.mapper.ApiMapper;
import za.co.capitec.booking.application.service.AvailabilityQueryService;

@Path("/api/v1/availability")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AvailabilityController {
  private final AvailabilityQueryService availabilityQueryService;
  private final ApiMapper apiMapper;

  @GET
  public Uni<List<BookingSlotAvailabilityResponse>> getAvailability(
    @QueryParam("branchId") @NotNull UUID branchId,
    @QueryParam("date") @NotNull LocalDate appointmentDate
  ) {
    return availabilityQueryService.findAvailability(branchId, appointmentDate)
      .map(result -> result.bookingSlots().stream()
        .map(bookingSlot -> apiMapper.toBookingSlotAvailabilityResponse(bookingSlot, result.branchZone()))
        .toList());
  }
}
