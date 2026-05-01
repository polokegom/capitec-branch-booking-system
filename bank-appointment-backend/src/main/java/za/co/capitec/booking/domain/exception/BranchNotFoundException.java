package za.co.capitec.booking.domain.exception;

import java.util.UUID;

public final class BranchNotFoundException extends BookingDomainException {
  public BranchNotFoundException(UUID branchId) {
    super(404, "branch_not_found", "Branch '%s' was not found.".formatted(branchId));
  }
}
