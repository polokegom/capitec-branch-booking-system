package za.co.capitec.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.mutiny.Uni;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.capitec.booking.application.configuration.EmailDeliveryGuardrails;
import za.co.capitec.booking.application.configuration.CountriesWithBankBranches;
import za.co.capitec.booking.application.command.CreateBookingCommand;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.application.port.BranchCatalog;
import za.co.capitec.booking.application.port.BookingSlotInventoryRepository;
import za.co.capitec.booking.application.service.BookingCommandService;
import za.co.capitec.booking.application.service.BookingNotificationService;
import za.co.capitec.booking.application.utility.BookingDateTimes;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingStatus;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;
import za.co.capitec.booking.domain.exception.CustomerHasActiveBookingException;

class BookingCommandServiceTest {
  private static final ZoneId BRANCH_ZONE = ZoneId.of("Africa/Johannesburg");
  private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-20T08:00:00Z"), BRANCH_ZONE);

  @Test
  void shouldReturnSameBookingForRepeatedIdempotencyKey() {
    UUID branchId = UUID.fromString("0d0fb1e2-3d44-4a66-9f4b-0d745e9f1a03");
    LocalDate appointmentDate = LocalDate.of(2026, 4, 21);
    LocalTime bookingSlotStartTime = LocalTime.of(9, 0);

    InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    InMemoryBranchCatalog branchCatalog = new InMemoryBranchCatalog(sandtonCityBranch(branchId));
    InMemoryBookingSlotInventoryRepository bookingSlotInventoryRepository = new InMemoryBookingSlotInventoryRepository(
      branchId,
      appointmentDate,
      bookingSlotStartTime,
      LocalTime.of(9, 30)
    );

    BookingReferenceGenerator referenceGenerator = new BookingReferenceGenerator() {
      @Override
      public String nextReference() {
        return "BKG-20260420-TEST0001";
      }
    };

    BookingCommandService bookingCommandService = new BookingCommandService(
      bookingRepository,
      branchCatalog,
      bookingSlotInventoryRepository,
      new BookingPolicy(45, FIXED_CLOCK),
      referenceGenerator,
      new NoOpBookingNotificationService(),
      new TestCountriesWithBankBranches()
    );

    CreateBookingCommand command = new CreateBookingCommand(
      branchId,
      toBranchUtc(appointmentDate, bookingSlotStartTime),
      toBranchUtc(appointmentDate, LocalTime.of(9, 30)),
      "Tebogo Ndlovu",
      "tebogo@example.co.za",
      "en",
      "idem-123"
    );

    Booking firstBooking = bookingCommandService.createBooking(command).await().indefinitely();
    Booking repeatedBooking = bookingCommandService.createBooking(command).await().indefinitely();

    assertThat(repeatedBooking).isEqualTo(firstBooking);
    assertThat(bookingSlotInventoryRepository.reservationCount).isEqualTo(1);
    assertThat(bookingRepository.savedBookings).hasSize(1);
    assertThat(firstBooking.status()).isEqualTo(BookingStatus.CONFIRMED);
  }

  @Test
  void shouldAllowBookingWhenEarlierSameDayAppointmentHasAlreadyStarted() {
    UUID branchId = UUID.fromString("0d0fb1e2-3d44-4a66-9f4b-0d745e9f1a03");
    LocalDate today = LocalDate.of(2026, 4, 20);

    InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    bookingRepository.seed(confirmedBooking(
      branchId,
      today,
      LocalTime.of(9, 0),
      "BKG-20260420-OLD0001",
      "tebogo@example.co.za"
    ));

    BookingCommandService bookingCommandService = bookingCommandService(
      bookingRepository,
      branchId,
      today,
      LocalTime.of(11, 0),
      LocalTime.of(11, 30),
      "BKG-20260420-NEW0001"
    );

    Booking booking = bookingCommandService.createBooking(new CreateBookingCommand(
      branchId,
      toBranchUtc(today, LocalTime.of(11, 0)),
      toBranchUtc(today, LocalTime.of(11, 30)),
      "Tebogo Ndlovu",
      "tebogo@example.co.za",
      "en",
      "idem-new-slot"
    )).await().indefinitely();

    assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(booking.startDateTime()).isEqualTo(toBranchUtc(today, LocalTime.of(11, 0)));
    assertThat(booking.endDateTime()).isEqualTo(toBranchUtc(today, LocalTime.of(11, 30)));
    assertThat(BookingDateTimes.toBookingDate(booking.startDateTime(), BRANCH_ZONE)).isEqualTo(today);
    assertThat(BookingDateTimes.toBookingTime(booking.startDateTime(), BRANCH_ZONE)).isEqualTo(LocalTime.of(11, 0));
  }

  @Test
  void shouldRejectBookingWhenCustomerHasLaterSameDayAppointment() {
    UUID branchId = UUID.fromString("0d0fb1e2-3d44-4a66-9f4b-0d745e9f1a03");
    LocalDate today = LocalDate.of(2026, 4, 20);

    InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    bookingRepository.seed(confirmedBooking(
      branchId,
      today,
      LocalTime.of(11, 0),
      "BKG-20260420-FUTURE1",
      "tebogo@example.co.za"
    ));

    BookingCommandService bookingCommandService = bookingCommandService(
      bookingRepository,
      branchId,
      today,
      LocalTime.of(12, 0),
      LocalTime.of(12, 30),
      "BKG-20260420-NEW0002"
    );

    assertThatThrownBy(() -> bookingCommandService.createBooking(new CreateBookingCommand(
      branchId,
      toBranchUtc(today, LocalTime.of(12, 0)),
      toBranchUtc(today, LocalTime.of(12, 30)),
      "Tebogo Ndlovu",
      "tebogo@example.co.za",
      "en",
      "idem-blocked-slot"
    )).await().indefinitely())
      .isInstanceOf(CustomerHasActiveBookingException.class)
      .hasMessageContaining("You already have an upcoming appointment");
  }

  private static BookingCommandService bookingCommandService(
    InMemoryBookingRepository bookingRepository,
    UUID branchId,
    LocalDate appointmentDate,
    LocalTime bookingSlotStartTime,
    LocalTime bookingSlotEndTime,
    String bookingReference
  ) {
    return new BookingCommandService(
      bookingRepository,
      new InMemoryBranchCatalog(sandtonCityBranch(branchId)),
      new InMemoryBookingSlotInventoryRepository(branchId, appointmentDate, bookingSlotStartTime, bookingSlotEndTime),
      new BookingPolicy(45, FIXED_CLOCK),
      () -> bookingReference,
      new NoOpBookingNotificationService(),
      new TestCountriesWithBankBranches()
    );
  }

  private static Booking confirmedBooking(
    UUID branchId,
    LocalDate appointmentDate,
    LocalTime bookingSlotStartTime,
    String bookingReference,
    String customerEmail
  ) {
    return Booking.builder()
      .id(UUID.randomUUID())
      .bookingReference(bookingReference)
      .idempotencyKey("seed-" + bookingReference)
      .branchId(branchId)
      .startDateTime(toBranchUtc(appointmentDate, bookingSlotStartTime))
      .endDateTime(toBranchUtc(appointmentDate, bookingSlotStartTime.plusMinutes(30)))
      .customerName("Tebogo Ndlovu")
      .customerEmail(customerEmail)
      .preferredLanguage("en")
      .status(BookingStatus.CONFIRMED)
      .createdAt(java.time.OffsetDateTime.now())
      .build();
  }

  private static final class InMemoryBranchCatalog implements BranchCatalog {
    private final Branch branch;

    private InMemoryBranchCatalog(Branch branch) {
      this.branch = branch;
    }

    @Override
    public Uni<List<Branch>> search(String query, int limit) {
      return Uni.createFrom().item(List.of(branch));
    }

    @Override
    public Uni<Optional<Branch>> findById(UUID branchId) {
      return Uni.createFrom().item(branch.id().equals(branchId) ? Optional.of(branch) : Optional.empty());
    }
  }

  private static Branch sandtonCityBranch(UUID branchId) {
    return Branch.builder()
      .id(branchId)
      .code("JHB-SDT")
      .name("Capitec Sandton City")
      .city("Johannesburg")
      .province("Gauteng")
      .country("South Africa")
      .address("Sandton City Mall")
      .openingTime(LocalTime.of(9, 0))
      .closingTime(LocalTime.of(16, 0))
      .active(true)
      .build();
  }

  private static java.time.OffsetDateTime toBranchUtc(LocalDate appointmentDate, LocalTime bookingSlotTime) {
    return BookingDateTimes.toUtc(appointmentDate, bookingSlotTime, BRANCH_ZONE);
  }

  private static final class InMemoryBookingSlotInventoryRepository implements BookingSlotInventoryRepository {
    private final BookingSlotAvailability bookingSlotAvailability;
    private int reservationCount;

    private InMemoryBookingSlotInventoryRepository(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime, LocalTime bookingSlotEndTime) {
      this.bookingSlotAvailability = new BookingSlotAvailability(branchId, appointmentDate, bookingSlotStartTime, bookingSlotEndTime, 12, 0);
    }

    @Override
    public Uni<Void> ensureInventory(UUID branchId, LocalDate appointmentDate) {
      return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<List<BookingSlotAvailability>> findAvailability(UUID branchId, LocalDate appointmentDate) {
      return Uni.createFrom().item(List.of(bookingSlotAvailability));
    }

    @Override
    public Uni<Optional<BookingSlotAvailability>> findBookingSlot(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
      return Uni.createFrom().item(Optional.of(bookingSlotAvailability));
    }

    @Override
    public Uni<Boolean> reserveBookingSlot(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
      if (reservationCount >= bookingSlotAvailability.capacity()) {
        return Uni.createFrom().item(false);
      }
      reservationCount++;
      return Uni.createFrom().item(true);
    }

    @Override
    public Uni<Boolean> releaseBookingSlot(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
      if (reservationCount <= 0) {
        return Uni.createFrom().item(false);
      }
      reservationCount--;
      return Uni.createFrom().item(true);
    }
  }

  private static final class InMemoryBookingRepository implements BookingRepository {
    private final Map<String, Booking> byIdempotencyKey = new HashMap<>();
    private final Map<String, Booking> byReference = new HashMap<>();
    private final Map<UUID, Booking> savedBookings = new HashMap<>();

    @Override
    public Uni<Optional<Booking>> findByIdempotencyKey(String idempotencyKey) {
      return Uni.createFrom().item(Optional.ofNullable(byIdempotencyKey.get(idempotencyKey)));
    }

    @Override
    public Uni<Optional<Booking>> findByReference(String bookingReference) {
      return Uni.createFrom().item(Optional.ofNullable(byReference.get(bookingReference)));
    }

    @Override
    public Uni<Optional<Booking>> findUpcomingByCustomerEmail(String customerEmail, OffsetDateTime currentDateTime) {
      return Uni.createFrom().item(savedBookings.values().stream()
        .filter(booking -> booking.customerEmail() != null
          && booking.customerEmail().equalsIgnoreCase(customerEmail))
        .filter(booking -> booking.startDateTime().toInstant().isAfter(currentDateTime.toInstant()))
        .filter(booking -> booking.status() == za.co.capitec.booking.domain.model.BookingStatus.CONFIRMED)
        .sorted(Comparator.comparing(Booking::startDateTime))
        .findFirst());
    }

    private void seed(Booking booking) {
      save(booking).await().indefinitely();
    }

    @Override
    public Uni<Booking> save(Booking booking) {
      byIdempotencyKey.put(booking.idempotencyKey(), booking);
      byReference.put(booking.bookingReference(), booking);
      savedBookings.put(booking.id(), booking);
      return Uni.createFrom().item(booking);
    }

    @Override
    public Uni<List<Booking>> findForAdmin(
      Collection<UUID> branchIds,
      OffsetDateTime startDateTime,
      OffsetDateTime endDateTime
    ) {
      return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<Booking>> findByCustomerEmail(String customerEmail) {
      return Uni.createFrom().item(savedBookings.values().stream()
        .filter(booking -> booking.customerEmail() != null
          && booking.customerEmail().equalsIgnoreCase(customerEmail))
        .toList());
    }

    @Override
    public Uni<Booking> update(Booking booking) {
      byReference.put(booking.bookingReference(), booking);
      byIdempotencyKey.put(booking.idempotencyKey(), booking);
      savedBookings.put(booking.id(), booking);
      return Uni.createFrom().item(booking);
    }
  }

  private static final class NoOpBookingNotificationService extends BookingNotificationService {
    private NoOpBookingNotificationService() {
      super(null, new EmailDeliveryGuardrails(), new TestCountriesWithBankBranches());
    }

    @Override
    public void sendConfirmationEmail(Booking booking) {
    }

    @Override
    public void sendConfirmationEmail(Booking booking, Branch branch) {
    }

    @Override
    public void sendDayOfReminderEmail(Booking booking) {
    }

    @Override
    public void sendDayOfReminderEmail(Booking booking, Branch branch) {
    }
  }

  private static final class TestCountriesWithBankBranches implements CountriesWithBankBranches {
    @Override
    public Map<String, Market> markets() {
      return Map.of("South Africa", new Market() {
        @Override
        public String timezone() {
          return BRANCH_ZONE.getId();
        }

        @Override
        public List<String> provinces() {
          return List.of("Gauteng");
        }
      });
    }
  }
}
