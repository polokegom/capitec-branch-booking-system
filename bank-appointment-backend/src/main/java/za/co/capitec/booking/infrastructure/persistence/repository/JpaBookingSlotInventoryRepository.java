package za.co.capitec.booking.infrastructure.persistence.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.reactive.mutiny.Mutiny;
import za.co.capitec.booking.application.port.BookingSlotInventoryRepository;
import za.co.capitec.booking.domain.model.BookingSlotAvailability;
import za.co.capitec.booking.infrastructure.persistence.entity.BookingSlotInventoryEntity;
import za.co.capitec.booking.infrastructure.persistence.entity.BookingSlotInventoryId;
import za.co.capitec.booking.infrastructure.persistence.mapper.PersistenceMapper;

@ApplicationScoped
@RequiredArgsConstructor
public class JpaBookingSlotInventoryRepository implements BookingSlotInventoryRepository {
  private final Mutiny.SessionFactory sessionFactory;
  private final PersistenceMapper persistenceMapper;

  @Override
  public Uni<Void> ensureInventory(UUID branchId, LocalDate appointmentDate) {
    return sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery(
        """
        insert into booking.booking_slot_inventory (
          branch_id,
          appointment_date,
          booking_slot_start_time,
          booking_slot_end_time,
          capacity,
          reserved_count,
          version
        )
        select
          template.branch_id,
          cast(:appointmentDate as date),
          template.booking_slot_start_time,
          template.booking_slot_end_time,
          template.capacity,
          0,
          0
        from booking.branch_slot_template template
        where template.branch_id = :branchId
          and template.active = true
        on conflict (branch_id, appointment_date, booking_slot_start_time) do nothing
        """
      )
      .setParameter("branchId", branchId)
      .setParameter("appointmentDate", appointmentDate)
      .executeUpdate()
      .replaceWithVoid());
  }

  @Override
  public Uni<List<BookingSlotAvailability>> findAvailability(UUID branchId, LocalDate appointmentDate) {
    return sessionFactory.withSession(session -> session.createQuery(
        """
        from BookingSlotInventoryEntity bookingSlot
        where bookingSlot.branchId = :branchId
          and bookingSlot.appointmentDate = :appointmentDate
          and bookingSlot.capacity > bookingSlot.reservedCount
        order by bookingSlot.bookingSlotStartTime
        """,
        BookingSlotInventoryEntity.class
      )
      .setParameter("branchId", branchId)
      .setParameter("appointmentDate", appointmentDate)
      .getResultList()
      .map(persistenceMapper::toBookingSlotAvailabilities));
  }

  @Override
  public Uni<Optional<BookingSlotAvailability>> findBookingSlot(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
    return sessionFactory.withSession(session -> session.find(
        BookingSlotInventoryEntity.class,
        new BookingSlotInventoryId(branchId, appointmentDate, bookingSlotStartTime)
      ))
      .map(entity -> Optional.ofNullable(entity).map(persistenceMapper::toDomain));
  }

  @Override
  public Uni<Boolean> reserveBookingSlot(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
    return sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery(
        """
        update booking.booking_slot_inventory
        set reserved_count = reserved_count + 1,
            version = version + 1,
            updated_at = now()
        where branch_id = :branchId
          and appointment_date = cast(:appointmentDate as date)
          and booking_slot_start_time = cast(:bookingSlotStartTime as time)
          and reserved_count < capacity
        """
      )
      .setParameter("branchId", branchId)
      .setParameter("appointmentDate", appointmentDate)
      .setParameter("bookingSlotStartTime", bookingSlotStartTime)
      .executeUpdate()
      .map(updatedRows -> updatedRows == 1));
  }

  @Override
  public Uni<Boolean> releaseBookingSlot(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
    return sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery(
        """
        update booking.booking_slot_inventory
        set reserved_count = greatest(reserved_count - 1, 0),
            version = version + 1,
            updated_at = now()
        where branch_id = :branchId
          and appointment_date = cast(:appointmentDate as date)
          and booking_slot_start_time = cast(:bookingSlotStartTime as time)
          and reserved_count > 0
        """
      )
      .setParameter("branchId", branchId)
      .setParameter("appointmentDate", appointmentDate)
      .setParameter("bookingSlotStartTime", bookingSlotStartTime)
      .executeUpdate()
      .map(updatedRows -> updatedRows == 1));
  }
}
