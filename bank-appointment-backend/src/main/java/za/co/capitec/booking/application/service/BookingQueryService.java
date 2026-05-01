package za.co.capitec.booking.application.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import za.co.capitec.booking.application.port.BranchCatalog;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.domain.exception.BookingNotFoundException;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingDetails;
import za.co.capitec.booking.domain.model.Pagination;

@ApplicationScoped
@RequiredArgsConstructor
public class BookingQueryService {
  private final BookingRepository bookingRepository;
  private final BranchCatalog branchCatalog;

  public Uni<Booking> findByReference(String bookingReference) {
    return bookingRepository.findByReference(bookingReference)
      .map(booking -> booking.orElseThrow(() -> new BookingNotFoundException(bookingReference)));
  }

  public Uni<List<Booking>> findByCustomerEmail(String customerEmail) {
    if (customerEmail == null || customerEmail.isBlank()) {
      return Uni.createFrom().item(List.of());
    }
    return bookingRepository.findByCustomerEmail(customerEmail);
  }

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
    return bookingRepository.findByCustomerEmailUsingPagination(customerEmail, startDateTime, endDateTime, branchSearch, startIndex, endIndex);
  }

  public Uni<Pagination<BookingDetails>> findCustomerBookingDetailsUsingPagination(
    String customerEmail,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    String branchSearch,
    int startIndex,
    int endIndex
  ) {
    return findByCustomerEmailUsingPagination(customerEmail, startDateTime, endDateTime, branchSearch, startIndex, endIndex)
      .chain(this::attachBranches);
  }

  private Uni<Pagination<BookingDetails>> attachBranches(Pagination<Booking> pagination) {
    if (pagination.items().isEmpty()) {
      return Uni.createFrom().item(new Pagination<>(List.of(), pagination.total(), pagination.startIndex(), pagination.endIndex()));
    }

    List<Uni<BookingDetails>> detailLookups = pagination.items().stream()
      .map(booking -> branchCatalog.findById(booking.branchId())
        .map(branch -> new BookingDetails(booking, branch.orElse(null))))
      .toList();

    return Uni.combine().all().unis(detailLookups)
      .with(items -> {
        @SuppressWarnings("unchecked")
        List<BookingDetails> bookingDetails = (List<BookingDetails>) (List<?>) items;
        return new Pagination<>(bookingDetails, pagination.total(), pagination.startIndex(), pagination.endIndex());
      });
  }
}
