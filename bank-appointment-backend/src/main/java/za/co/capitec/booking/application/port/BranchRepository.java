package za.co.capitec.booking.application.port;

import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.Pagination;

public interface BranchRepository {
  Uni<List<Branch>> search(String query, int limit);

  Uni<Pagination<Branch>> searchUsingPagination(String query, int startIndex, int endIndex);

  Uni<Optional<Branch>> findById(UUID branchId);
}
