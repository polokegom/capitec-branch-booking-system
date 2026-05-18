package za.co.capitec.booking.infrastructure.persistence.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.reactive.mutiny.Mutiny;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.domain.exception.BookingReferenceCollisionException;
import za.co.capitec.booking.domain.exception.DuplicateBookingRequestException;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingStatus;
import za.co.capitec.booking.domain.model.Pagination;
import za.co.capitec.booking.domain.model.PaginationWindow;
import za.co.capitec.booking.infrastructure.persistence.entity.BookingEntity;
import za.co.capitec.booking.infrastructure.persistence.mapper.PersistenceMapper;
import za.co.capitec.booking.infrastructure.persistence.utility.SearchTerm;

@ApplicationScoped
@RequiredArgsConstructor
public class JpaBookingRepository implements BookingRepository {
  private final Mutiny.SessionFactory sessionFactory;
  private final PersistenceMapper persistenceMapper;

  @Override
  public Uni<Optional<Booking>> findByIdempotencyKey(String idempotencyKey) {
    return sessionFactory.withSession(session -> session.createNativeQuery(
        "select * from booking.booking entity where entity.idempotency_key = :idempotencyKey",
        BookingEntity.class
      )
      .setParameter("idempotencyKey", idempotencyKey)
      .setMaxResults(1)
      .getResultList()
      .map(this::firstBooking));
  }

  @Override
  public Uni<Optional<Booking>> findByReference(String bookingReference) {
    return sessionFactory.withSession(session -> session.createNativeQuery(
        "select * from booking.booking entity where entity.booking_reference = :bookingReference",
        BookingEntity.class
      )
      .setParameter("bookingReference", bookingReference)
      .setMaxResults(1)
      .getResultList()
      .map(this::firstBooking));
  }

  @Override
  public Uni<Optional<Booking>> findUpcomingByCustomerEmail(String customerEmail, LocalDateTime currentDateTime) {
    return sessionFactory.withSession(session -> session.createNativeQuery(
        "select * from booking.booking entity " +
          "where lower(entity.customer_email) = lower(:customerEmail) " +
          "and entity.start_datetime > :currentDateTime " +
          "and entity.status = :status " +
          "order by entity.start_datetime asc",
        BookingEntity.class
      )
      .setParameter("customerEmail", customerEmail)
      .setParameter("currentDateTime", currentDateTime)
      .setParameter("status", BookingStatus.CONFIRMED.name())
      .setMaxResults(1)
      .getResultList()
      .map(this::firstBooking));
  }

  @Override
  public Uni<List<Booking>> findForAdmin(
    Collection<UUID> branchIds,
    LocalDateTime startDateTime,
    LocalDateTime endDateTime
  ) {
    if (branchIds == null || branchIds.isEmpty()) {
      return Uni.createFrom().item(List.of());
    }
    BookingDateTimeFilter filter = BookingDateTimeFilter.from(startDateTime, endDateTime);
    return sessionFactory.withSession(session -> filter.bind(session.createNativeQuery(
          "select * from booking.booking entity " +
            "where entity.branch_id in (:branchIds) " +
            "and entity.status = :status " +
            filter.whereClause() +
            "order by entity.start_datetime asc, entity.customer_name asc",
          BookingEntity.class
        )
        .setParameter("branchIds", branchIds)
        .setParameter("status", BookingStatus.CONFIRMED.name()))
      .getResultList()
      .map(persistenceMapper::toBookings));
  }

  @Override
  public Uni<Pagination<Booking>> findForAdminUsingPagination(
    Collection<UUID> branchIds,
    LocalDateTime startDateTime,
    LocalDateTime endDateTime,
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
        return filter.bind(session.createNativeQuery(
            "select * from booking.booking entity " +
              "where entity.branch_id in (:branchIds) " +
              "and entity.status = :status " +
              filter.whereClause() +
              "order by entity.start_datetime asc, entity.customer_name asc",
            BookingEntity.class
          )
          .setParameter("branchIds", branchIds)
          .setParameter("status", BookingStatus.CONFIRMED.name()))
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
    return sessionFactory.withTransaction((session, transaction) -> session
        .persist(bookingEntity)
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
    return sessionFactory.withSession(session -> session.createNativeQuery(
        "select * from booking.booking entity " +
          "where lower(entity.customer_email) = lower(:customerEmail) " +
          "order by entity.start_datetime desc",
        BookingEntity.class
      )
      .setParameter("customerEmail", customerEmail)
      .getResultList()
      .map(persistenceMapper::toBookings));
  }

  @Override
  public Uni<Pagination<Booking>> findByCustomerEmailUsingPagination(
    String customerEmail,
    LocalDateTime startDateTime,
    LocalDateTime endDateTime,
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
        return filter.bind(session.createNativeQuery(
            "select * from booking.booking entity " + filter.whereClause() +
              " order by entity.start_datetime desc",
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

  @Override
  public Uni<List<LocalDateTime>> findConfirmedStartDateTimes(
    UUID branchId,
    LocalDateTime startInclusive,
    LocalDateTime endExclusive
  ) {
    return sessionFactory.withSession(session -> session.createNativeQuery(
        "select * from booking.booking entity " +
          "where entity.branch_id = :branchId " +
          "and entity.status = :status " +
          "and entity.start_datetime >= :startInclusive " +
          "and entity.start_datetime < :endExclusive",
        BookingEntity.class
      )
      .setParameter("branchId", branchId)
      .setParameter("status", BookingStatus.CONFIRMED.name())
      .setParameter("startInclusive", startInclusive)
      .setParameter("endExclusive", endExclusive)
      .getResultList()
      .map(entities -> entities.stream().map(entity -> entity.startDateTime).toList()));
  }

  @Override
  public Uni<Boolean> existsConfirmedBookingAt(UUID branchId, LocalDateTime startDateTime) {
    return sessionFactory.withSession(session -> session.createNativeQuery(
        "select count(*) from booking.booking entity " +
          "where entity.branch_id = :branchId " +
          "and entity.status = :status " +
          "and entity.start_datetime = :startDateTime",
        Long.class
      )
      .setParameter("branchId", branchId)
      .setParameter("status", BookingStatus.CONFIRMED.name())
      .setParameter("startDateTime", startDateTime)
      .getSingleResult()
      .map(total -> total != null && total > 0L));
  }

  private Uni<Long> countAdminBookings(Mutiny.Session session, Collection<UUID> branchIds, BookingDateTimeFilter filter) {
    return filter.bind(session.createNativeQuery(
        "select count(*) from booking.booking entity " +
          "where entity.branch_id in (:branchIds) " +
          "and entity.status = :status " +
          filter.whereClause(),
        Long.class
      )
      .setParameter("branchIds", branchIds)
      .setParameter("status", BookingStatus.CONFIRMED.name()))
      .getSingleResult()
      .map(total -> total == null ? 0L : total);
  }

  private Uni<Long> countCustomerBookings(Mutiny.Session session, CustomerBookingFilter filter) {
    return filter.bind(session.createNativeQuery(
        "select count(*) from booking.booking entity " + filter.whereClause(),
        Long.class
      ))
      .getSingleResult()
      .map(total -> total == null ? 0L : total);
  }

  private Optional<Booking> firstBooking(List<BookingEntity> entities) {
    return entities.stream()
      .findFirst()
      .map(persistenceMapper::toDomain);
  }

  private RuntimeException translate(Throwable throwable) {
    String message = findMessage(throwable);
    if (message.contains("uq_booking_idempotency_key")) {
      return new DuplicateBookingRequestException("The idempotency key has already been processed.");
    }
    if (message.contains("uq_booking_reference")) {
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

  private record BookingDateTimeFilter(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    private static BookingDateTimeFilter from(LocalDateTime startDateTime, LocalDateTime endDateTime) {
      return new BookingDateTimeFilter(startDateTime, endDateTime);
    }

    private String whereClause() {
      StringBuilder conditions = new StringBuilder();
      if (startDateTime != null) {
        conditions.append("and entity.start_datetime >= :startDateTime ");
      }
      if (endDateTime != null) {
        conditions.append("and entity.start_datetime <= :endDateTime ");
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
    LocalDateTime startDateTime,
    LocalDateTime endDateTime,
    String branchSearchPattern
  ) {
    private static CustomerBookingFilter from(
      String customerEmail,
      LocalDateTime startDateTime,
      LocalDateTime endDateTime,
      String branchSearch
    ) {
      SearchTerm branchSearchTerm = SearchTerm.from(branchSearch);
      String branchSearchPattern = branchSearchTerm.isBlank() ? null : branchSearchTerm.likePattern();
      return new CustomerBookingFilter(
        customerEmail,
        startDateTime,
        endDateTime,
        branchSearchPattern
      );
    }

    private String whereClause() {
      StringBuilder conditions = new StringBuilder(
        "where lower(entity.customer_email) = lower(:customerEmail) "
      );
      if (startDateTime != null) {
        conditions.append("and entity.start_datetime >= :startDateTime ");
      }
      if (endDateTime != null) {
        conditions.append("and entity.start_datetime <= :endDateTime ");
      }
      if (branchSearchPattern != null) {
        conditions.append(
          "and exists (" +
            "  select 1 from booking.branch branch where branch.id = entity.branch_id " +
            "  and lower(branch.code || ' ' || branch.name || ' ' || branch.city || ' ' || coalesce(branch.province, '') || ' ' || branch.country || ' ' || coalesce(branch.address, '')) like :branchSearchPattern" +
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
