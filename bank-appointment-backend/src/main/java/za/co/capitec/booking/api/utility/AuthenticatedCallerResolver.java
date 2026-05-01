package za.co.capitec.booking.api.utility;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.jwt.JsonWebToken;
import za.co.capitec.booking.application.security.FusionAuthAdminClient;

@ApplicationScoped
@RequiredArgsConstructor
public class AuthenticatedCallerResolver {
  private final FusionAuthAdminClient fusionAuthAdminClient;

  public Uni<String> resolveEmail(JsonWebToken jwt) {
    if (jwt == null) {
      return Uni.createFrom().nullItem();
    }

    String emailClaim = emailClaim(jwt);
    if (emailClaim != null) {
      return Uni.createFrom().item(emailClaim);
    }

    String subject = claim(jwt, "sub");
    if (subject == null || subject.isBlank()) {
      return Uni.createFrom().nullItem();
    }

    return ReactiveResourceSupport.fromWorker(
      () -> fusionAuthAdminClient.findEmailByUserId(subject).orElse(null)
    );
  }

  private String emailClaim(JsonWebToken jwt) {
    String email = claim(jwt, "email");
    if (isEmail(email)) {
      return email;
    }

    String userPrincipalName = claim(jwt, "upn");
    if (isEmail(userPrincipalName)) {
      return userPrincipalName;
    }

    String preferredUsername = claim(jwt, "preferred_username");
    if (isEmail(preferredUsername)) {
      return preferredUsername;
    }

    String tokenName = jwt.getName();
    return isEmail(tokenName) ? tokenName : null;
  }

  private String claim(JsonWebToken jwt, String claimName) {
    Object claimValue = jwt.getClaim(claimName);
    return claimValue == null ? null : claimValue.toString();
  }

  private boolean isEmail(String value) {
    return value != null && value.contains("@");
  }
}
