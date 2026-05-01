package za.co.capitec.booking.api.dto;

import java.util.List;
import java.util.function.Function;
import za.co.capitec.booking.domain.model.Pagination;

public record PaginationResponse<T>(
  List<T> items,
  long total,
  int startIndex,
  int endIndex,
  boolean hasMore
) {
  public static <T> PaginationResponse<T> of(Pagination<T> pagination) {
    return new PaginationResponse<>(pagination.items(), pagination.total(), pagination.startIndex(), pagination.endIndex(), pagination.hasMore());
  }

  public static <T, R> PaginationResponse<R> mapped(Pagination<T> pagination, Function<T, R> mapper) {
    List<R> mappedItems = pagination.items().stream().map(mapper).toList();
    return new PaginationResponse<>(mappedItems, pagination.total(), pagination.startIndex(), pagination.endIndex(), pagination.hasMore());
  }
}
