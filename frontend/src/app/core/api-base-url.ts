import { InjectionToken } from '@angular/core';

/**
 * Where Spring Boot lives.
 *
 * Injected rather than hard-coded so the same bundle can run against a dev
 * backend on :8080 and against a reverse-proxied deployment where the API is
 * same-origin. The default resolves at runtime: same-origin in production,
 * localhost:8080 when the Angular dev server is serving on :4200.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => {
    const origin = typeof window === 'undefined' ? '' : window.location.origin;
    // `ng serve` ports — anything else is assumed to be served by the backend
    // itself or behind a proxy that already routes /api to it.
    const devPorts = ['4200', '4201'];
    if (typeof window !== 'undefined' && devPorts.includes(window.location.port)) {
      return 'http://localhost:8080';
    }
    return origin;
  },
});
