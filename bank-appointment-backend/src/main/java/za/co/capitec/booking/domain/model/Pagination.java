package za.co.capitec.booking.domain.model;

import java.util.List;

public record Pagination<T>(List<T> items, long total, int startIndex, int endIndex) {
  public Pagination {
    items = items == null ? List.of() : List.copyOf(items);
  }

  public static <T> Pagination<T> empty(int startIndex, int endIndex) {
    return PaginationWindow.from(startIndex, endIndex).empty(0L);
  }

  public static <T> Pagination<T> slice(List<T> all, int startIndex, int endIndex) {
    if (all == null || all.isEmpty()) {
      return empty(startIndex, endIndex);
    }
    PaginationWindow paginationWindow = PaginationWindow.from(startIndex, endIndex);
    int size = all.size();
    int firstItemIndex = Math.min(paginationWindow.startIndex(), size);
    int endItemIndex = Math.min(paginationWindow.endIndex(), size);
    return new Pagination<>(all.subList(firstItemIndex, endItemIndex), size, paginationWindow.startIndex(), paginationWindow.endIndex());
  }

  public boolean hasMore() {
    return endIndex < total;
  }
}
