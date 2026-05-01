import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { firstValueFrom } from 'rxjs';
import { appendCompleteDateRangeParams, appendOptionalParam } from '../utility/http-params.utils';

export interface PaginationResponse<T> {
  items: T[];
  total: number;
  startIndex: number;
  endIndex: number;
  hasMore: boolean;
}

export interface BookingBranch {
  id: string;
  code: string;
  name: string;
  city: string;
  province: string | null;
  country: string;
}

export interface BookingSlot {
  branchId: string;
  startDateTime: string;
  endDateTime: string;
  capacity: number;
  reservedCount: number;
  remainingCapacity: number;
}

export interface CreateBookingPayload {
  branchId: string;
  startDateTime: string;
  endDateTime: string;
  customerName: string;
  customerEmail: string;
  preferredLanguage: string;
}

export interface BookingReceipt {
  bookingId: string;
  bookingReference: string;
  branchId: string;
  startDateTime: string;
  endDateTime: string;
  customerName: string;
  customerEmail: string;
  preferredLanguage: string;
  status: string;
  createdAt: string;
}

export interface BookingApiError {
  errorCode: string;
  message: string;
  timestamp: string;
}

@Injectable({ providedIn: 'root' })
export class BookingApiService {
  private readonly apiBaseUrl = '/api/v1';

  constructor(private readonly httpClient: HttpClient) {}

  searchBranches(query: string, startIndex = 0, endIndex = 10): Observable<PaginationResponse<BookingBranch>> {
    let params = new HttpParams()
      .set('startIndex', String(startIndex))
      .set('endIndex', String(endIndex));
    const branchSearchText = query.trim();

    if (branchSearchText) {
      params = params.set('query', branchSearchText);
    }

    return this.httpClient.get<PaginationResponse<BookingBranch>>(`${this.apiBaseUrl}/branches`, { params });
  }

  getAvailability(branchId: string, appointmentDate: string): Observable<BookingSlot[]> {
    const params = new HttpParams()
      .set('branchId', branchId)
      .set('date', appointmentDate);

    return this.httpClient.get<BookingSlot[]>(`${this.apiBaseUrl}/availability`, { params });
  }

  createBooking(payload: CreateBookingPayload, idempotencyKey: string): Observable<BookingReceipt> {
    return this.httpClient.post<BookingReceipt>(`${this.apiBaseUrl}/bookings`, payload, {
      headers: new HttpHeaders({ 'X-Idempotency-Key': idempotencyKey })
    });
  }

  listMyBookings(
    filters: MyBookingsFilters = {},
    startIndex = 0,
    endIndex = 10
  ): Promise<PaginationResponse<MyBooking>> {
    let params = new HttpParams()
      .set('startIndex', String(startIndex))
      .set('endIndex', String(endIndex));
    params = appendCompleteDateRangeParams(params, filters.startDate, filters.endDate);
    params = appendOptionalParam(params, 'branchSearch', filters.branchSearch);
    return firstValueFrom(
      this.httpClient.get<PaginationResponse<MyBooking>>(`${this.apiBaseUrl}/customer/bookings`, { params })
    );
  }

  cancelMyBooking(bookingReference: string): Promise<BookingReceipt> {
    return firstValueFrom(
      this.httpClient.delete<BookingReceipt>(`${this.apiBaseUrl}/bookings/${encodeURIComponent(bookingReference)}`)
    );
  }
}

export interface MyBookingsFilters {
  startDate?: string | null;
  endDate?: string | null;
  branchSearch?: string | null;
}

export interface MyBooking {
  id: string;
  bookingReference: string;
  branchId: string;
  branchName: string | null;
  branchCity: string | null;
  branchCountry: string | null;
  startDateTime: string;
  endDateTime: string;
  customerName: string;
  customerEmail: string;
  preferredLanguage: string;
  status: string;
  createdAt: string;
}
