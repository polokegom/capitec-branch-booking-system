package za.co.capitec.booking.application.service;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.reactive.mutiny.Mutiny;
import za.co.capitec.booking.application.command.SaveBranchCommand;
import za.co.capitec.booking.application.configuration.CountriesWithBankBranches;
import za.co.capitec.booking.application.security.FusionAuthAdminClient;
import za.co.capitec.booking.application.utility.TextSanitizer;
import za.co.capitec.booking.domain.exception.BranchNotFoundException;
import za.co.capitec.booking.domain.exception.InvalidBookingRequestException;
import za.co.capitec.booking.domain.model.Branch;
import za.co.capitec.booking.domain.model.Pagination;
import za.co.capitec.booking.domain.model.PaginationWindow;
import za.co.capitec.booking.infrastructure.persistence.entity.BranchEntity;
import za.co.capitec.booking.infrastructure.persistence.mapper.PersistenceMapper;

@ApplicationScoped
@RequiredArgsConstructor
public class BranchAdminService {

  private static final int SLOT_MINUTES = 30;
  private static final int DEFAULT_CAPACITY = 1;
  private static final String ALL_BRANCHES_QUERY = "from BranchEntity branch order by branch.active desc, branch.name asc";
  private static final String ALL_BRANCH_COUNT_QUERY = "select count(branch) from BranchEntity branch";
  private static final String ASSIGNED_BRANCHES_QUERY =
    "from BranchEntity branch where lower(branch.adminEmail) = lower(:email) order by branch.active desc, branch.name asc";
  private static final String ASSIGNED_BRANCH_COUNT_QUERY =
    "select count(branch) from BranchEntity branch where lower(branch.adminEmail) = lower(:email)";

  private final Mutiny.SessionFactory sessionFactory;
  private final FusionAuthAdminClient fusionAuthAdminClient;
  private final CountriesWithBankBranches countriesWithBankBranches;
  private final PersistenceMapper persistenceMapper;
  @Inject
  BookingNotificationService bookingNotificationService;

  public Uni<List<Branch>> listAll() {
    return sessionFactory.withSession(session -> session.createQuery(ALL_BRANCHES_QUERY, BranchEntity.class)
      .getResultList()
      .map(persistenceMapper::toBranches));
  }

  public Uni<List<Branch>> listAssignedTo(String adminEmail) {
    String assignedAdminEmail = sanitizeAdminEmail(adminEmail);
    if (assignedAdminEmail == null) {
      return Uni.createFrom().item(List.of());
    }
    return sessionFactory.withSession(session -> session.createQuery(ASSIGNED_BRANCHES_QUERY, BranchEntity.class)
      .setParameter("email", assignedAdminEmail)
      .getResultList()
      .map(persistenceMapper::toBranches));
  }

  public Uni<Pagination<Branch>> listAllUsingPagination(int startIndex, int endIndex) {
    PaginationWindow paginationWindow = PaginationWindow.from(startIndex, endIndex);

    return sessionFactory.withSession(session -> session.createQuery(ALL_BRANCH_COUNT_QUERY, Long.class)
      .getSingleResult()
      .chain(total -> {
        long safeTotal = total == null ? 0L : total;
        if (safeTotal == 0L || paginationWindow.isEmpty()) {
          return Uni.createFrom().item(paginationWindow.empty(safeTotal));
        }
        return session.createQuery(ALL_BRANCHES_QUERY, BranchEntity.class)
          .setFirstResult(paginationWindow.startIndex())
          .setMaxResults(paginationWindow.requestedItemCount())
          .getResultList()
          .map(persistenceMapper::toBranches)
          .map(items -> paginationWindow.toPagination(items, safeTotal));
      }));
  }

  public Uni<Pagination<Branch>> listAssignedToUsingPagination(String adminEmail, int startIndex, int endIndex) {
    PaginationWindow paginationWindow = PaginationWindow.from(startIndex, endIndex);
    String assignedAdminEmail = sanitizeAdminEmail(adminEmail);
    if (assignedAdminEmail == null || paginationWindow.isEmpty()) {
      return Uni.createFrom().item(paginationWindow.empty(0L));
    }

    return sessionFactory.withSession(session -> session.createQuery(ASSIGNED_BRANCH_COUNT_QUERY, Long.class)
      .setParameter("email", assignedAdminEmail)
      .getSingleResult()
      .chain(total -> {
        long safeTotal = total == null ? 0L : total;
        if (safeTotal == 0L) {
          return Uni.createFrom().item(paginationWindow.empty(0L));
        }
        return session.createQuery(ASSIGNED_BRANCHES_QUERY, BranchEntity.class)
          .setParameter("email", assignedAdminEmail)
          .setFirstResult(paginationWindow.startIndex())
          .setMaxResults(paginationWindow.requestedItemCount())
          .getResultList()
          .map(persistenceMapper::toBranches)
          .map(items -> paginationWindow.toPagination(items, safeTotal));
      }));
  }

  public Uni<Branch> create(SaveBranchCommand command) {
    validateCommand(command);

    String assignedAdminEmail = sanitizeAdminEmail(command.adminEmail());

    return ensureAdminEmailIsRegistered(assignedAdminEmail)
      .chain(() -> sessionFactory.withTransaction((session, transaction) -> findByCode(session, command.code())
        .chain(duplicate -> {
          if (duplicate != null) {
            return Uni.createFrom().failure(duplicateBranchCode(command.code()));
          }

          BranchEntity entity = newBranchEntity(command, assignedAdminEmail);

          return session.persist(entity)
            .chain(() -> regenerateTemplates(session, entity.id, command.openingTime(), command.closingTime()))
            .replaceWith(persistenceMapper.toDomain(entity));
        })))
      .invoke(branch -> {
        if (assignedAdminEmail != null) {
          bookingNotificationService.sendAdminRoleAssignedEmail(
            assignedAdminEmail,
            command.name(),
            branchLabel(command)
          );
        }
      });
  }

  public Uni<Branch> update(UUID branchId, SaveBranchCommand command) {
    validateCommand(command);

    String assignedAdminEmail = sanitizeAdminEmail(command.adminEmail());

    return sessionFactory.withTransaction((session, transaction) -> findRequiredBranch(session, branchId)
      .chain(entity -> {
        return findByCode(session, command.code())
          .chain(duplicate -> updateExistingBranch(
            session,
            entity,
            duplicate,
            branchId,
            command,
            assignedAdminEmail
          ));
      }))
      .invoke(result -> sendAdminAssignmentNotifications(
        result.previousAdminEmail(),
        result.assignedAdminEmail(),
        result.branch().name(),
        result.branch().city()
      ))
      .map(BranchUpdateResult::branch);
  }

  public Uni<Void> deactivate(UUID branchId) {
    return sessionFactory.withTransaction((session, transaction) -> findRequiredBranch(session, branchId)
      .chain(entity -> {
        String previousAdminEmail = entity.adminEmail;
        entity.active = false;
        entity.adminEmail = null;
        String branchName = entity.name;
        String branchDisplayLabel = branchLabel(branchName, entity.city);
        return session.flush()
          .replaceWith(new BranchDeactivationResult(previousAdminEmail, branchName, branchDisplayLabel));
      }))
      .invoke(result -> {
        if (result.previousAdminEmail() != null) {
          bookingNotificationService.sendAdminRoleRemovedEmail(
            result.previousAdminEmail(),
            result.branchName(),
            result.branchDisplayLabel()
          );
        }
      })
      .replaceWithVoid();
  }

  public Uni<Branch> reactivate(UUID branchId) {
    return sessionFactory.withTransaction((session, transaction) -> findRequiredBranch(session, branchId)
      .chain(entity -> {
        entity.active = true;
        return regenerateTemplates(session, entity.id, entity.openingTime, entity.closingTime)
          .replaceWith(persistenceMapper.toDomain(entity));
      }));
  }

  private Uni<BranchUpdateResult> updateExistingBranch(
    Mutiny.Session session,
    BranchEntity entity,
    BranchEntity duplicate,
    UUID branchId,
    SaveBranchCommand command,
    String assignedAdminEmail
  ) {
    if (duplicate != null && !duplicate.id.equals(branchId)) {
      return Uni.createFrom().failure(duplicateBranchCode(command.code()));
    }

    String previousAdminEmail = entity.adminEmail;
    boolean hoursChanged = !command.openingTime().equals(entity.openingTime) || !command.closingTime().equals(entity.closingTime);

    return ensureAdminEmailIsRegisteredWhenChanged(assignedAdminEmail, previousAdminEmail)
      .chain(() -> {
        updateBranchEntity(entity, command, assignedAdminEmail);

        return hoursChanged
          ? regenerateTemplates(session, branchId, command.openingTime(), command.closingTime())
          : Uni.createFrom().voidItem();
      })
      .call(session::flush)
      .replaceWith(new BranchUpdateResult(
        persistenceMapper.toDomain(entity),
        previousAdminEmail,
        assignedAdminEmail
      ));
  }

  private record BranchUpdateResult(Branch branch, String previousAdminEmail, String assignedAdminEmail) {}

  private record BranchDeactivationResult(String previousAdminEmail, String branchName, String branchDisplayLabel) {}

  private BranchEntity newBranchEntity(SaveBranchCommand command, String assignedAdminEmail) {
    return BranchEntity.builder()
      .id(UUID.randomUUID())
      .code(command.code().trim())
      .name(command.name().trim())
      .city(command.city().trim())
      .province(trimToNull(command.province()))
      .address(trimToNull(command.address()))
      .country(command.country().trim())
      .openingTime(command.openingTime())
      .closingTime(command.closingTime())
      .active(true)
      .adminEmail(assignedAdminEmail)
      .build();
  }

  private void updateBranchEntity(BranchEntity entity, SaveBranchCommand command, String assignedAdminEmail) {
    entity.code = command.code().trim();
    entity.name = command.name().trim();
    entity.city = command.city().trim();
    entity.province = trimToNull(command.province());
    entity.address = trimToNull(command.address());
    entity.country = command.country().trim();
    entity.openingTime = command.openingTime();
    entity.closingTime = command.closingTime();
    entity.active = true;
    entity.adminEmail = assignedAdminEmail;
  }

  private Uni<BranchEntity> findByCode(Mutiny.Session session, String code) {
    return session.createQuery(
        "from BranchEntity branch where lower(branch.code) = lower(:code)",
        BranchEntity.class
      )
      .setParameter("code", code.trim())
      .setMaxResults(1)
      .getResultList()
      .map(entities -> entities.isEmpty() ? null : entities.get(0));
  }

  private Uni<BranchEntity> findRequiredBranch(Mutiny.Session session, UUID branchId) {
    return session.find(BranchEntity.class, branchId)
      .onItem().ifNull().failWith(() -> new BranchNotFoundException(branchId));
  }

  private void validateCommand(SaveBranchCommand command) {
    if (command == null) {
      throw new InvalidBookingRequestException("Branch payload is required.");
    }
    validateCountry(command.country());
    validateHours(command.openingTime(), command.closingTime());
  }

  private void validateCountry(String country) {
    if (!countriesWithBankBranches.isSupported(country)) {
      throw new InvalidBookingRequestException(
        "Country '" + country + "' is not supported. Supported countries: " +
          String.join(", ", countriesWithBankBranches.names()) + "."
      );
    }
  }

  private void validateHours(LocalTime openingTime, LocalTime closingTime) {
    if (openingTime == null || closingTime == null) {
      throw new InvalidBookingRequestException("Operating hours are required.");
    }
    if (!openingTime.isBefore(closingTime)) {
      throw new InvalidBookingRequestException("Opening time must be before closing time.");
    }
    if (openingTime.getMinute() % SLOT_MINUTES != 0 || closingTime.getMinute() % SLOT_MINUTES != 0) {
      throw new InvalidBookingRequestException("Operating hours must align to " + SLOT_MINUTES + "-minute boundaries.");
    }
  }

  private String sanitizeAdminEmail(String adminEmail) {
    return TextSanitizer.trimToNull(adminEmail);
  }

  private String trimToNull(String value) {
    return TextSanitizer.trimToNull(value);
  }

  private InvalidBookingRequestException duplicateBranchCode(String code) {
    return new InvalidBookingRequestException("A branch with code '" + code + "' already exists.");
  }

  private String branchLabel(SaveBranchCommand command) {
    return branchLabel(command.name(), command.city());
  }

  private String branchLabel(String branchName, String branchCity) {
    return branchName + ", " + branchCity;
  }

  private Uni<Void> ensureAdminEmailIsRegistered(String email) {
    if (email == null) {
      return Uni.createFrom().voidItem();
    }
    return runBlocking(() -> {
      Optional<FusionAuthAdminClient.UserRoles> user = fusionAuthAdminClient.findUserRolesByEmail(email);
      if (user.isEmpty()) {
        throw new InvalidBookingRequestException(
          "No registered customer was found with email '" + email + "'. The user must sign up first."
        );
      }
      return null;
    }).replaceWithVoid();
  }

  private Uni<Void> ensureAdminEmailIsRegisteredWhenChanged(String newAdminEmail, String previousAdminEmail) {
    if (newAdminEmail == null || newAdminEmail.equalsIgnoreCase(previousAdminEmail)) {
      return Uni.createFrom().voidItem();
    }
    return ensureAdminEmailIsRegistered(newAdminEmail);
  }

  private void sendAdminAssignmentNotifications(String previousAdminEmail, String assignedAdminEmail, String branchName, String branchCity) {
    String branchDisplayLabel = branchLabel(branchName, branchCity);
    if (assignedAdminEmail != null && !assignedAdminEmail.equalsIgnoreCase(previousAdminEmail)) {
      bookingNotificationService.sendAdminRoleAssignedEmail(
        assignedAdminEmail,
        branchName,
        branchDisplayLabel
      );
    }
    if (previousAdminEmail != null && !previousAdminEmail.equalsIgnoreCase(assignedAdminEmail)) {
      bookingNotificationService.sendAdminRoleRemovedEmail(
        previousAdminEmail,
        branchName,
        branchDisplayLabel
      );
    }
  }

  private <T> Uni<T> runBlocking(java.util.function.Supplier<T> supplier) {
    return Uni.createFrom()
      .item(supplier)
      .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
  }

  private Uni<Void> regenerateTemplates(Mutiny.Session session, UUID branchId, LocalTime openingTime, LocalTime closingTime) {
    return session.createNativeQuery(
        "delete from booking.branch_slot_template where branch_id = :branchId " +
          "and (booking_slot_start_time < :openingTime or booking_slot_end_time > :closingTime)"
      )
      .setParameter("branchId", branchId)
      .setParameter("openingTime", openingTime)
      .setParameter("closingTime", closingTime)
      .executeUpdate()
      .chain(() -> insertTemplates(session, branchId, openingTime, closingTime));
  }

  private Uni<Void> insertTemplates(Mutiny.Session session, UUID branchId, LocalTime cursor, LocalTime closingTime) {
    if (cursor.plusMinutes(SLOT_MINUTES).compareTo(closingTime) > 0) {
      return Uni.createFrom().voidItem();
    }
    LocalTime end = cursor.plusMinutes(SLOT_MINUTES);
    return session.createNativeQuery(
        "insert into booking.branch_slot_template (branch_id, booking_slot_start_time, booking_slot_end_time, capacity) " +
          "values (:branchId, :start, :end, :capacity) " +
          "on conflict (branch_id, booking_slot_start_time) do nothing"
      )
      .setParameter("branchId", branchId)
      .setParameter("start", cursor)
      .setParameter("end", end)
      .setParameter("capacity", DEFAULT_CAPACITY)
      .executeUpdate()
      .chain(() -> insertTemplates(session, branchId, end, closingTime));
  }
}
