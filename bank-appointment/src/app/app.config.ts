import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, isDevMode } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideTransloco, translocoConfig } from '@jsverse/transloco';
import { authTokenInterceptor } from './auth.interceptor';

import { routes } from './app.routes';
import { TranslocoHttpLoader } from './transloco/transloco-loader';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(withInterceptors([authTokenInterceptor])),
    provideTransloco({
      config: translocoConfig({
        availableLangs: ['en', 'af', 'tn', 'nso', 'zu', 'xh'],
        defaultLang: 'en',
        fallbackLang: 'en',
        reRenderOnLangChange: true,
        prodMode: !isDevMode()
      }),
      loader: TranslocoHttpLoader
    })
  ]
};
