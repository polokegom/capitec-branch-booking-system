package za.co.capitec.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.application.port.BranchRepository;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingStatus;
import za.co.capitec.booking.domain.model.Branch;

@QuarkusTest
class PostgresRepositoryIT {
  private static final UUID SANDTON_BRANCH_ID = UUID.fromString("0d0fb1e2-3d44-4a66-9f4b-0d745e9f1a03");

  @Inject
  BranchRepository branchRepository;

  @Inject
  BookingRepository bookingRepository;

  @Test
  void shouldLoadSeededBranchesFromPostgres() {
    var branches = branchRepository.search("Sandton", 10).await().indefinitely();

    assertThat(branches)
      .extracting(branch -> branch.code())
      .contains("JHB-SDT");
  }

  @Test
  void shouldTrackConfirmedBookingOccupancyAndFreeSlotOnCancellation() {
    LocalDate appointmentDate = nextWeekday();
    LocalTime bookingSlotStartTime = LocalTime.of(14, 30);
    LocalDateTime startDateTime = toDateTime(appointmentDate, bookingSlotStartTime);
    LocalDateTime windowStart = toDateTime(appointmentDate, LocalTime.MIDNIGHT);
    LocalDateTime windowEnd = toDateTime(appointmentDate.plusDays(1), LocalTime.MIDNIGHT);

    Booking confirmed = booking("occupancy-" + UUID.randomUUID() + "@capitec.co.za", appointmentDate, bookingSlotStartTime);
    bookingRepository.save(confirmed).await().indefinitely();

    assertThat(bookingRepository.existsConfirmedBookingAt(SANDTON_BRANCH_ID, startDateTime).await().indefinitely()).isTrue();
    assertThat(bookingRepository.findConfirmedStartDateTimes(SANDTON_BRANCH_ID, windowStart, windowEnd).await().indefinitely())
      .contains(startDateTime);

    Booking cancelled = confirmed.toBuilder().status(BookingStatus.CANCELLED).build();
    bookingRepository.update(cancelled).await().indefinitely();

    assertThat(bookingRepository.existsConfirmedBookingAt(SANDTON_BRANCH_ID, startDateTime).await().indefinitely()).isFalse();
  }

  @Test
  void shouldFindOnlyCustomerBookingsThatHaveNotStarted() {
    LocalDate currentDate = nextWeekday();
    String customerEmail = "postgres-it-" + UUID.randomUUID() + "@capitec.co.za";

    Booking startedBooking = booking(customerEmail, currentDate, LocalTime.of(9, 0));
    Booking upcomingBooking = booking(customerEmail, currentDate, LocalTime.of(11, 0));

    bookingRepository.save(startedBooking).await().indefinitely();
    bookingRepository.save(upcomingBooking).await().indefinitely();

    Optional<Booking> nextBooking = bookingRepository
      .findUpcomingByCustomerEmail(customerEmail, toDateTime(currentDate, LocalTime.of(10, 0)))
      .await()
      .indefinitely();

    assertThat(nextBooking).hasValueSatisfying(booking ->
      assertThat(booking.bookingReference()).isEqualTo(upcomingBooking.bookingReference()));

    assertThat(bookingRepository.findUpcomingByCustomerEmail(customerEmail, toDateTime(currentDate, LocalTime.of(12, 0))).await().indefinitely())
      .isEmpty();
  }

  private static Booking booking(String customerEmail, LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
    String uniqueToken = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    return Booking.builder()
      .id(UUID.randomUUID())
      .bookingReference("BKG-" + uniqueToken)
      .idempotencyKey("idem-" + uniqueToken)
      .branchId(SANDTON_BRANCH_ID)
      .startDateTime(toDateTime(appointmentDate, bookingSlotStartTime))
      .endDateTime(toDateTime(appointmentDate, bookingSlotStartTime.plusMinutes(Branch.SLOT_MINUTES)))
      .customerName("Postgres Integration")
      .customerEmail(customerEmail)
      .preferredLanguage("en")
      .status(BookingStatus.CONFIRMED)
      .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
      .build();
  }

  private static LocalDate nextWeekday() {
    LocalDate date = LocalDate.now().plusDays(3);
    while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      date = date.plusDays(1);
    }
    return date;
  }

  private static LocalDateTime toDateTime(LocalDate appointmentDate, LocalTime bookingSlotTime) {
    return LocalDateTime.of(appointmentDate, bookingSlotTime);
  }
}
