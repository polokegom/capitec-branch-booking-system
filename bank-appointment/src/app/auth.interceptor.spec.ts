import { HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { authTokenInterceptor } from './auth.interceptor';
import { AuthService } from './services/auth.service';

describe('authTokenInterceptor', () => {
  let authService: { getAccessToken: jest.Mock };

  beforeEach(() => {
    authService = {
      getAccessToken: jest.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService }
      ]
    });
  });

  it('adds the bearer token to protected API requests', async () => {
    authService.getAccessToken.mockResolvedValue('access-token');
    const handledRequests: HttpRequest<unknown>[] = [];
    const request = new HttpRequest('GET', '/api/v1/bookings/ABC123');
    const next: HttpHandlerFn = (nextRequest) => {
      handledRequests.push(nextRequest);
      return of(new HttpResponse({ status: 200 }));
    };

    await TestBed.runInInjectionContext(() => firstValueFrom(authTokenInterceptor(request, next)));

    expect(handledRequests[0].headers.get('Authorization')).toBe('Bearer access-token');
  });

  it('adds the bearer token to branches and availability requests', async () => {
    authService.getAccessToken.mockResolvedValue('access-token');
    const handledRequests: HttpRequest<unknown>[] = [];
    const next: HttpHandlerFn = (nextRequest) => {
      handledRequests.push(nextRequest);
      return of(new HttpResponse({ status: 200 }));
    };

    await TestBed.runInInjectionContext(() =>
      firstValueFrom(authTokenInterceptor(new HttpRequest('GET', '/api/v1/branches'), next))
    );

    await TestBed.runInInjectionContext(() =>
      firstValueFrom(authTokenInterceptor(new HttpRequest('GET', '/api/v1/availability'), next))
    );

    expect(handledRequests[0].headers.get('Authorization')).toBe('Bearer access-token');
    expect(handledRequests[1].headers.get('Authorization')).toBe('Bearer access-token');
  });

  it('does not request a token for public requests', async () => {
    const handledRequests: HttpRequest<unknown>[] = [];
    const request = new HttpRequest('GET', '/assets/i18n/en.json');
    const next: HttpHandlerFn = (nextRequest) => {
      handledRequests.push(nextRequest);
      return of(new HttpResponse({ status: 200 }));
    };

    await TestBed.runInInjectionContext(() => firstValueFrom(authTokenInterceptor(request, next)));

    expect(authService.getAccessToken).not.toHaveBeenCalled();
    expect(handledRequests[0].headers.has('Authorization')).toBe(false);
  });
});
