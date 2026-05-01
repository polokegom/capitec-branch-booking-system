import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { apiErrorMessage } from '../../utility/api-error.utils';
import {
  formatBookingDateTimeDate,
  formatBookingDateTimeTime,
  isDateRangeFilterValid,
  toIsoDate
} from '../../utility/date-time.utils';
import { createPaginationState } from '../../utility/pagination-state.utils';
import { trimToNull } from '../../utility/text.utils';
import { AuthService } from '../../services/auth.service';
import {
  AdminBooking,
  BranchAdminApiService
} from '../../services/branch-admin-api.service';

@Component({
  selector: 'app-admin-bookings-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoPipe],
  templateUrl: './admin-bookings-page.component.html',
  styleUrl: './admin-bookings-page.component.scss'
})
export class AdminBookingsPageComponent implements OnInit {
  private readonly branchAdminApiService = inject(BranchAdminApiService);
  private readonly router = inject(Router);
  readonly authService = inject(AuthService);

  readonly bookings = signal<AdminBooking[]>([]);
  readonly errorMessage = signal<string | null>(null);

  private readonly pagination = createPaginationState();
  readonly pageSize = this.pagination.pageSize;
  readonly startIndex = this.pagination.startIndex;
  readonly total = this.pagination.total;
  readonly hasMore = this.pagination.hasMore;
  readonly currentPage = this.pagination.currentPage;
  readonly totalPages = this.pagination.totalPages;

  readonly branchSearch = signal('');
  readonly startDate = signal(toIsoDate(new Date()));
  readonly endDate = signal(toIsoDate(new Date()));

  readonly groupedBookings = computed(() => {
    const bookingsByBranch = new Map<string, AdminBooking[]>();
    for (const booking of this.bookings()) {
      const key = booking.branchName ?? booking.branchId;
      const branchBookings = bookingsByBranch.get(key) ?? [];
      branchBookings.push(booking);
      bookingsByBranch.set(key, branchBookings);
    }
    return Array.from(bookingsByBranch.entries()).map(([branchName, items]) => ({ branchName, items }));
  });

  readonly loading = signal(false);
  readonly backgroundRefreshing = signal(false);
  readonly showInitialLoadingState = computed(() => this.loading() && this.bookings().length === 0);
  readonly showEmptyState = computed(() => !this.loading() && this.bookings().length === 0);
  readonly canSearch = computed(() => !this.loading());

  async ngOnInit(): Promise<void> {
    await this.search();
  }

  async search(options: { resetPage?: boolean; preserveVisibleContent?: boolean } = {}): Promise<void> {
    const { resetPage = true, preserveVisibleContent = false } = options;
    if (!this.isDateRangeValid()) {
      this.errorMessage.set('admin.bookings.invalidRange');
      return;
    }
    if (resetPage) {
      this.pagination.reset();
    }
    this.loading.set(true);
    this.backgroundRefreshing.set(preserveVisibleContent && this.bookings().length > 0);
    this.errorMessage.set(null);
    try {
      const pageStartIndex = this.startIndex();
      const bookingPage = await this.branchAdminApiService.listBookings(
        {
          startDate: this.startDate(),
          endDate: this.endDate(),
          branchSearch: trimToNull(this.branchSearch())
        },
        pageStartIndex,
        this.pagination.endIndex()
      );
      this.bookings.set(bookingPage.items);
      this.pagination.applyPage(bookingPage);
    } catch (error: unknown) {
      this.errorMessage.set(apiErrorMessage(error, 'admin.bookings.loadError'));
      this.bookings.set([]);
      this.pagination.clear();
    } finally {
      this.loading.set(false);
      this.backgroundRefreshing.set(false);
    }
  }

  nextPage(): void {
    if (this.loading() || !this.pagination.next()) return;
    void this.search({ resetPage: false, preserveVisibleContent: true });
  }

  previousPage(): void {
    if (this.loading() || !this.pagination.previous()) return;
    void this.search({ resetPage: false, preserveVisibleContent: true });
  }

  resetFilters(): void {
    this.branchSearch.set('');
    this.startDate.set(toIsoDate(new Date()));
    this.endDate.set(toIsoDate(new Date()));
    void this.search();
  }

  async signOut(): Promise<void> {
    await this.authService.logout();
    await this.router.navigateByUrl('/login');
  }

  goToBranches(): void {
    void this.router.navigateByUrl('/admin/branches');
  }

  formatDate(value: string): string {
    return formatBookingDateTimeDate(value);
  }

  formatTime(value: string): string {
    return formatBookingDateTimeTime(value);
  }

  private isDateRangeValid(): boolean {
    return isDateRangeFilterValid(this.startDate(), this.endDate());
  }
}
