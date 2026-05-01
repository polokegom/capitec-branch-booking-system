import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { DateAdapter, MatNativeDateModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { BookingApiError, BookingApiService, BookingBranch, BookingSlot, CreateBookingPayload, MyBooking } from '../services/booking-api.service';
import { AuthService } from '../services/auth.service';
import { addDays, formatBookingDateTimeTime, startOfDay, toIsoDate } from '../utility/date-time.utils';

interface BankBranch {
  id: string;
  name: string;
  city: string;
}

interface AvailableBookingSlot {
  startDateTime: string;
  endDateTime: string;
  label: string;
  remainingCapacity: number;
}

interface BookingConfirmation {
  success: boolean;
  messageKey: string;
  reference?: string;
}

@Component({
  selector: 'app-booking-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    TranslocoPipe
  ],
  templateUrl: './booking-page.component.html',
  styleUrl: './booking-page.component.scss'
})
export class BookingPageComponent implements OnInit, OnDestroy {
  readonly minDate = startOfDay(new Date());
  readonly maxDate = startOfDay(addDays(new Date(), 45));
  readonly steps = ['steps.branch', 'steps.date', 'steps.timeslot', 'steps.confirm'] as const;

  private readonly translocoService = inject(TranslocoService);
  private readonly dateAdapter = inject(DateAdapter<Date>);
  private readonly bookingApiService = inject(BookingApiService);
  readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly activeLanguage = toSignal(this.translocoService.langChanges$, {
    initialValue: this.translocoService.getActiveLang()
  });

  private readonly localeMap: Record<string, string> = {
    en: 'en-ZA',
    af: 'af-ZA',
    tn: 'tn-ZA',
    nso: 'nso-ZA',
    zu: 'zu-ZA',
    xh: 'xh-ZA'
  };

  readonly currentStep = signal(1);
  readonly branchQuery = signal('');
  readonly filteredBranches = signal<BankBranch[]>([]);
  readonly branchTotal = signal(0);
  readonly branchHasMore = signal(false);
  readonly isLoadingMoreBranches = signal(false);
  readonly branchPageSize = 10;
  readonly selectedBranch = signal<BankBranch | null>(null);
  readonly selectedDate = signal<Date | null>(null);
  readonly availableBookingSlots = signal<AvailableBookingSlot[]>([]);
  readonly selectedBookingSlot = signal<AvailableBookingSlot | null>(null);
  readonly isSubmitting = signal(false);
  readonly isLoadingBranches = signal(false);
  readonly isLoadingBookingSlots = signal(false);
  readonly branchErrorMessageKey = signal<string | null>(null);
  readonly bookingSlotErrorMessageKey = signal<string | null>(null);
  readonly confirmation = signal<BookingConfirmation | null>(null);
  readonly bookingAttemptIdempotencyKey = signal<string | null>(null);

  readonly selectedLocale = computed(() => this.localeMap[this.activeLanguage()] ?? 'en-ZA');
  readonly customerName = computed(() => this.authService.displayName()?.trim() ?? '');
  readonly customerEmail = computed(() => this.authService.email()?.trim() ?? '');
  readonly hasCustomerProfileDetails = computed(() => this.customerName().length > 0 && this.customerEmail().length > 0);
  readonly canSubmitAppointment = computed(() => {
    return this.authService.authenticated()
      && this.selectedBranch() !== null
      && this.selectedDate() !== null
      && this.selectedBookingSlot() !== null
      && !this.isSubmitting()
      && this.hasCustomerProfileDetails();
  });
  readonly selectedBranchLabel = computed(() => {
    const selectedBranch = this.selectedBranch();
    return selectedBranch ? `${selectedBranch.name}, ${selectedBranch.city}` : '';
  });
  readonly selectedDateLabel = computed(() => this.formatDateLabel(this.selectedDate()));

  private branchSearchTimeout: ReturnType<typeof setTimeout> | null = null;
  private branchRequestCounter = 0;
  private bookingSlotRequestCounter = 0;

  private readonly syncDateAdapterLocale = effect(() => {
    this.dateAdapter.setLocale(this.selectedLocale());
  });

  ngOnInit(): void {
    void this.loadBranches();
  }

  ngOnDestroy(): void {
    this.clearPendingBranchSearch();
  }

  onBranchQueryChange(query: string): void {
    this.branchQuery.set(query);
    this.scheduleBranchSearch();
  }

  dateFilter = (date: Date | null): boolean => {
    if (!date) {
      return false;
    }

    const day = date.getDay();
    const calendarDate = startOfDay(date);
    const inRange = calendarDate >= this.minDate && calendarDate <= this.maxDate;
    const notWeekend = day !== 0 && day !== 6;

    return inRange && notWeekend;
  };

  private clearConfirmationState(): void {
    this.confirmation.set(null);
    this.bookingAttemptIdempotencyKey.set(null);
  }

  private clearSelectedBookingSlot(options: { clearAvailableSlots?: boolean; clearBookingSlotError?: boolean } = {}): void {
    const { clearAvailableSlots = false, clearBookingSlotError = false } = options;

    this.selectedBookingSlot.set(null);
    if (clearAvailableSlots) {
      this.availableBookingSlots.set([]);
    }
    if (clearBookingSlotError) {
      this.bookingSlotErrorMessageKey.set(null);
    }
    this.clearConfirmationState();
  }

  private clearSelectedDateAndBookingSlot(): void {
    this.selectedDate.set(null);
    this.clearSelectedBookingSlot({ clearAvailableSlots: true, clearBookingSlotError: true });
  }

  chooseBranch(branch: BankBranch): void {
    this.branchRequestCounter += 1;
    this.bookingSlotRequestCounter += 1;

    const alternatives = this.filteredBranches()
      .filter((candidateBranch) => candidateBranch.id !== branch.id)
      .slice(0, 3);

    this.clearPendingBranchSearch();
    this.selectedBranch.set(branch);
    this.branchQuery.set(branch.name);
    this.filteredBranches.set(alternatives);
    this.branchHasMore.set(false);
    this.clearSelectedDateAndBookingSlot();
    this.currentStep.set(2);
  }

  chooseDate(date: Date | null): void {
    if (!date) {
      return;
    }

    this.selectedDate.set(startOfDay(date));
    this.clearSelectedBookingSlot({ clearAvailableSlots: true, clearBookingSlotError: true });
    this.currentStep.set(3);
    void this.loadAvailableBookingSlots();
  }

  chooseBookingSlot(bookingSlot: AvailableBookingSlot): void {
    this.selectedBookingSlot.set(bookingSlot);
    this.clearConfirmationState();
    this.bookingAttemptIdempotencyKey.set(this.generateIdempotencyKey());
    this.currentStep.set(4);
  }

  submitAppointment(): void {
    if (!this.canSubmitAppointment()) {
      return;
    }

    const selectedBranch = this.selectedBranch();
    const selectedBookingSlot = this.selectedBookingSlot();
    if (!selectedBranch || !selectedBookingSlot) {
      return;
    }

    const idempotencyKey = this.bookingAttemptIdempotencyKey() ?? this.generateIdempotencyKey();
    this.bookingAttemptIdempotencyKey.set(idempotencyKey);

    const payload: CreateBookingPayload = {
      branchId: selectedBranch.id,
      startDateTime: selectedBookingSlot.startDateTime,
      endDateTime: selectedBookingSlot.endDateTime,
      customerName: this.customerName(),
      customerEmail: this.customerEmail(),
      preferredLanguage: this.translocoService.getActiveLang()
    };

    this.isSubmitting.set(true);

    void this.createBooking(payload, idempotencyKey);
  }

  retryFromTimeslot(): void {
    this.clearSelectedBookingSlot();
    this.currentStep.set(3);
    void this.loadAvailableBookingSlots();
  }

  viewBookings(): void {
    void this.router.navigateByUrl('/my-bookings');
  }

  handleConfirmationAction(bookingConfirmation: BookingConfirmation): void {
    if (this.shouldNavigateToBookings(bookingConfirmation)) {
      this.viewBookings();
      return;
    }

    this.retryFromTimeslot();
  }

  confirmationActionLabelKey(bookingConfirmation: BookingConfirmation): string {
    return this.shouldNavigateToBookings(bookingConfirmation)
      ? 'confirmation.viewBooking'
      : 'confirmation.retryTimeslot';
  }

  goBack(): void {
    if (this.currentStep() === 4) {
      this.currentStep.set(3);
      return;
    }

    if (this.currentStep() === 3) {
      this.bookingSlotRequestCounter += 1;
      this.currentStep.set(2);
      this.clearSelectedBookingSlot();
      return;
    }

    if (this.currentStep() === 2) {
      this.currentStep.set(1);
      void this.loadBranches(this.branchQuery());
    }
  }

  goToStep(step: number): void {
    if (step === this.currentStep() || this.confirmation()?.success) {
      return;
    }
    if (!this.isStepAvailable(step) || step > this.currentStep()) {
      return;
    }
    if (step <= 3) {
      this.clearConfirmationState();
    }
    if (step <= 2) {
      this.selectedBookingSlot.set(null);
      this.bookingAttemptIdempotencyKey.set(null);
      this.bookingSlotRequestCounter += 1;
    }
    if (step === 1) {
      void this.loadBranches(this.branchQuery());
    }
    this.currentStep.set(step);
  }

  formatDateLabel(date: Date | null): string {
    if (!date) {
      return '';
    }

    return date.toLocaleDateString(this.selectedLocale(), {
      localeMatcher: 'best fit',
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    });
  }

  isStepAvailable(step: number): boolean {
    if (step === 1) {
      return true;
    }

    if (step === 2) {
      return this.selectedBranch() !== null;
    }

    if (step === 3) {
      return this.selectedBranch() !== null && this.selectedDate() !== null;
    }

    return this.selectedBranch() !== null && this.selectedDate() !== null && this.selectedBookingSlot() !== null;
  }

  private clearPendingBranchSearch(): void {
    if (!this.branchSearchTimeout) {
      return;
    }

    clearTimeout(this.branchSearchTimeout);
    this.branchSearchTimeout = null;
  }

  private scheduleBranchSearch(): void {
    this.clearPendingBranchSearch();
    const query = this.branchQuery().trim();
    this.branchSearchTimeout = setTimeout(() => {
      void this.loadBranches(query);
    }, 180);
  }

  private async createBooking(payload: CreateBookingPayload, idempotencyKey: string): Promise<void> {
    try {
      const bookingReceipt = await firstValueFrom(this.bookingApiService.createBooking(payload, idempotencyKey));
      this.setBookingSuccessConfirmation(bookingReceipt.bookingReference);
    } catch (error: unknown) {
      const httpError = this.toHttpErrorResponse(error);
      const recoveredBooking = await this.findRecoveredConfirmedBooking(payload, httpError);

      if (recoveredBooking) {
        this.setBookingSuccessConfirmation(recoveredBooking.bookingReference);
        return;
      }

      this.confirmation.set({
        success: false,
        messageKey: this.mapSubmissionError(httpError)
      });
    } finally {
      this.isSubmitting.set(false);
    }
  }

  private async findRecoveredConfirmedBooking(payload: CreateBookingPayload, error: HttpErrorResponse): Promise<MyBooking | null> {
    if (!this.shouldAttemptBookingRecovery(error)) {
      return null;
    }

    try {
      const recoveryLookupLimit = 50;
      const bookings = await this.bookingApiService.listMyBookings({}, 0, recoveryLookupLimit);
      return bookings.items.find((booking) => this.isMatchingConfirmedBooking(booking, payload)) ?? null;
    } catch {
      return null;
    }
  }

  private shouldAttemptBookingRecovery(error: HttpErrorResponse): boolean {
    const apiError = error.error as BookingApiError | undefined;

    return apiError?.errorCode === 'booking_slot_unavailable'
      || error.status === 0
      || error.status >= 500;
  }

  private shouldNavigateToBookings(bookingConfirmation: BookingConfirmation): boolean {
    return bookingConfirmation.success
      || bookingConfirmation.messageKey === 'confirmation.activeBookingErrorMessage';
  }

  private isMatchingConfirmedBooking(booking: MyBooking, payload: CreateBookingPayload): boolean {
    return booking.status === 'CONFIRMED'
      && booking.branchId === payload.branchId
      && booking.startDateTime === payload.startDateTime
      && booking.endDateTime === payload.endDateTime;
  }

  private setBookingSuccessConfirmation(bookingReference: string): void {
    this.confirmation.set({
      success: true,
      messageKey: 'confirmation.successMessage',
      reference: bookingReference
    });
  }

  private async loadBranches(query = this.branchQuery()): Promise<void> {
    this.clearPendingBranchSearch();

    const requestId = ++this.branchRequestCounter;
    this.isLoadingBranches.set(true);
    this.isLoadingMoreBranches.set(false);
    this.branchErrorMessageKey.set(null);

    try {
      const pagination = await firstValueFrom(this.bookingApiService.searchBranches(query, 0, this.branchPageSize));
      if (requestId !== this.branchRequestCounter) {
        return;
      }

      const branches = pagination.items.map((branch) => this.toBankBranch(branch));
      this.filteredBranches.set(branches);
      this.branchTotal.set(pagination.total);
      this.branchHasMore.set(pagination.hasMore);
    } catch {
      if (requestId !== this.branchRequestCounter) {
        return;
      }

      this.filteredBranches.set([]);
      this.branchTotal.set(0);
      this.branchHasMore.set(false);
      this.branchErrorMessageKey.set('branch.loadError');
    } finally {
      if (requestId === this.branchRequestCounter) {
        this.isLoadingBranches.set(false);
      }
    }
  }

  async loadMoreBranches(): Promise<void> {
    if (!this.branchHasMore() || this.isLoadingMoreBranches() || this.isLoadingBranches()) {
      return;
    }

    const requestId = this.branchRequestCounter;
    const startIndex = this.filteredBranches().length;
    const endIndex = startIndex + this.branchPageSize;
    this.isLoadingMoreBranches.set(true);

    try {
      const pagination = await firstValueFrom(this.bookingApiService.searchBranches(this.branchQuery(), startIndex, endIndex));
      if (requestId !== this.branchRequestCounter) {
        return;
      }

      const newBranches = pagination.items.map((branch) => this.toBankBranch(branch));
      this.filteredBranches.update((currentBranches) => [...currentBranches, ...newBranches]);
      this.branchTotal.set(pagination.total);
      this.branchHasMore.set(pagination.hasMore);
    } catch {
      if (requestId !== this.branchRequestCounter) {
        return;
      }

      this.branchErrorMessageKey.set('branch.loadError');
    } finally {
      if (requestId === this.branchRequestCounter) {
        this.isLoadingMoreBranches.set(false);
      }
    }
  }

  private async loadAvailableBookingSlots(): Promise<void> {
    const selectedBranch = this.selectedBranch();
    const selectedDate = this.selectedDate();
    if (!selectedBranch || !selectedDate) {
      return;
    }

    const requestId = ++this.bookingSlotRequestCounter;
    this.isLoadingBookingSlots.set(true);
    this.bookingSlotErrorMessageKey.set(null);

    try {
      const slots = await firstValueFrom(this.bookingApiService.getAvailability(selectedBranch.id, toIsoDate(selectedDate)));
      if (requestId !== this.bookingSlotRequestCounter) {
        return;
      }

      this.availableBookingSlots.set(slots.map((bookingSlot) => this.toAvailableBookingSlot(bookingSlot)));
    } catch {
      if (requestId !== this.bookingSlotRequestCounter) {
        return;
      }

      this.availableBookingSlots.set([]);
      this.bookingSlotErrorMessageKey.set('bookingSlot.loadError');
    } finally {
      if (requestId === this.bookingSlotRequestCounter) {
        this.isLoadingBookingSlots.set(false);
      }
    }
  }

  private toBankBranch(branch: BookingBranch): BankBranch {
    return {
      id: branch.id,
      name: branch.name,
      city: branch.city
    };
  }

  private toAvailableBookingSlot(bookingSlot: BookingSlot): AvailableBookingSlot {
    return {
      startDateTime: bookingSlot.startDateTime,
      endDateTime: bookingSlot.endDateTime,
      label: this.toTimeLabel(bookingSlot.startDateTime),
      remainingCapacity: bookingSlot.remainingCapacity
    };
  }

  private toTimeLabel(timeValue: string): string {
    return formatBookingDateTimeTime(timeValue, this.selectedLocale());
  }

  private toHttpErrorResponse(error: unknown): HttpErrorResponse {
    return error instanceof HttpErrorResponse ? error : new HttpErrorResponse({ error });
  }

  private mapSubmissionError(error: HttpErrorResponse): string {
    const apiError = error.error as BookingApiError | undefined;

    if (apiError?.errorCode === 'booking_slot_unavailable') {
      return 'confirmation.errorMessage';
    }

    if (apiError?.errorCode === 'customer_has_active_booking') {
      return 'confirmation.activeBookingErrorMessage';
    }

    if (apiError?.errorCode === 'validation_error' || apiError?.errorCode === 'invalid_booking_request') {
      return 'confirmation.validationErrorMessage';
    }

    if (error.status === 401 || error.status === 403) {
      return 'auth.sessionExpired';
    }

    return 'confirmation.genericErrorMessage';
  }

  login(): void {
    void this.router.navigateByUrl('/login');
  }

  register(): void {
    void this.router.navigateByUrl('/register');
  }

  private generateIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }

    return `booking-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

}
