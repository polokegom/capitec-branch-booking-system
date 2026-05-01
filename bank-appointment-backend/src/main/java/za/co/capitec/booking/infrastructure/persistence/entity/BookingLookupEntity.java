package za.co.capitec.booking.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_lookup")
public class BookingLookupEntity {
  @Id
  @Column(name = "booking_reference", nullable = false, length = 32)
  public String bookingReference;

  @Column(name = "booking_id", nullable = false)
  public UUID bookingId;

  @Column(name = "idempotency_key", nullable = false, length = 80)
  public String idempotencyKey;

  @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
  public OffsetDateTime createdAt;
}
