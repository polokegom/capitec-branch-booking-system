package za.co.capitec.booking.api.utility;

public record PaginationRange(int startIndex, int endIndex) {
  public static final int DEFAULT_PAGE_SIZE = 10;
  public static final int MAX_PAGE_SIZE = 100;

  public PaginationRange {
    if (startIndex < 0) {
      startIndex = 0;
    }
    if (endIndex <= startIndex) {
      endIndex = startIndex + DEFAULT_PAGE_SIZE;
    }
    int size = endIndex - startIndex;
    if (size > MAX_PAGE_SIZE) {
      endIndex = startIndex + MAX_PAGE_SIZE;
    }
  }

  public static PaginationRange from(Integer startIndex, Integer endIndex) {
    int start = (startIndex == null || startIndex < 0) ? 0 : startIndex;
    int end = (endIndex == null || endIndex <= 0) ? start + DEFAULT_PAGE_SIZE : endIndex;
    return new PaginationRange(start, end);
  }

  public int size() {
    return endIndex - startIndex;
  }
}
