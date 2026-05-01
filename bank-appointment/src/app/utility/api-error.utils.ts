export function apiErrorMessage(error: unknown, fallbackKey: string): string {
  if (hasMessage(error)) {
    return error.message;
  }
  if (hasNestedMessage(error)) {
    return error.error.message;
  }
  return fallbackKey;
}

function hasMessage(value: unknown): value is { message: string } {
  return typeof value === 'object' && value !== null && 'message' in value && typeof value.message === 'string';
}

function hasNestedMessage(value: unknown): value is { error: { message: string } } {
  if (typeof value !== 'object' || value === null || !('error' in value)) {
    return false;
  }
  const errorBody = value.error;
  return typeof errorBody === 'object' && errorBody !== null && 'message' in errorBody && typeof errorBody.message === 'string';
}
