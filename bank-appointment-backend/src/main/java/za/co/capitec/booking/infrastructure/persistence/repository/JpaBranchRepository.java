package za.co.capitec.booking.infrastructure.persistence.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.reactive.mutiny.Mutiny;
import za.co.capitec.booking.application.port.BranchRepository;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.Pagination;
import za.co.capitec.booking.domain.model.PaginationWindow;
import za.co.capitec.booking.infrastructure.persistence.entity.BranchEntity;
import za.co.capitec.booking.infrastructure.persistence.mapper.PersistenceMapper;
import za.co.capitec.booking.infrastructure.persistence.utility.SearchTerm;

@ApplicationScoped
@RequiredArgsConstructor
public class JpaBranchRepository implements BranchRepository {
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
        return session.createNativeQuery(
            "select count(*) from booking.branch where active = true",
            Long.class
          )
          .getSingleResult()
          .map(total -> total == null ? 0L : total);
      }

      return session.createNativeQuery(
          """
          select count(*)
          from booking.branch
          where active = true
            and (
              lower(code) like :likeQuery
              or lower(name) like :likeQuery
              or lower(city) like :likeQuery
              or lower(coalesce(province, '')) like :likeQuery
              or lower(coalesce(country, '')) like :likeQuery
              or lower(coalesce(address, '')) like :likeQuery
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
        return session.createNativeQuery(
            "select * from booking.branch where active = true order by name",
            BranchEntity.class
          )
          .setFirstResult(offset)
          .setMaxResults(limit)
          .getResultList()
          .map(persistenceMapper::toBranches);
      }

      return session.createNativeQuery(
          """
          select *
          from booking.branch
          where active = true
            and (
              lower(code) like :likeQuery
              or lower(name) like :likeQuery
              or lower(city) like :likeQuery
              or lower(coalesce(province, '')) like :likeQuery
              or lower(coalesce(country, '')) like :likeQuery
              or lower(coalesce(address, '')) like :likeQuery
            )
          order by name asc
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
