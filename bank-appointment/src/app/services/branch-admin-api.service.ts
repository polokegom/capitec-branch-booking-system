import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { PaginationResponse } from './booking-api.service';
import { appendCompleteDateRangeParams, appendOptionalParam } from '../utility/http-params.utils';

const ADMIN_BRANCHES_API_URL = '/api/v1/admin/branches';
const ADMIN_BOOKINGS_API_URL = '/api/v1/admin/bookings';
const BRANCH_COUNTRIES_API_URL = '/api/v1/branches/countries';

export interface AdminBranch {
  id: string;
  code: string;
  name: string;
  city: string;
  province: string | null;
  address: string | null;
  country: string;
  openingTime: string;
  closingTime: string;
  active: boolean;
  adminEmail: string | null;
}

export interface AdminBranchPayload {
  code: string;
  name: string;
  city: string;
  province: string | null;
  address: string | null;
  country: string;
  openingTime: string;
  closingTime: string;
  adminEmail: string | null;
}

export interface BranchMarket {
  timezone: string;
  provinces: string[];
}

@Injectable({ providedIn: 'root' })
export class BranchAdminApiService {
  private readonly http = inject(HttpClient);

  list(startIndex = 0, endIndex = 10): Promise<PaginationResponse<AdminBranch>> {
    return firstValueFrom(this.http.get<PaginationResponse<AdminBranch>>(ADMIN_BRANCHES_API_URL, {
      params: this.paginationParams(startIndex, endIndex)
    }));
  }

  create(payload: AdminBranchPayload): Promise<AdminBranch> {
    return firstValueFrom(this.http.post<AdminBranch>(ADMIN_BRANCHES_API_URL, payload));
  }

  update(id: string, payload: AdminBranchPayload): Promise<AdminBranch> {
    return firstValueFrom(this.http.put<AdminBranch>(`${ADMIN_BRANCHES_API_URL}/${id}`, payload));
  }

  deactivate(id: string): Promise<void> {
    return firstValueFrom(this.http.patch<void>(`${ADMIN_BRANCHES_API_URL}/${id}`, { active: false }));
  }

  reactivate(id: string): Promise<void> {
    return firstValueFrom(this.http.patch<void>(`${ADMIN_BRANCHES_API_URL}/${id}`, { active: true }));
  }

  listCountries(): Promise<Record<string, BranchMarket>> {
    return firstValueFrom(this.http.get<Record<string, BranchMarket>>(BRANCH_COUNTRIES_API_URL));
  }

  listVisibleBranches(startIndex = 0, endIndex = 100): Promise<PaginationResponse<AdminBranch>> {
    return firstValueFrom(this.http.get<PaginationResponse<AdminBranch>>(`${ADMIN_BOOKINGS_API_URL}/branches`, {
      params: this.paginationParams(startIndex, endIndex)
    }));
  }

  listBookings(
    filters: AdminBookingFilters,
    startIndex = 0,
    endIndex = 10
  ): Promise<PaginationResponse<AdminBooking>> {
    let params = this.paginationParams(startIndex, endIndex);
    params = appendCompleteDateRangeParams(params, filters.startDate, filters.endDate);
    params = appendOptionalParam(params, 'branchSearch', filters.branchSearch);
    return firstValueFrom(this.http.get<PaginationResponse<AdminBooking>>(ADMIN_BOOKINGS_API_URL, { params }));
  }

  private paginationParams(startIndex: number, endIndex: number): HttpParams {
    return new HttpParams()
      .set('startIndex', String(startIndex))
      .set('endIndex', String(endIndex));
  }

}

export interface AdminBookingFilters {
  startDate?: string | null;
  endDate?: string | null;
  branchSearch?: string | null;
}

export interface AdminBooking {
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
