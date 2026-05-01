package za.co.capitec.booking.application.port;

import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.Pagination;

public interface BranchCatalog {
  Uni<List<Branch>> search(String query, int limit);

  default Uni<Pagination<Branch>> searchUsingPagination(String query, int startIndex, int endIndex) {
    int size = Math.max(1, endIndex - startIndex);
    return search(query, startIndex + size)
      .map(branches -> Pagination.slice(branches, startIndex, endIndex));
  }

  Uni<Optional<Branch>> findById(UUID branchId);
}
