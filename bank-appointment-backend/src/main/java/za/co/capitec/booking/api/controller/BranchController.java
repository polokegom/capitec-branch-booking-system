package za.co.capitec.booking.api.controller;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import za.co.capitec.booking.api.dto.BranchMarketResponse;
import lombok.RequiredArgsConstructor;
import za.co.capitec.booking.api.dto.BranchAdminRequest;
import za.co.capitec.booking.api.dto.BranchResponse;
import za.co.capitec.booking.api.dto.BranchStatusRequest;
import za.co.capitec.booking.api.dto.PaginationResponse;
import za.co.capitec.booking.api.mapper.ApiMapper;
import za.co.capitec.booking.api.utility.PaginationRange;
import za.co.capitec.booking.application.configuration.CountriesWithBankBranches;
import za.co.capitec.booking.application.security.SecurityRoles;
import za.co.capitec.booking.application.service.BranchAdminService;
import za.co.capitec.booking.application.service.BranchQueryService;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class BranchController {
  private final BranchQueryService branchQueryService;
  private final BranchAdminService branchAdminService;
  private final CountriesWithBankBranches countriesWithBankBranches;
  private final ApiMapper apiMapper;

  @GET
  @Path("/branches")
  @Authenticated
  public Uni<PaginationResponse<BranchResponse>> searchBranches(
    @QueryParam("query") @DefaultValue("") String query,
    @QueryParam("startIndex") Integer startIndex,
    @QueryParam("endIndex") Integer endIndex
  ) {
    PaginationRange range = PaginationRange.from(startIndex, endIndex);
    return branchQueryService.searchBranchesUsingPagination(query, range.startIndex(), range.endIndex())
      .map(pagination -> PaginationResponse.mapped(pagination, apiMapper::toBranchResponse));
  }

  @GET
  @Path("/branches/countries")
  @Authenticated
  public Uni<Map<String, BranchMarketResponse>> listCountriesWithBankBranches() {
    return Uni.createFrom().item(
      countriesWithBankBranches.markets().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> new BranchMarketResponse(e.getValue().timezone(), e.getValue().provinces())))
    );
  }

  @GET
  @Path("/admin/branches")
  @RolesAllowed(SecurityRoles.OWNER)
  public Uni<PaginationResponse<BranchResponse>> listAdminBranches(
    @QueryParam("startIndex") Integer startIndex,
    @QueryParam("endIndex") Integer endIndex
  ) {
    PaginationRange range = PaginationRange.from(startIndex, endIndex);
    return branchAdminService.listAllUsingPagination(range.startIndex(), range.endIndex())
      .map(pagination -> PaginationResponse.mapped(pagination, apiMapper::toBranchResponse));
  }

  @POST
  @Path("/admin/branches")
  @RolesAllowed(SecurityRoles.OWNER)
  public Uni<Response> createBranch(@NotNull @Valid BranchAdminRequest request) {
    return branchAdminService.create(apiMapper.toSaveBranchCommand(request))
      .map(apiMapper::toBranchResponse)
      .map(body -> Response.status(Response.Status.CREATED).entity(body).build());
  }

  @PUT
  @Path("/admin/branches/{branchId}")
  @RolesAllowed(SecurityRoles.OWNER)
  public Uni<BranchResponse> updateBranch(
    @PathParam("branchId") @NotNull UUID branchId,
    @NotNull @Valid BranchAdminRequest request
  ) {
    return branchAdminService.update(branchId, apiMapper.toSaveBranchCommand(request))
      .map(apiMapper::toBranchResponse);
  }

  @PATCH
  @Path("/admin/branches/{branchId}")
  @RolesAllowed(SecurityRoles.OWNER)
  public Uni<Response> updateBranchStatus(
    @PathParam("branchId") @NotNull UUID branchId,
    @NotNull @Valid BranchStatusRequest request
  ) {
    if (Boolean.TRUE.equals(request.active())) {
      return branchAdminService.reactivate(branchId)
        .replaceWith(Response.noContent().build());
    }
    return branchAdminService.deactivate(branchId)
      .replaceWith(Response.noContent().build());
  }
}
