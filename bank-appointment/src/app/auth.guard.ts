import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './services/auth.service';

export const requireAuthGuard: CanActivateFn = async () => {
  const { authService, router } = await resolveAuthContext();

  if (authService.authenticated()) {
    return true;
  }

  return router.createUrlTree(['/login']);
};

export const redirectIfAuthGuard: CanActivateFn = async () => {
  const { authService, router } = await resolveAuthContext();

  if (!authService.authenticated()) {
    return true;
  }
  return router.createUrlTree([landingRoute(authService)]);
};

export const requireOwnerGuard: CanActivateFn = async () => {
  const { authService, router } = await resolveAuthContext();

  if (!authService.authenticated()) {
    return router.createUrlTree(['/login']);
  }

  if (!authService.isOwner()) {
    return router.createUrlTree([authService.isAdmin() ? '/admin/bookings' : '/book']);
  }

  return true;
};

export const requireStaffGuard: CanActivateFn = async () => {
  const { authService, router } = await resolveAuthContext();

  if (!authService.authenticated()) {
    return router.createUrlTree(['/login']);
  }

  if (!authService.isStaff()) {
    return router.createUrlTree(['/book']);
  }

  return true;
};

export const requireCustomerGuard: CanActivateFn = async () => {
  const { authService, router } = await resolveAuthContext();

  if (!authService.authenticated()) {
    return router.createUrlTree(['/login']);
  }
  if (authService.isStaff()) {
    return router.createUrlTree([landingRoute(authService)]);
  }
  return true;
};

async function resolveAuthContext(): Promise<{ authService: AuthService; router: Router }> {
  const authService = inject(AuthService);
  const router = inject(Router);
  await authService.init();
  return { authService, router };
}

function landingRoute(authService: AuthService): string {
  if (authService.isOwner()) {
    return '/admin/branches';
  }
  if (authService.isAdmin()) {
    return '/admin/bookings';
  }
  return '/book';
}
