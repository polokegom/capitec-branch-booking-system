import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { HomePageComponent } from './home-page.component';
import { AuthService } from '../services/auth.service';

describe('HomePageComponent', () => {
  let fixture: ComponentFixture<HomePageComponent>;
  let component: HomePageComponent;

  beforeEach(async () => {
    const authStub: Partial<AuthService> = {
      authenticated: signal(false)
    };

    await TestBed.configureTestingModule({
      imports: [
        HomePageComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { availableLangs: ['en'], defaultLang: 'en' } })
      ],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authStub }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HomePageComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('navigates to /login on login()', () => {
    const router = TestBed.inject(Router);
    const spy = jest.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    component.login();
    expect(spy).toHaveBeenCalledWith('/login');
  });
});
