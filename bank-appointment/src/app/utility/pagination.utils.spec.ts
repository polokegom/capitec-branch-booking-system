import {
  currentPageNumber,
  nextPageStartIndex,
  pageEndIndex,
  previousPageStartIndex,
  totalPageCount
} from './pagination.utils';

describe('pagination utilities', () => {
  it('calculates the visible page number from the start index', () => {
    expect(currentPageNumber(0)).toBe(1);
    expect(currentPageNumber(20)).toBe(3);
  });

  it('keeps the total page count at one when there are no items', () => {
    expect(totalPageCount(0)).toBe(1);
    expect(totalPageCount(21)).toBe(3);
  });

  it('calculates the next page range', () => {
    expect(pageEndIndex(10)).toBe(20);
    expect(nextPageStartIndex(10)).toBe(20);
  });

  it('does not move the previous page before the first item', () => {
    expect(previousPageStartIndex(0)).toBe(0);
    expect(previousPageStartIndex(10)).toBe(0);
    expect(previousPageStartIndex(20)).toBe(10);
  });
});
