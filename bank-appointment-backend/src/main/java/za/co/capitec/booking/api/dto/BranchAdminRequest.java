package za.co.capitec.booking.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record BranchAdminRequest(
  @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9-]+$") String code,
  @NotBlank @Size(max = 60) String name,
  @NotBlank @Size(max = 60) String city,
  @Size(max = 60) String province,
  @Size(max = 120) String address,
  @NotBlank @Size(max = 40) String country,
  @NotNull LocalTime openingTime,
  @NotNull LocalTime closingTime,
  @Email @Size(max = 60) String adminEmail
) {
}
