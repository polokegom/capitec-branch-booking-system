package za.co.capitec.booking.application.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;
import za.co.capitec.booking.application.port.BranchRepository;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.Pagination;

@ApplicationScoped
@RequiredArgsConstructor
public class BranchQueryService {
  private final BranchRepository branchRepository;

  public Uni<List<Branch>> searchBranches(String query, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 20));
    return branchRepository.search(query, safeLimit);
  }

  public Uni<Pagination<Branch>> searchBranchesUsingPagination(String query, int startIndex, int endIndex) {
    return branchRepository.searchUsingPagination(query, startIndex, endIndex);
  }
}
