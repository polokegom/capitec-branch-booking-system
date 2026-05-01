import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../services/auth.service';
import { apiErrorMessage } from '../utility/api-error.utils';

function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  if (!password || !confirmPassword) return null;
  return password === confirmPassword ? null : { passwordsMismatch: true };
}

@Component({
  selector: 'app-register-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './register-page.component.html',
  styleUrl: './register-page.component.scss'
})
export class RegisterPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly translocoService = inject(TranslocoService);

  readonly form = this.formBuilder.nonNullable.group(
    {
      firstName: ['', [Validators.required, Validators.maxLength(80)]],
      lastName: ['', [Validators.required, Validators.maxLength(80)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]]
    },
    { validators: passwordsMatchValidator }
  );

  readonly submitting = signal(false);
  readonly resendingVerification = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly verificationEmail = signal<string | null>(null);

  async submit(): Promise<void> {
    if (this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.verificationEmail.set(null);
    const { firstName, lastName, email, password } = this.form.getRawValue();
    try {
      const registrationResult = await this.authService.register({
        firstName,
        lastName,
        email,
        password
      });
      if (this.authService.authenticated()) {
        await this.router.navigateByUrl('/book');
        return;
      }
      this.form.reset();
      if (registrationResult.verificationRequired) {
        this.verificationEmail.set(email);
      }
      this.successMessage.set(registrationResult.message ?? this.translate('register.successVerification'));
    } catch (error: unknown) {
      this.errorMessage.set(apiErrorMessage(error, this.translate('register.errorDefault')));
    } finally {
      this.submitting.set(false);
    }
  }

  async resendVerificationEmail(): Promise<void> {
    const email = this.verificationEmail();
    if (!email || this.resendingVerification()) return;
    this.resendingVerification.set(true);
    this.errorMessage.set(null);
    try {
      const verificationEmailResult = await this.authService.resendVerificationEmail(email);
      this.successMessage.set(verificationEmailResult.message ?? this.translate('register.verificationSentDefault'));
    } catch (error: unknown) {
      this.errorMessage.set(apiErrorMessage(error, this.translate('auth.resendVerificationError')));
    } finally {
      this.resendingVerification.set(false);
    }
  }

  private translate(key: string): string {
    return this.translocoService.translate(key);
  }

}
