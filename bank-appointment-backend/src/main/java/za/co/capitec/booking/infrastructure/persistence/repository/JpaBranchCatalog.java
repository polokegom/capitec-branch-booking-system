package za.co.capitec.booking.infrastructure.persistence.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.reactive.mutiny.Mutiny;
import za.co.capitec.booking.application.port.BranchCatalog;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.Pagination;
import za.co.capitec.booking.domain.model.PaginationWindow;
import za.co.capitec.booking.infrastructure.persistence.entity.BranchEntity;
import za.co.capitec.booking.infrastructure.persistence.mapper.PersistenceMapper;
import za.co.capitec.booking.infrastructure.persistence.utility.SearchTerm;

@ApplicationScoped
@RequiredArgsConstructor
public class JpaBranchCatalog implements BranchCatalog {
  private final Mutiny.SessionFactory sessionFactory;
  private final PersistenceMapper persistenceMapper;

  @Override
  public Uni<List<Branch>> search(String query, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 20));
    return executeSearch(SearchTerm.from(query), 0, safeLimit);
  }

  @Override
  public Uni<Pagination<Branch>> searchUsingPagination(String query, int startIndex, int endIndex) {
    PaginationWindow paginationWindow = PaginationWindow.from(startIndex, endIndex);
    SearchTerm searchTerm = SearchTerm.from(query);

    return countActive(searchTerm)
      .chain(total -> {
        if (total == 0L || paginationWindow.isEmpty()) {
          return Uni.createFrom().item(paginationWindow.empty(total));
        }
        return executeSearch(searchTerm, paginationWindow.startIndex(), paginationWindow.requestedItemCount())
          .map(items -> paginationWindow.toPagination(items, total));
      });
  }

  private Uni<Long> countActive(SearchTerm searchTerm) {
    return sessionFactory.withSession(session -> {
      if (searchTerm.isBlank()) {
        return session.createQuery(
            "select count(branch) from BranchEntity branch where branch.active = true",
            Long.class
          )
          .getSingleResult()
          .map(total -> total == null ? 0L : total);
      }

      return session.createQuery(
          """
          select count(branch)
          from BranchEntity branch
          where branch.active = true
            and (
              lower(branch.code) like :likeQuery
              or lower(branch.name) like :likeQuery
              or lower(branch.city) like :likeQuery
              or lower(coalesce(branch.province, '')) like :likeQuery
              or lower(coalesce(branch.country, '')) like :likeQuery
              or lower(coalesce(branch.address, '')) like :likeQuery
            )
          """,
          Long.class
        )
        .setParameter("likeQuery", searchTerm.likePattern())
        .getSingleResult()
        .map(total -> total == null ? 0L : total);
    });
  }

  private Uni<List<Branch>> executeSearch(SearchTerm searchTerm, int offset, int limit) {
    return sessionFactory.withSession(session -> {
      if (searchTerm.isBlank()) {
        return session.createQuery(
            "from BranchEntity branch where branch.active = true order by branch.name",
            BranchEntity.class
          )
          .setFirstResult(offset)
          .setMaxResults(limit)
          .getResultList()
          .map(persistenceMapper::toBranches);
      }

      return session.createQuery(
          """
          from BranchEntity branch
          where branch.active = true
            and (
              lower(branch.code) like :likeQuery
              or lower(branch.name) like :likeQuery
              or lower(branch.city) like :likeQuery
              or lower(coalesce(branch.province, '')) like :likeQuery
              or lower(coalesce(branch.country, '')) like :likeQuery
              or lower(coalesce(branch.address, '')) like :likeQuery
            )
          order by branch.name asc
          """,
          BranchEntity.class
        )
        .setParameter("likeQuery", searchTerm.likePattern())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList()
        .map(persistenceMapper::toBranches);
    });
  }

  @Override
  public Uni<Optional<Branch>> findById(UUID branchId) {
    return sessionFactory.withSession(session -> session.find(BranchEntity.class, branchId))
      .map(branchEntity -> Optional.ofNullable(branchEntity).map(persistenceMapper::toDomain));
  }
}
