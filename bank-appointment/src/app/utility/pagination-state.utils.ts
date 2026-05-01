import { Signal, WritableSignal, computed, signal } from '@angular/core';
import {
  DEFAULT_PAGE_SIZE,
  currentPageNumber,
  nextPageStartIndex,
  pageEndIndex,
  previousPageStartIndex,
  totalPageCount
} from './pagination.utils';

export interface PaginationStateSnapshot {
  total: number;
  hasMore: boolean;
}

export interface PaginationState {
  pageSize: number;
  startIndex: WritableSignal<number>;
  total: WritableSignal<number>;
  hasMore: WritableSignal<boolean>;
  currentPage: Signal<number>;
  totalPages: Signal<number>;
  applyPage(snapshot: PaginationStateSnapshot): void;
  clear(): void;
  reset(): void;
  next(): boolean;
  previous(): boolean;
  endIndex(): number;
}

export function createPaginationState(pageSize = DEFAULT_PAGE_SIZE): PaginationState {
  const startIndex = signal(0);
  const total = signal(0);
  const hasMore = signal(false);

  return {
    pageSize,
    startIndex,
    total,
    hasMore,
    currentPage: computed(() => currentPageNumber(startIndex(), pageSize)),
    totalPages: computed(() => totalPageCount(total(), pageSize)),
    applyPage(snapshot) {
      total.set(snapshot.total);
      hasMore.set(snapshot.hasMore);
    },
    clear() {
      total.set(0);
      hasMore.set(false);
    },
    reset() {
      startIndex.set(0);
    },
    next() {
      if (!hasMore()) {
        return false;
      }
      startIndex.set(nextPageStartIndex(startIndex(), pageSize));
      return true;
    },
    previous() {
      if (startIndex() === 0) {
        return false;
      }
      startIndex.set(previousPageStartIndex(startIndex(), pageSize));
      return true;
    },
    endIndex() {
      return pageEndIndex(startIndex(), pageSize);
    }
  };
}