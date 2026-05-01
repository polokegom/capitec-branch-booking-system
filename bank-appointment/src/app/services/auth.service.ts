import { Injectable, computed, inject, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

const STORAGE_KEY = 'capitec.booking.auth';
const AUTH_API_URL = '/api/v1/auth';

interface ApiMessageResponse {
  message?: string;
}

interface AuthResponseBody extends ApiMessageResponse {
  token?: string;
  refreshToken?: string;
  profile?: BookingProfile;
}

interface StoredAuthState {
  token: string;
  refreshToken?: string;
  profile: BookingProfile;
}

interface AuthHttpResponse<TBody> {
  ok: boolean;
  body: TBody | null;
}

export interface BookingProfile {
  email?: string;
  firstName?: string;
  lastName?: string;
  name?: string;
  roles?: string[];
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
}

export interface RegisterResult {
  verificationRequired?: boolean;
  message?: string;
}

export interface ResendVerificationResult {
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly translocoService = inject(TranslocoService);
  private readonly tokenState = signal<string | null>(null);
  private readonly profileState = signal<BookingProfile | null>(null);
  private readonly initializedState = signal(false);

  readonly authenticated = computed(() => this.tokenState() !== null);
  readonly displayName = computed(() => {
    const profile = this.profileState();
    if (!profile) return null;
    if (profile.name) return profile.name;
    const fullName = [profile.firstName, profile.lastName].filter(Boolean).join(' ').trim();
    return fullName || profile.email || null;
  });
  readonly email = computed(() => this.profileState()?.email ?? null);
  readonly roles = computed(() => this.profileState()?.roles ?? []);
  readonly isOwner = computed(() => this.roles().includes('owner'));
  readonly isAdmin = computed(() => this.roles().includes('admin'));
  readonly isStaff = computed(() => this.isOwner() || this.isAdmin());

  async init(): Promise<void> {
    if (this.initializedState()) return;
    this.applyStoredAuthState();
    this.initializedState.set(true);
  }

  private applyStoredAuthState(): void {
    const raw = this.readStoredAuthState();
    if (!raw) {
      this.tokenState.set(null);
      this.profileState.set(null);
      return;
    }
    try {
      const stored: StoredAuthState = JSON.parse(raw);
      if (stored.token && !this.isExpired(stored.token)) {
        this.tokenState.set(stored.token);
        this.profileState.set(stored.profile);
        return;
      }
      this.clearStoredAuthState();
    } catch {
      this.clearStoredAuthState();
    }
  }

  async login(payload: LoginPayload): Promise<void> {
    const response = await this.postAuth<AuthResponseBody>('/sessions', payload);
    this.handleAuthenticatedResponse(response);
  }

  async register(payload: RegisterPayload): Promise<RegisterResult> {
    const response = await this.postAuth<AuthResponseBody & RegisterResult>('/users', payload);
    return this.handleRegisterResponse(response);
  }

  async resendVerificationEmail(email: string): Promise<ResendVerificationResult> {
    const response = await this.postAuth<ApiMessageResponse>('/email-verifications', { email });
    if (!response.ok) {
      const message = response.body?.message || this.translate('auth.resendVerificationError');
      throw new Error(message);
    }
    return { message: response.body?.message };
  }

  async logout(): Promise<void> {
    this.clearStoredAuthState();
    this.tokenState.set(null);
    this.profileState.set(null);
  }

  async getAccessToken(): Promise<string | null> {
    await this.init();
    const token = this.tokenState();
    if (!token) return null;
    if (this.isExpired(token)) {
      await this.logout();
      return null;
    }
    return token;
  }

  private async postAuth<TBody>(path: string, payload: unknown): Promise<AuthHttpResponse<TBody>> {
    try {
      const response = await fetch(`${AUTH_API_URL}${path}`, {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        cache: 'no-store',
        credentials: 'same-origin',
        body: JSON.stringify(payload)
      });
      return {
        ok: response.ok,
        body: await this.readJson<TBody>(response)
      };
    } catch {
      throw new Error(this.translate('auth.networkError'));
    }
  }

  private async readJson<TBody>(response: Response): Promise<TBody | null> {
    try {
      return await response.json() as TBody;
    } catch {
      return null;
    }
  }

  private handleAuthenticatedResponse(response: AuthHttpResponse<AuthResponseBody>): void {
    if (!response.ok) {
      const message = response.body?.message || this.translate('auth.authenticationFailed');
      throw new Error(message);
    }
    if (!response.body?.token) {
      throw new Error(this.translate('auth.responseMissingToken'));
    }
    const profile: BookingProfile = response.body.profile ?? {};
    const stored: StoredAuthState = {
      token: response.body.token,
      refreshToken: response.body.refreshToken,
      profile
    };
    this.storeAuthState(stored);
    this.tokenState.set(stored.token);
    this.profileState.set(profile);
    this.initializedState.set(true);
  }

  private handleRegisterResponse(response: AuthHttpResponse<AuthResponseBody & RegisterResult>): RegisterResult {
    if (!response.ok) {
      const message = response.body?.message || this.translate('register.errorDefault');
      throw new Error(message);
    }
    if (response.body?.token) {
      this.handleAuthenticatedResponse({ ok: true, body: response.body });
      return {};
    }
    return {
      verificationRequired: Boolean(response.body?.verificationRequired),
      message: response.body?.message
    };
  }

  private isExpired(token: string): boolean {
    try {
      const [, payload] = token.split('.');
      const decodedPayload = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
      if (!this.hasExpiry(decodedPayload)) return false;
      return decodedPayload.exp * 1000 <= Date.now();
    } catch {
      return true;
    }
  }

  private translate(key: string): string {
    return this.translocoService.translate(key);
  }

  private hasExpiry(value: unknown): value is { exp: number } {
    return typeof value === 'object' && value !== null && 'exp' in value && typeof value.exp === 'number';
  }

  private readStoredAuthState(): string | null {
    return sessionStorage.getItem(STORAGE_KEY);
  }

  private storeAuthState(state: StoredAuthState): void {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  private clearStoredAuthState(): void {
    sessionStorage.removeItem(STORAGE_KEY);
  }
}
