package za.co.capitec.booking.api.dto;

import jakarta.validation.constraints.NotNull;

public record BranchStatusRequest(@NotNull Boolean active) {}
