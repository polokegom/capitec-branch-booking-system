import { apiErrorMessage } from './api-error.utils';

describe('apiErrorMessage', () => {
  it('returns a direct error message when present', () => {
    expect(apiErrorMessage({ message: 'Direct message' }, 'fallback.key')).toBe('Direct message');
  });

  it('returns a nested API error message when present', () => {
    expect(apiErrorMessage({ error: { message: 'Nested message' } }, 'fallback.key')).toBe('Nested message');
  });

  it('returns the fallback key when no message is available', () => {
    expect(apiErrorMessage({ error: {} }, 'fallback.key')).toBe('fallback.key');
  });
});
