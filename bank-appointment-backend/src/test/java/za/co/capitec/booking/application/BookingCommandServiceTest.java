package za.co.capitec.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.mutiny.Uni;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import za.co.capitec.booking.application.command.CreateBookingCommand;
import za.co.capitec.booking.application.port.BookingRepository;
import za.co.capitec.booking.application.port.BranchRepository;
import za.co.capitec.booking.application.service.BookingCommandService;
import za.co.capitec.booking.application.service.BookingNotificationService;
import za.co.capitec.booking.domain.model.Booking;
import za.co.capitec.booking.domain.model.BookingStatus;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.Pagination;
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
    InMemoryBranchRepository branchRepository = new InMemoryBranchRepository(sandtonCityBranch(branchId));

    BookingReferenceGenerator referenceGenerator = new BookingReferenceGenerator() {
      @Override
      public String nextReference() {
        return "BKG-20260420-TEST0001";
      }
    };

    BookingCommandService bookingCommandService = new BookingCommandService(
      bookingRepository,
      branchRepository,
      new BookingPolicy(45, FIXED_CLOCK),
      referenceGenerator,
      new NoOpBookingNotificationService()
    );

    CreateBookingCommand command = new CreateBookingCommand(
      branchId,
      toDateTime(appointmentDate, bookingSlotStartTime),
      toDateTime(appointmentDate, bookingSlotStartTime.plusMinutes(Branch.SLOT_MINUTES)),
      "Tebogo Ndlovu",
      "tebogo@example.co.za",
      "en",
      "idem-123"
    );

    Booking firstBooking = bookingCommandService.createBooking(command).await().indefinitely();
    Booking repeatedBooking = bookingCommandService.createBooking(command).await().indefinitely();

    assertThat(repeatedBooking).isEqualTo(firstBooking);
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
      "BKG-20260420-NEW0001"
    );

    Booking booking = bookingCommandService.createBooking(new CreateBookingCommand(
      branchId,
      toDateTime(today, LocalTime.of(11, 0)),
      toDateTime(today, LocalTime.of(11, 0).plusMinutes(Branch.SLOT_MINUTES)),
      "Tebogo Ndlovu",
      "tebogo@example.co.za",
      "en",
      "idem-new-slot"
    )).await().indefinitely();

    assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(booking.startDateTime()).isEqualTo(toDateTime(today, LocalTime.of(11, 0)));
    assertThat(booking.endDateTime()).isEqualTo(toDateTime(today, LocalTime.of(11, 0).plusMinutes(Branch.SLOT_MINUTES)));
    assertThat(booking.startDateTime().toLocalDate()).isEqualTo(today);
    assertThat(booking.startDateTime().toLocalTime()).isEqualTo(LocalTime.of(11, 0));
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
      "BKG-20260420-NEW0002"
    );

    assertThatThrownBy(() -> bookingCommandService.createBooking(new CreateBookingCommand(
      branchId,
      toDateTime(today, LocalTime.of(12, 0)),
      toDateTime(today, LocalTime.of(12, 0).plusMinutes(Branch.SLOT_MINUTES)),
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
    String bookingReference
  ) {
    return new BookingCommandService(
      bookingRepository,
      new InMemoryBranchRepository(sandtonCityBranch(branchId)),
      new BookingPolicy(45, FIXED_CLOCK),
      () -> bookingReference,
      new NoOpBookingNotificationService()
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
      .startDateTime(toDateTime(appointmentDate, bookingSlotStartTime))
      .endDateTime(toDateTime(appointmentDate, bookingSlotStartTime.plusMinutes(Branch.SLOT_MINUTES)))
      .customerName("Tebogo Ndlovu")
      .customerEmail(customerEmail)
      .preferredLanguage("en")
      .status(BookingStatus.CONFIRMED)
      .createdAt(OffsetDateTime.now())
      .build();
  }

  private static final class InMemoryBranchRepository implements BranchRepository {
    private final Branch branch;

    private InMemoryBranchRepository(Branch branch) {
      this.branch = branch;
    }

    @Override
    public Uni<List<Branch>> search(String query, int limit) {
      return Uni.createFrom().item(List.of(branch));
    }

    @Override
    public Uni<Pagination<Branch>> searchUsingPagination(String query, int startIndex, int endIndex) {
      return Uni.createFrom().item(new Pagination<>(List.of(branch), 1, startIndex, endIndex));
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

  private static LocalDateTime toDateTime(LocalDate appointmentDate, LocalTime bookingSlotTime) {
    return LocalDateTime.of(appointmentDate, bookingSlotTime);
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
    public Uni<Optional<Booking>> findUpcomingByCustomerEmail(String customerEmail, LocalDateTime currentDateTime) {
      return Uni.createFrom().item(savedBookings.values().stream()
        .filter(booking -> booking.customerEmail() != null
          && booking.customerEmail().equalsIgnoreCase(customerEmail))
        .filter(booking -> booking.startDateTime().isAfter(currentDateTime))
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
      LocalDateTime startDateTime,
      LocalDateTime endDateTime
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

    @Override
    public Uni<List<LocalDateTime>> findConfirmedStartDateTimes(
      UUID branchId,
      LocalDateTime startInclusive,
      LocalDateTime endExclusive
    ) {
      return Uni.createFrom().item(savedBookings.values().stream()
        .filter(booking -> booking.branchId().equals(branchId))
        .filter(booking -> booking.status() == BookingStatus.CONFIRMED)
        .filter(booking -> !booking.startDateTime().isBefore(startInclusive))
        .filter(booking -> booking.startDateTime().isBefore(endExclusive))
        .map(Booking::startDateTime)
        .toList());
    }

    @Override
    public Uni<Boolean> existsConfirmedBookingAt(UUID branchId, LocalDateTime startDateTime) {
      return Uni.createFrom().item(savedBookings.values().stream()
        .filter(booking -> booking.branchId().equals(branchId))
        .filter(booking -> booking.status() == BookingStatus.CONFIRMED)
        .anyMatch(booking -> booking.startDateTime().equals(startDateTime)));
    }
  }

  private static final class NoOpBookingNotificationService extends BookingNotificationService {
    private NoOpBookingNotificationService() {
      super(null, new EmailDeliveryGuardrails());
    }


    @Override
    public void sendConfirmationEmail(Booking booking, Branch branch) {
    }


  }
}
