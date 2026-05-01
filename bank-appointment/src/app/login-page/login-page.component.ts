import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../services/auth.service';
import { apiErrorMessage } from '../utility/api-error.utils';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.scss'
})
export class LoginPageComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly translocoService = inject(TranslocoService);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  readonly submitting = signal(false);
  readonly resendingVerification = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);
  readonly verificationIssue = signal(false);

  ngOnInit(): void {
    const verificationStatus = this.activatedRoute.snapshot.queryParamMap.get('emailVerification');
    if (verificationStatus) {
      this.showVerificationRedirectMessage(verificationStatus);
    }
  }

  async submit(): Promise<void> {
    if (this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.verificationIssue.set(false);
    try {
      await this.authService.login(this.form.getRawValue());
      await this.router.navigateByUrl(
        this.authService.isOwner()
          ? '/admin/branches'
          : this.authService.isAdmin()
            ? '/admin/bookings'
            : '/book'
      );
    } catch (error: unknown) {
      const message = apiErrorMessage(error, this.translate('login.errorDefault'));
      this.errorMessage.set(message);
      this.verificationIssue.set(this.isVerificationMessage(message) && this.form.controls.email.valid);
    } finally {
      this.submitting.set(false);
    }
  }

  async resendVerificationEmail(): Promise<void> {
    if (this.resendingVerification()) return;
    if (this.form.controls.email.invalid) {
      this.form.controls.email.markAsTouched();
      return;
    }
    this.resendingVerification.set(true);
    this.noticeMessage.set(null);
    this.errorMessage.set(null);
    try {
      const verificationEmailResult = await this.authService.resendVerificationEmail(this.form.getRawValue().email);
      this.noticeMessage.set(verificationEmailResult.message ?? this.translate('login.verificationSentDefault'));
    } catch (error: unknown) {
      this.errorMessage.set(apiErrorMessage(error, this.translate('auth.resendVerificationError')));
    } finally {
      this.resendingVerification.set(false);
    }
  }

  private isVerificationMessage(message: string): boolean {
    return message.toLowerCase().includes('verify your email');
  }

  private showVerificationRedirectMessage(status: string): void {
    if (status === 'verified') {
      this.noticeMessage.set(this.translate('login.emailVerified'));
      return;
    }

    const messageKey = status === 'expired'
      ? 'login.emailVerificationExpired'
      : 'login.emailVerificationUnavailable';
    this.errorMessage.set(this.translate(messageKey));
  }

  private translate(key: string): string {
    return this.translocoService.translate(key);
  }

}
