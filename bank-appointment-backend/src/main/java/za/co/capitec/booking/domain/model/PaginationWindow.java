package za.co.capitec.booking.domain.model;

import java.util.List;

public record PaginationWindow(int startIndex, int endIndex, int requestedItemCount) {
  public static PaginationWindow from(int startIndex, int endIndex) {
    int firstItemIndex = Math.max(0, startIndex);
    int endItemIndex = Math.max(firstItemIndex, endIndex);
    return new PaginationWindow(firstItemIndex, endItemIndex, endItemIndex - firstItemIndex);
  }

  public boolean isEmpty() {
    return requestedItemCount == 0;
  }

  public <T> Pagination<T> empty(long total) {
    return new Pagination<>(List.of(), total, startIndex, endIndex);
  }

  public <T> Pagination<T> toPagination(List<T> items, long total) {
    return new Pagination<>(items, total, startIndex, endIndex);
  }
}
