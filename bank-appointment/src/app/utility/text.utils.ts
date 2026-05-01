export function trimToNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim() ?? '';
  return trimmed ? trimmed : null;
}

export function trimToUpper(value: string | null | undefined): string {
  return value?.trim().toUpperCase() ?? '';
}

export function searchTokens(value: string | null | undefined): string[] {
  return value?.trim().toLowerCase().split(/\s+/).filter(Boolean) ?? [];
}
