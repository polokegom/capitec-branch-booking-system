package za.co.capitec.booking.application.port;

import io.smallrye.mutiny.Uni;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.Pagination;

public interface BookingRepository {

  Uni<Optional<Booking>> findByIdempotencyKey(String idempotencyKey);

  Uni<Optional<Booking>> findByReference(String bookingReference);

  Uni<Optional<Booking>> findUpcomingByCustomerEmail(String customerEmail, OffsetDateTime currentDateTime);

  Uni<List<Booking>> findForAdmin(
    Collection<UUID> branchIds,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime
  );

  default Uni<Pagination<Booking>> findForAdminUsingPagination(
    Collection<UUID> branchIds,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    int startIndex,
    int endIndex
  ) {
    return findForAdmin(branchIds, startDateTime, endDateTime)
      .map(bookings -> Pagination.slice(bookings, startIndex, endIndex));
  }

  Uni<List<Booking>> findByCustomerEmail(String customerEmail);

  default Uni<Pagination<Booking>> findByCustomerEmailUsingPagination(
    String customerEmail,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    String branchSearch,
    int startIndex,
    int endIndex
  ) {
    String customerEmailFilter = customerEmail == null
      ? null
      : customerEmail.trim().toLowerCase(Locale.ROOT);
    if (customerEmailFilter == null || customerEmailFilter.isEmpty()) {
      return Uni.createFrom().item(Pagination.empty(startIndex, endIndex));
    }
    String branchSearchFilter = branchSearch == null
      ? null
      : branchSearch.trim().toLowerCase(Locale.ROOT);
    return findByCustomerEmail(customerEmail)
      .map(bookings -> bookings.stream()
        .filter(booking -> startDateTime == null || !booking.startDateTime().isBefore(startDateTime))
        .filter(booking -> endDateTime == null || !booking.startDateTime().isAfter(endDateTime))
        .filter(booking -> branchSearchFilter == null || branchSearchFilter.isEmpty())
        .toList())
      .map(filtered -> Pagination.slice(filtered, startIndex, endIndex));
  }

  Uni<Booking> save(Booking booking);

  Uni<Booking> update(Booking booking);
}
