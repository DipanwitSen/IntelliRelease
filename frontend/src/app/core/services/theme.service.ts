import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

export type ThemePreference = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

const STORAGE_KEY = 'intellirelease.theme';

/**
 * Owns the light/dark decision for the whole app.
 *
 * The stored value is a *preference* (`system` included), while `resolved`
 * is the theme actually in effect. Keeping those separate is what lets the
 * toggle show "following your system" honestly instead of silently pinning
 * whichever theme the OS happened to be using at first paint.
 *
 * Only an explicit light/dark choice writes `data-theme` onto <html>. Under
 * `system` the attribute is removed entirely, handing control back to the
 * `prefers-color-scheme` block in _tokens.css.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly systemPrefersDark = signal(false);

  readonly preference = signal<ThemePreference>(this.readStoredPreference());

  readonly resolved = computed<ResolvedTheme>(() => {
    const preference = this.preference();
    if (preference !== 'system') {
      return preference;
    }
    return this.systemPrefersDark() ? 'dark' : 'light';
  });

  readonly isDark = computed(() => this.resolved() === 'dark');

  constructor() {
    const media = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
    if (media) {
      this.systemPrefersDark.set(media.matches);
      media.addEventListener('change', (event) => this.systemPrefersDark.set(event.matches));
    }

    effect(() => this.apply(this.preference()));
  }

  set(preference: ThemePreference): void {
    this.preference.set(preference);
    try {
      this.document.defaultView?.localStorage?.setItem(STORAGE_KEY, preference);
    } catch {
      // Private browsing or a locked-down profile. The theme still applies for
      // this session; only persistence is lost, which is not worth an error.
    }
  }

  /** Cycles light -> dark -> system, which is what the topbar button does. */
  cycle(): void {
    const order: readonly ThemePreference[] = ['light', 'dark', 'system'];
    const next = order[(order.indexOf(this.preference()) + 1) % order.length];
    this.set(next);
  }

  private apply(preference: ThemePreference): void {
    const root = this.document.documentElement;
    if (preference === 'system') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', preference);
    }
  }

  private readStoredPreference(): ThemePreference {
    try {
      const stored = this.document.defaultView?.localStorage?.getItem(STORAGE_KEY);
      if (stored === 'light' || stored === 'dark' || stored === 'system') {
        return stored;
      }
    } catch {
      // Ignored — fall through to the default.
    }
    return 'system';
  }
}
