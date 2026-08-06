import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { InMemoryScrollingFeature, provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';

import { authInterceptor } from './interceptors/auth.interceptor';
import { routes } from './app.routes';

/**
 * `withComponentInputBinding` is what lets a detail page declare
 * `readonly id = input.required<string>()` and receive the `:id` route param
 * directly, instead of every page hand-rolling an ActivatedRoute subscription.
 *
 * Scroll restoration is enabled so going back to a long, scrolled list returns
 * you to where you were rather than to the top.
 */
const scrolling: InMemoryScrollingFeature = withInMemoryScrolling({
  scrollPositionRestoration: 'enabled',
  anchorScrolling: 'enabled',
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding(), scrolling),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
  ],
};
