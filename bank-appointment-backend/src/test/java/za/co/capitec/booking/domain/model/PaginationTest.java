package za.co.capitec.booking.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaginationTest {
  @Test
  void shouldClampNegativeStartIndexes() {
    Pagination<String> pagination = Pagination.slice(List.of("A", "B", "C"), -5, 2);

    assertThat(pagination.items()).containsExactly("A", "B");
    assertThat(pagination.startIndex()).isZero();
    assertThat(pagination.endIndex()).isEqualTo(2);
    assertThat(pagination.total()).isEqualTo(3);
    assertThat(pagination.hasMore()).isTrue();
  }

  @Test
  void shouldReturnEmptyPageWhenStartIsBeyondAvailableItems() {
    Pagination<String> pagination = Pagination.slice(List.of("A", "B"), 10, 20);

    assertThat(pagination.items()).isEmpty();
    assertThat(pagination.total()).isEqualTo(2);
    assertThat(pagination.hasMore()).isFalse();
  }
}
