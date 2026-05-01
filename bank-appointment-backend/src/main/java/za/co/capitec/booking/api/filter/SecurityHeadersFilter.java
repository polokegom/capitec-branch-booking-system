package za.co.capitec.booking.api.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {
  private static final String CONTENT_SECURITY_POLICY = String.join("; ",
    "default-src 'none'",
    "frame-ancestors 'none'",
    "base-uri 'none'",
    "form-action 'none'"
  );

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    responseContext.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
    responseContext.getHeaders().putSingle("X-Frame-Options", "DENY");
    responseContext.getHeaders().putSingle("Referrer-Policy", "strict-origin-when-cross-origin");
    responseContext.getHeaders().putSingle("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    responseContext.getHeaders().putSingle("Content-Security-Policy", CONTENT_SECURITY_POLICY);
    responseContext.getHeaders().putSingle("Cross-Origin-Opener-Policy", "same-origin");
    responseContext.getHeaders().putSingle("Cross-Origin-Resource-Policy", "same-origin");
    responseContext.getHeaders().putSingle("X-Permitted-Cross-Domain-Policies", "none");
    responseContext.getHeaders().putSingle("Cache-Control", "no-store");
    responseContext.getHeaders().putSingle("Pragma", "no-cache");
  }
}
