import { searchTokens, trimToNull, trimToUpper } from './text.utils';

describe('text utilities', () => {
  it('trims blank input to null', () => {
    expect(trimToNull('  branch  ')).toBe('branch');
    expect(trimToNull('   ')).toBeNull();
    expect(trimToNull(null)).toBeNull();
  });

  it('trims and uppercases reference text', () => {
    expect(trimToUpper(' abc123 ')).toBe('ABC123');
    expect(trimToUpper(undefined)).toBe('');
  });

  it('splits search text into lowercase tokens', () => {
    expect(searchTokens('  Sandton   Johannesburg  ')).toEqual(['sandton', 'johannesburg']);
    expect(searchTokens('   ')).toEqual([]);
  });
});
