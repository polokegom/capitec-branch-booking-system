package za.co.capitec.booking.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "branch")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchEntity {
  @Id
  @Column(nullable = false)
  public UUID id;

  @Column(nullable = false, length = 40)
  public String code;

  @Column(nullable = false, length = 60)
  public String name;

  @Column(nullable = false, length = 60)
  public String city;

  @Column(length = 60)
  public String province;

  @Column(nullable = false, length = 40)
  public String country;

  @Column(length = 120)
  public String address;

  @Column(name = "opening_time", nullable = false)
  public LocalTime openingTime;

  @Column(name = "closing_time", nullable = false)
  public LocalTime closingTime;

  @Column(nullable = false)
  public boolean active;

  @Column(name = "admin_email", length = 60)
  public String adminEmail;
}
