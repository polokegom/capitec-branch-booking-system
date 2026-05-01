package za.co.capitec.booking.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateBookingRequest(
  @NotNull UUID branchId,
  @NotNull OffsetDateTime startDateTime,
  @NotNull OffsetDateTime endDateTime,
  @NotBlank @Size(max = 160) String customerName,
  @NotBlank @Email @Size(max = 254) String customerEmail,
  @NotBlank @Size(max = 16) @Pattern(regexp = "en|af|tn|nso|zu|xh") String preferredLanguage
) {}
