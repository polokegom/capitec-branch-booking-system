package za.co.capitec.booking.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "booking_slot_inventory")
@IdClass(BookingSlotInventoryId.class)
public class BookingSlotInventoryEntity {
  @Id
  @Column(name = "branch_id", nullable = false)
  public UUID branchId;

  @Id
  @Column(name = "appointment_date", nullable = false)
  public LocalDate appointmentDate;

  @Id
  @Column(name = "booking_slot_start_time", nullable = false)
  public LocalTime bookingSlotStartTime;

  @Column(name = "booking_slot_end_time", nullable = false)
  public LocalTime bookingSlotEndTime;

  @Column(nullable = false)
  public int capacity;

  @Column(name = "reserved_count", nullable = false)
  public int reservedCount;
}
