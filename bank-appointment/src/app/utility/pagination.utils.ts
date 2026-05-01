export const DEFAULT_PAGE_SIZE = 10;

export function currentPageNumber(startIndex: number, pageSize = DEFAULT_PAGE_SIZE): number {
  return Math.floor(startIndex / pageSize) + 1;
}

export function totalPageCount(totalItems: number, pageSize = DEFAULT_PAGE_SIZE): number {
  return Math.max(1, Math.ceil(totalItems / pageSize));
}

export function pageEndIndex(startIndex: number, pageSize = DEFAULT_PAGE_SIZE): number {
  return startIndex + pageSize;
}

export function nextPageStartIndex(startIndex: number, pageSize = DEFAULT_PAGE_SIZE): number {
  return startIndex + pageSize;
}

export function previousPageStartIndex(startIndex: number, pageSize = DEFAULT_PAGE_SIZE): number {
  return Math.max(0, startIndex - pageSize);
}
