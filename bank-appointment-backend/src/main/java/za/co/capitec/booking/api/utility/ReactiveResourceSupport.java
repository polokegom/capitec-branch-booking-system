package za.co.capitec.booking.api.utility;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.util.function.Supplier;

public final class ReactiveResourceSupport {

  private ReactiveResourceSupport() {
  }

  public static <T> Uni<T> fromWorker(Supplier<T> supplier) {
    return Uni.createFrom()
      .item(supplier)
      .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
  }
}
