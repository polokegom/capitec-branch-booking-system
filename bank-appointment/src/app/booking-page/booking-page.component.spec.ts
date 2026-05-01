import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { BookingApiService, BookingBranch, CreateBookingPayload, MyBooking, PaginationResponse } from '../services/booking-api.service';
import { BookingPageComponent } from './booking-page.component';

describe('BookingPageComponent', () => {
  let fixture: ComponentFixture<BookingPageComponent>;
  let component: BookingPageComponent;
  let bookingApiService: jest.Mocked<Pick<BookingApiService, 'searchBranches' | 'getAvailability' | 'createBooking' | 'listMyBookings'>>;
  let router: Router;

  const authServiceMock = {
    authenticated: signal(true),
    displayName: signal('Repro Customer'),
    email: signal('repro.customer@example.com'),
    roles: signal<string[]>([])
  };

  const emptyBranchPagination = (): PaginationResponse<BookingBranch> => ({
    items: [],
    total: 0,
    startIndex: 0,
    endIndex: 0,
    hasMore: false
  });

  const emptyBookingPagination = (): PaginationResponse<MyBooking> => ({
    items: [],
    total: 0,
    startIndex: 0,
    endIndex: 0,
    hasMore: false
  });

  beforeEach(async () => {
    bookingApiService = {
      searchBranches: jest.fn().mockReturnValue(of(emptyBranchPagination())),
      getAvailability: jest.fn().mockReturnValue(of([])),
      createBooking: jest.fn(),
      listMyBookings: jest.fn().mockResolvedValue(emptyBookingPagination())
    };

    await TestBed.configureTestingModule({
      imports: [
        BookingPageComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { availableLangs: ['en'], defaultLang: 'en' } })
      ],
      providers: [
        provideRouter([]),
        { provide: BookingApiService, useValue: bookingApiService },
        { provide: AuthService, useValue: authServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BookingPageComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    jest.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should start at step 1', () => {
    expect(component.currentStep()).toBe(1);
  });

  it('should use the view booking action for successful confirmations', () => {
    const bookingConfirmation = {
      success: true,
      messageKey: 'confirmation.successMessage',
      reference: 'BKG-123'
    };

    expect(component.confirmationActionLabelKey(bookingConfirmation)).toBe('confirmation.viewBooking');

    component.handleConfirmationAction(bookingConfirmation);

    expect(router.navigateByUrl).toHaveBeenCalledWith('/my-bookings');
  });

  it('should use the view booking action for active booking errors', () => {
    const bookingConfirmation = {
      success: false,
      messageKey: 'confirmation.activeBookingErrorMessage'
    };

    expect(component.confirmationActionLabelKey(bookingConfirmation)).toBe('confirmation.viewBooking');

    component.handleConfirmationAction(bookingConfirmation);

    expect(router.navigateByUrl).toHaveBeenCalledWith('/my-bookings');
  });

  it('should retry the selected timeslot for non-active-booking errors', () => {
    const bookingConfirmation = {
      success: false,
      messageKey: 'confirmation.errorMessage'
    };
    const retryFromTimeslotSpy = jest.spyOn(component, 'retryFromTimeslot');

    expect(component.confirmationActionLabelKey(bookingConfirmation)).toBe('confirmation.retryTimeslot');

    component.handleConfirmationAction(bookingConfirmation);

    expect(retryFromTimeslotSpy).toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('should recover a successful booking when the submission fails after persistence', async () => {
    const payload: CreateBookingPayload = {
      branchId: 'branch-1',
      startDateTime: '2026-05-04T08:00:00Z',
      endDateTime: '2026-05-04T08:30:00Z',
      customerName: 'Repro Customer',
      customerEmail: 'repro.customer@example.com',
      preferredLanguage: 'en'
    };
    const recoveredBooking: MyBooking = {
      id: 'booking-1',
      bookingReference: 'BKG-RECOVERED',
      branchId: payload.branchId,
      branchName: 'Capitec Bellville',
      branchCity: 'Cape Town',
      branchCountry: 'South Africa',
      startDateTime: payload.startDateTime,
      endDateTime: payload.endDateTime,
      customerName: payload.customerName,
      customerEmail: payload.customerEmail,
      preferredLanguage: payload.preferredLanguage,
      status: 'CONFIRMED',
      createdAt: '2026-05-01T18:09:30.569557451Z'
    };

    bookingApiService.createBooking.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    bookingApiService.listMyBookings.mockResolvedValue({
      ...emptyBookingPagination(),
      items: [recoveredBooking],
      total: 1,
      endIndex: 1
    });

    await (component as any).createBooking(payload, 'idempotency-key');

    expect(component.confirmation()).toEqual(expect.objectContaining({
      success: true,
      messageKey: 'confirmation.successMessage',
      reference: recoveredBooking.bookingReference
    }));
  });
});
