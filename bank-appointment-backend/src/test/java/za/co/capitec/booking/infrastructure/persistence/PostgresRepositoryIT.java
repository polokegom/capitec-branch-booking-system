package za.co.capitec.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.application.port.BranchCatalog;
import za.co.capitec.booking.application.port.BookingSlotInventoryRepository;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingStatus;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;

@QuarkusTest
class PostgresRepositoryIT {
  private static final UUID SANDTON_BRANCH_ID = UUID.fromString("0d0fb1e2-3d44-4a66-9f4b-0d745e9f1a03");
  private static final ZoneId SANDTON_ZONE = ZoneId.of("Africa/Johannesburg");

  @Inject
  BranchCatalog branchCatalog;

  @Inject
  BookingSlotInventoryRepository bookingSlotInventoryRepository;

  @Inject
  BookingRepository bookingRepository;

  @Test
  void shouldLoadSeededBranchesFromPostgres() {
    var branches = branchCatalog.search("Sandton", 10).await().indefinitely();

    assertThat(branches)
      .extracting(branch -> branch.code())
      .contains("JHB-SDT");
  }

  @Test
  void shouldMaterializeReserveAndReleaseBookingSlotInventoryInPostgres() {
    LocalDate appointmentDate = nextWeekday();
    LocalTime bookingSlotStartTime = LocalTime.of(9, 0);

    bookingSlotInventoryRepository.ensureInventory(SANDTON_BRANCH_ID, appointmentDate).await().indefinitely();

    assertThat(bookingSlotInventoryRepository.findAvailability(SANDTON_BRANCH_ID, appointmentDate).await().indefinitely())
      .extracting(BookingSlotAvailability::bookingSlotStartTime)
      .contains(bookingSlotStartTime);

    assertThat(bookingSlotInventoryRepository.reserveBookingSlot(SANDTON_BRANCH_ID, appointmentDate, bookingSlotStartTime).await().indefinitely()).isTrue();
    assertThat(bookingSlotInventoryRepository.reserveBookingSlot(SANDTON_BRANCH_ID, appointmentDate, bookingSlotStartTime).await().indefinitely()).isFalse();
    assertThat(bookingSlotInventoryRepository.findAvailability(SANDTON_BRANCH_ID, appointmentDate).await().indefinitely())
      .extracting(BookingSlotAvailability::bookingSlotStartTime)
      .doesNotContain(bookingSlotStartTime);

    assertThat(bookingSlotInventoryRepository.releaseBookingSlot(SANDTON_BRANCH_ID, appointmentDate, bookingSlotStartTime).await().indefinitely()).isTrue();
    assertThat(bookingSlotInventoryRepository.releaseBookingSlot(SANDTON_BRANCH_ID, appointmentDate, bookingSlotStartTime).await().indefinitely()).isFalse();
    assertThat(bookingSlotInventoryRepository.findAvailability(SANDTON_BRANCH_ID, appointmentDate).await().indefinitely())
      .extracting(BookingSlotAvailability::bookingSlotStartTime)
      .contains(bookingSlotStartTime);
  }

  @Test
  void shouldFindOnlyCustomerBookingsThatHaveNotStarted() {
    LocalDate currentDate = nextWeekday();
    String customerEmail = "postgres-it-" + UUID.randomUUID() + "@example.co.za";

    Booking startedBooking = booking(customerEmail, currentDate, LocalTime.of(9, 0));
    Booking upcomingBooking = booking(customerEmail, currentDate, LocalTime.of(11, 0));

    bookingRepository.save(startedBooking).await().indefinitely();
    bookingRepository.save(upcomingBooking).await().indefinitely();

    Optional<Booking> nextBooking = bookingRepository
      .findUpcomingByCustomerEmail(customerEmail, toSandtonUtc(currentDate, LocalTime.of(10, 0)))
      .await()
      .indefinitely();

    assertThat(nextBooking).hasValueSatisfying(booking ->
      assertThat(booking.bookingReference()).isEqualTo(upcomingBooking.bookingReference()));

    assertThat(bookingRepository.findUpcomingByCustomerEmail(customerEmail, toSandtonUtc(currentDate, LocalTime.of(12, 0))).await().indefinitely())
      .isEmpty();
  }

  private static Booking booking(String customerEmail, LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
    String uniqueToken = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    return Booking.builder()
      .id(UUID.randomUUID())
      .bookingReference("BKG-" + uniqueToken)
      .idempotencyKey("idem-" + uniqueToken)
      .branchId(SANDTON_BRANCH_ID)
      .startDateTime(toSandtonUtc(appointmentDate, bookingSlotStartTime))
      .endDateTime(toSandtonUtc(appointmentDate, bookingSlotStartTime.plusMinutes(30)))
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

  private static OffsetDateTime toSandtonUtc(LocalDate appointmentDate, LocalTime bookingSlotTime) {
    return BookingDateTimes.toUtc(appointmentDate, bookingSlotTime, SANDTON_ZONE);
  }
}
