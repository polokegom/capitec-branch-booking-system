package za.co.capitec.booking.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import za.co.capitec.booking.domain.model.BookingStatus;

@Entity
@Table(name = "booking")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEntity {
  @Id
  @Column(nullable = false)
  public UUID id;

  @Column(name = "booking_reference", nullable = false, length = 32)
  public String bookingReference;

  @Column(name = "idempotency_key", nullable = false, length = 80)
  public String idempotencyKey;

  @Column(name = "branch_id", nullable = false)
  public UUID branchId;

  @Column(name = "start_datetime", nullable = false, columnDefinition = "timestamp")
  public LocalDateTime startDateTime;

  @Column(name = "end_datetime", nullable = false, columnDefinition = "timestamp")
  public LocalDateTime endDateTime;

  @Column(name = "customer_name", nullable = false, length = 160)
  public String customerName;

  @Column(name = "customer_email", nullable = false, length = 254)
  public String customerEmail;

  @Column(name = "preferred_language", nullable = false, length = 16)
  public String preferredLanguage;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  public BookingStatus status;

  @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
  public OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
  public OffsetDateTime updatedAt;
}
