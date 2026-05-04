package za.co.capitec.booking.api.controller;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.jwt.JsonWebToken;
import za.co.capitec.booking.api.dto.AdminBookingResponse;
import za.co.capitec.booking.api.dto.BookingResponse;
import za.co.capitec.booking.api.dto.BranchResponse;
import za.co.capitec.booking.api.dto.CreateBookingRequest;
import za.co.capitec.booking.api.dto.PaginationResponse;
import za.co.capitec.booking.api.mapper.ApiMapper;
import za.co.capitec.booking.api.utility.AuthenticatedCallerResolver;
import za.co.capitec.booking.api.utility.PaginationRange;
import za.co.capitec.booking.api.utility.RequestParameters;
import za.co.capitec.booking.api.utility.RequestParameters.DateTimeRange;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.application.security.SecurityRoles;
import za.co.capitec.booking.application.service.BranchAdminService;
import za.co.capitec.booking.application.service.BookingCommandService;
import za.co.capitec.booking.application.service.BookingQueryService;
import za.co.capitec.booking.application.utility.TextSanitizer;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;
import za.co.capitec.booking.domain.model.Branch;

@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class BookingController {
  private final BookingCommandService bookingCommandService;
  private final BookingQueryService bookingQueryService;
  private final BranchAdminService branchAdminService;
  private final BookingRepository bookingRepository;
  private final AuthenticatedCallerResolver callerResolver;
  private final ApiMapper apiMapper;

  @Inject
  JsonWebToken jwt;

  @Inject
  SecurityIdentity identity;

  @POST
  @Path("/bookings")
  @RolesAllowed(SecurityRoles.CUSTOMER)
  public Uni<Response> createBooking(
    @HeaderParam("X-Idempotency-Key") @NotBlank String idempotencyKey,
    @NotNull @Valid CreateBookingRequest request
  ) {
    return bookingCommandService.createBooking(apiMapper.toCreateBookingCommand(request, idempotencyKey))
      .map(apiMapper::toBookingResponse)
      .map(bookingResponse -> Response.status(Response.Status.CREATED).entity(bookingResponse).build());
  }

  @GET
  @Path("/bookings/{bookingReference}")
  @Authenticated
  public Uni<BookingResponse> findBooking(@PathParam("bookingReference") @NotBlank String bookingReference) {
    return bookingQueryService.findByReference(bookingReference)
      .map(apiMapper::toBookingResponse);
  }

  @GET
  @Path("/customer/bookings")
  @RolesAllowed(SecurityRoles.CUSTOMER)
  public Uni<PaginationResponse<AdminBookingResponse>> listCustomerBookings(
    @QueryParam("startDate") String startDate,
    @QueryParam("endDate") String endDate,
    @QueryParam("branchSearch") String branchSearch,
    @QueryParam("startIndex") Integer startIndex,
    @QueryParam("endIndex") Integer endIndex
  ) {
    DateTimeRange dateRange = RequestParameters.optionalDateRange(startDate, endDate);
    return callerEmail()
      .chain(email -> {
        if (TextSanitizer.isBlank(email)) {
          throw new InvalidBookingRequestException("Authenticated email is required.");
        }
        String search = TextSanitizer.trimToNull(branchSearch);
        PaginationRange range = PaginationRange.from(startIndex, endIndex);

        return bookingQueryService.findCustomerBookingDetailsUsingPagination(
            email,
            dateRange.startDateTime(),
            dateRange.endDateTime(),
            search,
            range.startIndex(),
            range.endIndex()
          )
          .map(pagination -> PaginationResponse.mapped(pagination, apiMapper::toAdminBookingResponse));
      });
  }

  @DELETE
  @Path("/bookings/{bookingReference}")
  @RolesAllowed(SecurityRoles.CUSTOMER)
  public Uni<BookingResponse> cancelBooking(@PathParam("bookingReference") @NotBlank String bookingReference) {
    return callerEmail()
      .chain(email -> {
        if (TextSanitizer.isBlank(email)) {
          throw new InvalidBookingRequestException("Authenticated email is required.");
        }
        return bookingCommandService.cancelBooking(bookingReference, email);
      })
      .map(apiMapper::toBookingResponse);
  }

  @GET
  @Path("/admin/bookings/branches")
  @RolesAllowed({SecurityRoles.OWNER, SecurityRoles.ADMIN})
  public Uni<PaginationResponse<BranchResponse>> listVisibleAdminBranches(
    @QueryParam("startIndex") Integer startIndex,
    @QueryParam("endIndex") Integer endIndex
  ) {
    return scopedBranches()
      .map(visibleBranches -> {
        PaginationRange range = PaginationRange.from(startIndex, endIndex);
        int visibleStartIndex = Math.min(range.startIndex(), visibleBranches.size());
        int visibleEndIndex = Math.min(range.endIndex(), visibleBranches.size());
        List<BranchResponse> branchResponses = visibleBranches.subList(visibleStartIndex, visibleEndIndex).stream()
          .map(apiMapper::toBranchResponse)
          .toList();
        return new PaginationResponse<>(
          branchResponses,
          visibleBranches.size(),
          range.startIndex(),
          range.endIndex(),
          visibleEndIndex < visibleBranches.size()
        );
      });
  }

  @GET
  @Path("/admin/bookings")
  @RolesAllowed({SecurityRoles.OWNER, SecurityRoles.ADMIN})
  public Uni<PaginationResponse<AdminBookingResponse>> listAdminBookings(
    @QueryParam("startDate") String startDate,
    @QueryParam("endDate") String endDate,
    @QueryParam("branchSearch") String branchSearch,
    @QueryParam("startIndex") Integer startIndex,
    @QueryParam("endIndex") Integer endIndex
  ) {
    DateTimeRange dateRange = RequestParameters.optionalDateRange(startDate, endDate);
    PaginationRange range = PaginationRange.from(startIndex, endIndex);

    return scopedBranches()
      .chain(visibleBranches -> {
        List<Branch> matchingBranches = filterBranches(visibleBranches, branchSearch);
        if (matchingBranches.isEmpty()) {
          return Uni.createFrom().item(new PaginationResponse<>(List.of(), 0L, range.startIndex(), range.endIndex(), false));
        }
        return listAdminBookingsForBranches(branchesById(matchingBranches), dateRange, range);
      });
  }

  private Uni<String> callerEmail() {
    return callerResolver.resolveEmail(jwt);
  }

  private Uni<List<Branch>> scopedBranches() {
    if (identity != null && identity.hasRole(SecurityRoles.OWNER)) {
      return branchAdminService.listAll()
        .map(branches -> branches.stream().filter(Branch::active).toList());
    }
    return callerEmail()
      .chain(email -> email == null
        ? Uni.createFrom().item(List.of())
        : branchAdminService.listAssignedTo(email).map(branches -> branches.stream().filter(Branch::active).toList()));
  }

  private List<Branch> filterBranches(List<Branch> branches, String search) {
    String branchSearchText = TextSanitizer.trimToLower(search);
    if (branchSearchText == null) {
      return branches;
    }
    return branches.stream()
      .filter(branch -> TextSanitizer.containsIgnoreCase(branch.name(), branchSearchText)
        || TextSanitizer.containsIgnoreCase(branch.city(), branchSearchText)
        || TextSanitizer.containsIgnoreCase(branch.code(), branchSearchText))
      .toList();
  }

  private Map<UUID, Branch> branchesById(List<Branch> branches) {
    Map<UUID, Branch> branchesById = new HashMap<>();
    for (Branch branch : branches) {
      branchesById.put(branch.id(), branch);
    }
    return branchesById;
  }

  private Uni<PaginationResponse<AdminBookingResponse>> listAdminBookingsForBranches(
    Map<UUID, Branch> branchesById,
    DateTimeRange dateRange,
    PaginationRange range
  ) {
    return bookingRepository.findForAdminUsingPagination(
        branchesById.keySet(),
        dateRange.startDateTime(),
        dateRange.endDateTime(),
        range.startIndex(),
        range.endIndex()
      )
      .map(pagination -> PaginationResponse.mapped(
        pagination,
        booking -> apiMapper.toAdminBookingResponse(booking, branchesById.get(booking.branchId()))
      ));
  }

}

