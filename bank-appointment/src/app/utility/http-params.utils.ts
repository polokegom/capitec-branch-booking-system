import { HttpParams } from '@angular/common/http';

export function appendOptionalParam(
  params: HttpParams,
  key: string,
  value: string | null | undefined
): HttpParams {
  return value === null || value === undefined || value === '' ? params : params.set(key, value);
}

export function appendCompleteDateRangeParams(
  params: HttpParams,
  startDate: string | null | undefined,
  endDate: string | null | undefined
): HttpParams {
  return startDate && endDate
    ? params.set('startDate', startDate).set('endDate', endDate)
    : params;
}
