import { Component, HostListener, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    RouterOutlet,
    TranslocoPipe,
    MatButtonModule,
    MatIconModule,
    MatMenuModule
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  encapsulation: ViewEncapsulation.None
})
export class AppComponent implements OnInit {
  readonly title = 'capitec-booking';
  readonly languageOptions = [
    { code: 'en', short: 'EN', label: 'English', nativeLabel: 'English' },
    { code: 'af', short: 'AF', label: 'Afrikaans', nativeLabel: 'Afrikaans' },
    { code: 'tn', short: 'TN', label: 'Setswana', nativeLabel: 'Setswana' },
    { code: 'nso', short: 'NSO', label: 'Sepedi', nativeLabel: 'Sepedi' },
    { code: 'zu', short: 'ZU', label: 'Zulu', nativeLabel: 'isiZulu' },
    { code: 'xh', short: 'XH', label: 'Xhosa', nativeLabel: 'isiXhosa' }
  ];
  mobileNavOpen = false;

  private readonly router = inject(Router);

  constructor(
    private readonly translocoService: TranslocoService,
    public readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    void this.authService.init();
  }

  get activeLang(): string {
    return this.translocoService.getActiveLang();
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    if (window.innerWidth > 860) {
      this.closeMobileNav();
    }
  }

  changeLanguage(lang: string): void {
    this.translocoService.setActiveLang(lang);
    this.closeMobileNav();
  }

  activeLanguageOption() {
    return this.languageOptions.find((languageOption) => languageOption.code === this.activeLang) ?? this.languageOptions[0];
  }

  toggleMobileNav(): void {
    this.mobileNavOpen = !this.mobileNavOpen;
  }

  closeMobileNav(): void {
    this.mobileNavOpen = false;
  }

  login(): void {
    this.closeMobileNav();
    void this.router.navigateByUrl('/login');
  }

  register(): void {
    this.closeMobileNav();
    void this.router.navigateByUrl('/register');
  }

  logout(): void {
    this.closeMobileNav();
    void this.authService.logout().then(() => this.router.navigateByUrl('/'));
  }
}
