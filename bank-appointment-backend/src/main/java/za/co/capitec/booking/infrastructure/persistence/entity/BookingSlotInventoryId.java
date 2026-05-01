package za.co.capitec.booking.infrastructure.persistence.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public class BookingSlotInventoryId implements Serializable {
  public UUID branchId;
  public LocalDate appointmentDate;
  public LocalTime bookingSlotStartTime;

  public BookingSlotInventoryId() {
  }

  public BookingSlotInventoryId(UUID branchId, LocalDate appointmentDate, LocalTime bookingSlotStartTime) {
    this.branchId = branchId;
    this.appointmentDate = appointmentDate;
    this.bookingSlotStartTime = bookingSlotStartTime;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof BookingSlotInventoryId other)) {
      return false;
    }
    return Objects.equals(branchId, other.branchId)
      && Objects.equals(appointmentDate, other.appointmentDate)
      && Objects.equals(bookingSlotStartTime, other.bookingSlotStartTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(branchId, appointmentDate, bookingSlotStartTime);
  }
}
