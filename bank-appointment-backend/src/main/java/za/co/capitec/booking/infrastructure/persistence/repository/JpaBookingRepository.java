package za.co.capitec.booking.infrastructure.persistence.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.reactive.mutiny.Mutiny;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.exception.BookingReferenceCollisionException;
import za.co.capitec.booking.domain.exception.DuplicateBookingRequestException;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingStatus;
import za.co.capitec.booking.domain.model.Pagination;
import za.co.capitec.booking.domain.model.PaginationWindow;
import za.co.capitec.booking.infrastructure.persistence.entity.BookingEntity;
import za.co.capitec.booking.infrastructure.persistence.entity.BookingLookupEntity;
import za.co.capitec.booking.infrastructure.persistence.mapper.PersistenceMapper;
import za.co.capitec.booking.infrastructure.persistence.utility.SearchTerm;

@ApplicationScoped
@RequiredArgsConstructor
public class JpaBookingRepository implements BookingRepository {
  private final Mutiny.SessionFactory sessionFactory;
  private final PersistenceMapper persistenceMapper;

  @Override
  public Uni<Optional<Booking>> findByIdempotencyKey(String idempotencyKey) {
    return sessionFactory.withSession(session -> session.createQuery(
        "from BookingLookupEntity lookup where lookup.idempotencyKey = :idempotencyKey",
        BookingLookupEntity.class
      )
      .setParameter("idempotencyKey", idempotencyKey)
      .setMaxResults(1)
      .getResultList()
      .chain(lookups -> {
        if (lookups.isEmpty()) {
          return Uni.createFrom().item(Optional.empty());
        }
        return findBookingFromLookup(session, lookups.get(0));
      }));
  }

  @Override
  public Uni<Optional<Booking>> findByReference(String bookingReference) {
    return sessionFactory.withSession(session -> session.find(BookingLookupEntity.class, bookingReference)
      .chain(lookupEntity -> lookupEntity == null
        ? Uni.createFrom().item(Optional.empty())
        : findBookingFromLookup(session, lookupEntity)));
  }

  @Override
  public Uni<Optional<Booking>> findUpcomingByCustomerEmail(String customerEmail, OffsetDateTime currentDateTime) {
    return sessionFactory.withSession(session -> session.createQuery(
        "from BookingEntity entity " +
          "where lower(entity.customerEmail) = lower(:customerEmail) " +
          "and entity.startDateTime > :currentDateTime " +
          "and entity.status = :status " +
          "order by entity.startDateTime asc",
        BookingEntity.class
      )
      .setParameter("customerEmail", customerEmail)
      .setParameter("currentDateTime", BookingDateTimes.toUtc(currentDateTime))
      .setParameter("status", BookingStatus.CONFIRMED)
      .setMaxResults(1)
      .getResultList()
      .map(this::firstBooking));
  }

  @Override
  public Uni<List<Booking>> findForAdmin(
    Collection<UUID> branchIds,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime
  ) {
    if (branchIds == null || branchIds.isEmpty()) {
      return Uni.createFrom().item(List.of());
    }
    BookingDateTimeFilter filter = BookingDateTimeFilter.from(startDateTime, endDateTime);
    return sessionFactory.withSession(session -> filter.bind(session.createQuery(
          "from BookingEntity entity " +
            "where entity.branchId in :branchIds " +
            "and entity.status = :status " +
            filter.whereClause() +
            "order by entity.startDateTime asc, entity.customerName asc",
          BookingEntity.class
        )
        .setParameter("branchIds", branchIds)
        .setParameter("status", BookingStatus.CONFIRMED))
      .getResultList()
      .map(persistenceMapper::toBookings));
  }

  @Override
  public Uni<Pagination<Booking>> findForAdminUsingPagination(
    Collection<UUID> branchIds,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    int startIndex,
    int endIndex
  ) {
    if (branchIds == null || branchIds.isEmpty()) {
      return Uni.createFrom().item(Pagination.empty(startIndex, endIndex));
    }
    BookingDateTimeFilter filter = BookingDateTimeFilter.from(startDateTime, endDateTime);
    PaginationWindow paginationWindow = PaginationWindow.from(startIndex, endIndex);

    return sessionFactory.withSession(session -> countAdminBookings(session, branchIds, filter)
      .chain(total -> {
        if (total == 0L || paginationWindow.isEmpty()) {
          return Uni.createFrom().item(paginationWindow.empty(total));
        }
        return filter.bind(session.createQuery(
            "from BookingEntity entity " +
              "where entity.branchId in :branchIds " +
              "and entity.status = :status " +
              filter.whereClause() +
              "order by entity.startDateTime asc, entity.customerName asc",
            BookingEntity.class
          )
          .setParameter("branchIds", branchIds)
          .setParameter("status", BookingStatus.CONFIRMED))
          .setFirstResult(paginationWindow.startIndex())
          .setMaxResults(paginationWindow.requestedItemCount())
          .getResultList()
          .map(persistenceMapper::toBookings)
          .map(items -> paginationWindow.toPagination(items, total));
      }));
  }

  @Override
  public Uni<Booking> save(Booking booking) {
    BookingEntity bookingEntity = persistenceMapper.toEntity(booking);
    BookingLookupEntity lookupEntity = persistenceMapper.toLookupEntity(booking);
    return sessionFactory.withTransaction((session, transaction) -> session
        .persist(bookingEntity)
        .chain(() -> session.persist(lookupEntity))
        .call(session::flush)
        .replaceWith(booking))
      .onFailure()
      .transform(this::translate);
  }

  @Override
  public Uni<List<Booking>> findByCustomerEmail(String customerEmail) {
    if (customerEmail == null || customerEmail.isBlank()) {
      return Uni.createFrom().item(List.of());
    }
    return sessionFactory.withSession(session -> session.createQuery(
        "from BookingEntity entity " +
          "where lower(entity.customerEmail) = lower(:customerEmail) " +
          "order by entity.startDateTime desc",
        BookingEntity.class
      )
      .setParameter("customerEmail", customerEmail)
      .getResultList()
      .map(persistenceMapper::toBookings));
  }

  @Override
  public Uni<Pagination<Booking>> findByCustomerEmailUsingPagination(
    String customerEmail,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    String branchSearch,
    int startIndex,
    int endIndex
  ) {
    if (customerEmail == null || customerEmail.isBlank()) {
      return Uni.createFrom().item(Pagination.empty(startIndex, endIndex));
    }
    PaginationWindow paginationWindow = PaginationWindow.from(startIndex, endIndex);
    CustomerBookingFilter filter = CustomerBookingFilter.from(customerEmail, startDateTime, endDateTime, branchSearch);

    return sessionFactory.withSession(session -> countCustomerBookings(session, filter)
      .chain(total -> {
        if (total == 0L || paginationWindow.isEmpty()) {
          return Uni.createFrom().item(paginationWindow.empty(total));
        }
        return filter.bind(session.createQuery(
            "from BookingEntity entity " + filter.whereClause() +
              " order by entity.startDateTime desc",
            BookingEntity.class
          ))
          .setFirstResult(paginationWindow.startIndex())
          .setMaxResults(paginationWindow.requestedItemCount())
          .getResultList()
          .map(persistenceMapper::toBookings)
          .map(items -> paginationWindow.toPagination(items, total));
      }));
  }

  @Override
  public Uni<Booking> update(Booking booking) {
    return sessionFactory.withTransaction((session, transaction) -> session.find(BookingEntity.class, booking.id())
      .chain(managed -> {
        if (managed == null) {
          return Uni.createFrom().failure(new IllegalStateException("Booking not found for update: " + booking.id()));
        }
        managed.status = booking.status();
        managed.updatedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        return session.flush().replaceWith(persistenceMapper.toDomain(managed));
      }));
  }

  private Uni<Long> countAdminBookings(Mutiny.Session session, Collection<UUID> branchIds, BookingDateTimeFilter filter) {
    return filter.bind(session.createQuery(
        "select count(entity) from BookingEntity entity " +
          "where entity.branchId in :branchIds " +
          "and entity.status = :status " +
          filter.whereClause(),
        Long.class
      )
      .setParameter("branchIds", branchIds)
      .setParameter("status", BookingStatus.CONFIRMED))
      .getSingleResult()
      .map(total -> total == null ? 0L : total);
  }

  private Uni<Long> countCustomerBookings(Mutiny.Session session, CustomerBookingFilter filter) {
    return filter.bind(session.createQuery(
        "select count(entity) from BookingEntity entity " + filter.whereClause(),
        Long.class
      ))
      .getSingleResult()
      .map(total -> total == null ? 0L : total);
  }

  private Uni<Optional<Booking>> findBookingFromLookup(Mutiny.Session session, BookingLookupEntity lookupEntity) {
    return session.find(BookingEntity.class, lookupEntity.bookingId)
      .map(bookingEntity -> Optional.ofNullable(bookingEntity).map(persistenceMapper::toDomain));
  }

  private Optional<Booking> firstBooking(List<BookingEntity> entities) {
    return entities.stream()
      .findFirst()
      .map(persistenceMapper::toDomain);
  }

  private RuntimeException translate(Throwable throwable) {
    String message = findMessage(throwable);
    if (message.contains("uq_booking_lookup_idempotency")) {
      return new DuplicateBookingRequestException("The idempotency key has already been processed.");
    }
    if (message.contains("pk_booking_lookup")) {
      return new BookingReferenceCollisionException("Booking reference collision detected.");
    }
    return throwable instanceof RuntimeException runtimeException
      ? runtimeException
      : new IllegalStateException(throwable);
  }

  private String findMessage(Throwable throwable) {
    Throwable current = throwable;
    String message = throwable.getMessage() == null ? "" : throwable.getMessage();
    while (current != null) {
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        message = current.getMessage();
      }
      current = current.getCause();
    }
    return message;
  }

  private record BookingDateTimeFilter(OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
    private static BookingDateTimeFilter from(OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
      return new BookingDateTimeFilter(
        BookingDateTimes.toUtc(startDateTime),
        BookingDateTimes.toUtc(endDateTime)
      );
    }

    private String whereClause() {
      StringBuilder conditions = new StringBuilder();
      if (startDateTime != null) {
        conditions.append("and entity.startDateTime >= :startDateTime ");
      }
      if (endDateTime != null) {
        conditions.append("and entity.startDateTime <= :endDateTime ");
      }
      return conditions.toString();
    }

    private <T> Mutiny.SelectionQuery<T> bind(Mutiny.SelectionQuery<T> query) {
      if (startDateTime != null) {
        query.setParameter("startDateTime", startDateTime);
      }
      if (endDateTime != null) {
        query.setParameter("endDateTime", endDateTime);
      }
      return query;
    }
  }

  private record CustomerBookingFilter(
    String customerEmail,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    String branchSearchPattern
  ) {
    private static CustomerBookingFilter from(
      String customerEmail,
      OffsetDateTime startDateTime,
      OffsetDateTime endDateTime,
      String branchSearch
    ) {
      SearchTerm branchSearchTerm = SearchTerm.from(branchSearch);
      String branchSearchPattern = branchSearchTerm.isBlank() ? null : branchSearchTerm.likePattern();
      return new CustomerBookingFilter(
        customerEmail,
        BookingDateTimes.toUtc(startDateTime),
        BookingDateTimes.toUtc(endDateTime),
        branchSearchPattern
      );
    }

    private String whereClause() {
      StringBuilder conditions = new StringBuilder(
        "where lower(entity.customerEmail) = lower(:customerEmail) "
      );
      if (startDateTime != null) {
        conditions.append("and entity.startDateTime >= :startDateTime ");
      }
      if (endDateTime != null) {
        conditions.append("and entity.startDateTime <= :endDateTime ");
      }
      if (branchSearchPattern != null) {
        conditions.append(
          "and exists (" +
            "  select 1 from BranchEntity branch where branch.id = entity.branchId " +
            "  and (lower(branch.name) like :branchSearchPattern " +
            "       or lower(branch.city) like :branchSearchPattern " +
            "       or lower(branch.code) like :branchSearchPattern)" +
            ") "
        );
      }
      return conditions.toString();
    }

    private <T> Mutiny.SelectionQuery<T> bind(Mutiny.SelectionQuery<T> query) {
      query.setParameter("customerEmail", customerEmail);
      if (startDateTime != null) {
        query.setParameter("startDateTime", startDateTime);
      }
      if (endDateTime != null) {
        query.setParameter("endDateTime", endDateTime);
      }
      if (branchSearchPattern != null) {
        query.setParameter("branchSearchPattern", branchSearchPattern);
      }
      return query;
    }
  }
}
