package za.co.capitec.booking.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEmailVerificationRequest(@NotBlank @Email @Size(max = 254) String email) {}
