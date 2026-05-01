import { Routes } from '@angular/router';
import { redirectIfAuthGuard, requireCustomerGuard, requireOwnerGuard, requireStaffGuard } from './auth.guard';

export const routes: Routes = [
	{
		path: '',
		pathMatch: 'full',
		loadComponent: () => import('./home-page/home-page.component').then((module) => module.HomePageComponent),
		canActivate: [redirectIfAuthGuard]
	},
	{
		path: 'login',
		loadComponent: () => import('./login-page/login-page.component').then((module) => module.LoginPageComponent),
		canActivate: [redirectIfAuthGuard]
	},
	{
		path: 'register',
		loadComponent: () => import('./register-page/register-page.component').then((module) => module.RegisterPageComponent),
		canActivate: [redirectIfAuthGuard]
	},
	{
		path: 'book',
		loadComponent: () => import('./booking-page/booking-page.component').then((module) => module.BookingPageComponent),
		canActivate: [requireCustomerGuard]
	},
	{
		path: 'my-bookings',
		loadComponent: () => import('./customer/customer-bookings-page.component').then((module) => module.CustomerBookingsPageComponent),
		canActivate: [requireCustomerGuard]
	},
	{
		path: 'admin/branches',
		loadComponent: () => import('./admin/admin-branches-page/admin-branches-page.component').then((module) => module.AdminBranchesPageComponent),
		canActivate: [requireOwnerGuard]
	},
	{
		path: 'admin/bookings',
		loadComponent: () => import('./admin/admin-bookings-page/admin-bookings-page.component').then((module) => module.AdminBookingsPageComponent),
		canActivate: [requireStaffGuard]
	},
	{ path: '**', redirectTo: '' }
];
