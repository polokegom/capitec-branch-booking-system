package za.co.capitec.booking.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
class TestSecurityProducer {
  private static final String EMAIL = "postgres-it@example.co.za";
  private static final Map<String, Object> CLAIMS = Map.of(
    "email", EMAIL,
    "sub", "postgres-it-user"
  );

  @Produces
  JsonWebToken jsonWebToken() {
    return (JsonWebToken) Proxy.newProxyInstance(
      JsonWebToken.class.getClassLoader(),
      new Class<?>[] {JsonWebToken.class},
      (proxy, method, arguments) -> switch (method.getName()) {
        case "getName" -> EMAIL;
        case "getClaim" -> CLAIMS.get(arguments[0]);
        case "containsClaim" -> CLAIMS.containsKey(arguments[0]);
        case "getClaimNames" -> Set.copyOf(CLAIMS.keySet());
        case "toString" -> EMAIL;
        default -> defaultValue(method.getReturnType());
      }
    );
  }

  private static Object defaultValue(Class<?> returnType) {
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class || returnType == long.class || returnType == short.class || returnType == byte.class) {
      return 0;
    }
    return null;
  }
}
