import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../services/auth.service';
import { BookingApiService, MyBooking } from '../services/booking-api.service';
import { apiErrorMessage } from '../utility/api-error.utils';
import { formatBookingDateTimeDate, formatBookingDateTimeTime, isDateRangeFilterValid } from '../utility/date-time.utils';
import { createPaginationState } from '../utility/pagination-state.utils';
import { trimToNull } from '../utility/text.utils';

@Component({
  selector: 'app-customer-bookings-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoPipe],
  templateUrl: './customer-bookings-page.component.html',
  styleUrl: './customer-bookings-page.component.scss'
})
export class CustomerBookingsPageComponent implements OnInit {
  private readonly bookingApiService = inject(BookingApiService);
  private readonly router = inject(Router);
  readonly authService = inject(AuthService);

  readonly bookings = signal<MyBooking[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly cancellingReference = signal<string | null>(null);

  private readonly pagination = createPaginationState();
  readonly pageSize = this.pagination.pageSize;
  readonly startIndex = this.pagination.startIndex;
  readonly total = this.pagination.total;
  readonly hasMore = this.pagination.hasMore;
  readonly currentPage = this.pagination.currentPage;
  readonly totalPages = this.pagination.totalPages;

  readonly branchSearch = signal('');
  readonly startDate = signal('');
  readonly endDate = signal('');
  readonly statusFilter = signal<'all' | 'active' | 'completed' | 'cancelled'>('all');

  private getDerivedBookingStatus(booking: MyBooking): 'active' | 'completed' | 'cancelled' {
    if (booking.status === 'CANCELLED') return 'cancelled';
    const appointmentStart = new Date(booking.startDateTime);
    return appointmentStart > new Date() ? 'active' : 'completed';
  }

  readonly filteredBookings = computed(() => {
    const selectedStatusFilter = this.statusFilter();
    return this.bookings().filter((booking) => {
      return selectedStatusFilter === 'all' || this.getDerivedBookingStatus(booking) === selectedStatusFilter;
    });
  });
  readonly statusGroups = computed(() => {
    const sortByAppointmentStartAscending = (firstBooking: MyBooking, secondBooking: MyBooking) => {
      return Date.parse(firstBooking.startDateTime) - Date.parse(secondBooking.startDateTime);
    };
    const bookingsByStatus: Record<'active' | 'completed' | 'cancelled', MyBooking[]> = {
      active: [],
      completed: [],
      cancelled: []
    };
    for (const booking of this.filteredBookings()) {
      bookingsByStatus[this.getDerivedBookingStatus(booking)].push(booking);
    }
    const statusDisplayOrder: { key: 'active' | 'completed' | 'cancelled'; labelKey: string }[] = [
      { key: 'active', labelKey: 'customer.bookings.status_active' },
      { key: 'completed', labelKey: 'customer.bookings.status_completed' },
      { key: 'cancelled', labelKey: 'customer.bookings.status_cancelled' }
    ];
    return statusDisplayOrder
      .map((statusGroup) => ({
        ...statusGroup,
        items: bookingsByStatus[statusGroup.key].sort(sortByAppointmentStartAscending)
      }))
      .filter((statusGroup) => statusGroup.items.length > 0);
  });

  readonly loading = signal(false);
  readonly backgroundRefreshing = signal(false);
  readonly showInitialLoadingState = computed(() => this.loading() && this.bookings().length === 0);
  readonly showEmptyState = computed(() => !this.loading() && this.filteredBookings().length === 0);
  readonly canSearch = computed(() => !this.loading());

  async ngOnInit(): Promise<void> {
    await this.search();
  }

  async search(options: { resetPage?: boolean; preserveVisibleContent?: boolean } = {}): Promise<void> {
    const { resetPage = true, preserveVisibleContent = false } = options;
    if (!this.isDateRangeValid()) {
      this.errorMessage.set('customer.bookings.invalidRange');
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
      const selectedStartDate = this.startDate();
      const selectedEndDate = this.endDate();
      const bookingPage = await this.bookingApiService.listMyBookings(
        {
          startDate: selectedStartDate && selectedEndDate ? selectedStartDate : null,
          endDate: selectedStartDate && selectedEndDate ? selectedEndDate : null,
          branchSearch: trimToNull(this.branchSearch())
        },
        pageStartIndex,
        this.pagination.endIndex()
      );
      this.bookings.set(bookingPage.items);
      this.pagination.applyPage(bookingPage);
    } catch (error: unknown) {
      this.errorMessage.set(apiErrorMessage(error, 'customer.bookings.loadError'));
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
    this.startDate.set('');
    this.endDate.set('');
    this.statusFilter.set('all');
    void this.search();
  }

  isCancellable(booking: MyBooking): boolean {
    if (booking.status !== 'CONFIRMED') return false;
    const appointmentStart = new Date(booking.startDateTime);
    return appointmentStart > new Date();
  }

  async cancel(booking: MyBooking): Promise<void> {
    if (!this.isCancellable(booking)) return;

    this.cancellingReference.set(booking.bookingReference);
    this.errorMessage.set(null);
    try {
      await this.bookingApiService.cancelMyBooking(booking.bookingReference);
      await this.search({ resetPage: false, preserveVisibleContent: true });
    } catch (error: unknown) {
      this.errorMessage.set(apiErrorMessage(error, 'customer.bookings.cancelError'));
    } finally {
      this.cancellingReference.set(null);
    }
  }

  goToBook(): void {
    void this.router.navigateByUrl('/book');
  }

  async signOut(): Promise<void> {
    await this.authService.logout();
    await this.router.navigateByUrl('/login');
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
