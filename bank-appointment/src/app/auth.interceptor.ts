import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AuthService } from './services/auth.service';

const PROTECTED_API_PREFIXES = [
  '/api/v1/bookings',
  '/api/v1/customer',
  '/api/v1/admin',
  '/api/v1/branches',
  '/api/v1/availability'
];

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  if (!PROTECTED_API_PREFIXES.some((prefix) => request.url.startsWith(prefix))) {
    return next(request);
  }

  const authService = inject(AuthService);

  return from(authService.getAccessToken()).pipe(
    switchMap((token) => {
      if (!token) {
        return next(request);
      }

      return next(
        request.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`
          }
        })
      );
    })
  );
};
