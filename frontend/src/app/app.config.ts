import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es-MX';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

registerLocaleData(localeEs);

// ── Paleta Instituto Nevadi ────────────────────────────────────────────────────
// Base: #D02030 (rojo institucional)
const NevadiPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50:  '#fef2f3',
      100: '#fde0e2',
      200: '#fba7ac',
      300: '#f57580',
      400: '#ec4558',
      500: '#D02030',   // ← Nevadi Red
      600: '#b01c28',
      700: '#8c1620',
      800: '#681019',
      900: '#440b11',
      950: '#220508',
    },
  },
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: NevadiPreset,
        options: { prefix: 'p', darkModeSelector: '.dark-mode', cssLayer: false },
      },
      ripple: true,
    }),
    { provide: LOCALE_ID, useValue: 'es-MX' },
  ],
};
