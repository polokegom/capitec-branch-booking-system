import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { AuthService, LoginPayload, RegisterPayload } from './auth.service';

const STORAGE_KEY = 'capitec.booking.auth';

describe('AuthService', () => {
  let service: AuthService;
  let fetchMock: jest.MockedFunction<typeof fetch>;

  beforeEach(() => {
    sessionStorage.clear();
    fetchMock = jest.fn() as jest.MockedFunction<typeof fetch>;
    Object.defineProperty(globalThis, 'fetch', {
      value: fetchMock,
      configurable: true,
      writable: true
    });

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        {
          provide: TranslocoService,
          useValue: {
            translate: (key: string) => key
          }
        }
      ]
    });

    service = TestBed.inject(AuthService);
  });

  afterEach(() => {
    sessionStorage.clear();
    jest.restoreAllMocks();
  });

  it('stores the authenticated profile after a successful login', async () => {
    const token = createToken({ exp: futureExpiry() });
    const payload: LoginPayload = {
      email: 'client@example.com',
      password: 'SecurePassword1!'
    };

    fetchMock.mockResolvedValue(jsonResponse({
      token,
      refreshToken: 'refresh-token',
      profile: {
        email: 'client@example.com',
        firstName: 'Nandi',
        lastName: 'Mokoena',
        roles: ['customer']
      }
    }));

    await service.login(payload);

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/sessions', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(payload)
    }));
    expect(service.authenticated()).toBe(true);
    expect(service.displayName()).toBe('Nandi Mokoena');
    expect(service.email()).toBe('client@example.com');
    expect(JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '{}')).toMatchObject({
      token,
      refreshToken: 'refresh-token'
    });
  });

  it('uses the backend message when login fails', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ message: 'Invalid email or password.' }, 401));

    await expect(service.login({ email: 'client@example.com', password: 'wrong' }))
      .rejects.toThrow('Invalid email or password.');

    expect(service.authenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('returns the verification outcome after registration without a token', async () => {
    const payload: RegisterPayload = {
      email: 'client@example.com',
      firstName: 'Nandi',
      lastName: 'Mokoena',
      password: 'SecurePassword1!'
    };

    fetchMock.mockResolvedValue(jsonResponse({
      verificationRequired: true,
      message: 'Please verify your email address.'
    }, 202));

    await expect(service.register(payload)).resolves.toEqual({
      verificationRequired: true,
      message: 'Please verify your email address.'
    });
    expect(service.authenticated()).toBe(false);
  });

  it('clears an expired stored token before returning an access token', async () => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
      token: createToken({ exp: pastExpiry() }),
      profile: { email: 'expired@example.com' }
    }));

    await expect(service.getAccessToken()).resolves.toBeNull();

    expect(service.authenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    json: jest.fn().mockResolvedValue(body)
  } as unknown as Response;
}

function createToken(payload: Record<string, unknown>): string {
  return `${base64Url({ alg: 'none', typ: 'JWT' })}.${base64Url(payload)}.signature`;
}

function base64Url(value: unknown): string {
  return btoa(JSON.stringify(value))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

function futureExpiry(): number {
  return Math.floor(Date.now() / 1000) + 3600;
}

function pastExpiry(): number {
  return Math.floor(Date.now() / 1000) - 3600;
}
