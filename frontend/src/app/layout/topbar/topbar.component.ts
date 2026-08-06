import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';

import { HealthStatus } from '../../core/models/common';
import { ThemeService } from '../../core/services/theme.service';
import { AuthService } from '../../services/auth.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { BreadcrumbsComponent } from '../breadcrumbs/breadcrumbs.component';

/**
 * The top bar: where you are, how the platform is doing, and who you are.
 *
 * The two status pills are deliberately always visible. This product's whole
 * argument is that a human should know when they are looking at deterministic
 * output versus a model's explanation — so "AI unavailable, falling back to
 * deterministic narration" has to be ambient, not buried in a settings page.
 */
@Component({
  selector: 'ir-topbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, BreadcrumbsComponent],
  template: `
    <header class="topbar">
      <button
        type="button"
        class="btn btn-ghost btn-icon btn-sm menu-button"
        (click)="openMobileNav.emit()"
        aria-label="Open navigation"
      >
        <ir-icon name="menu" [size]="18" />
      </button>

      <ir-breadcrumbs class="crumbs" />

      <div class="spacer"></div>

      <button type="button" class="search-trigger" (click)="openPalette.emit()" aria-label="Search — Ctrl K">
        <ir-icon name="search" [size]="15" />
        <span class="search-text">Search</span>
        <kbd class="kbd">{{ shortcutLabel }}</kbd>
      </button>

      <div class="status-pills">
        <span class="pill" [class]="'tone-' + backendTone()" [title]="backendTitle()">
          <span class="dot" [class.dot-pulse]="backendStatus() === 'UNKNOWN'"></span>
          <span class="pill-text">API</span>
        </span>

        <span class="pill" [class]="'tone-' + aiTone()" [title]="aiTitle()">
          <ir-icon name="sparkles" [size]="12" />
          <span class="pill-text">{{ aiLabel() }}</span>
        </span>
      </div>

      <button
        type="button"
        class="btn btn-ghost btn-icon btn-sm"
        (click)="theme.cycle()"
        [attr.aria-label]="themeLabel()"
        [title]="themeLabel()"
      >
        <ir-icon [name]="themeIcon()" [size]="17" />
      </button>

      <div class="user-menu">
        <button
          type="button"
          class="user-button"
          (click)="userMenuOpen.set(!userMenuOpen())"
          [attr.aria-expanded]="userMenuOpen()"
          aria-haspopup="menu"
        >
          <span class="avatar">{{ initials() }}</span>
          <ir-icon name="chevron-down" [size]="13" class="muted" />
        </button>

        @if (userMenuOpen()) {
          <div class="menu-scrim" (click)="userMenuOpen.set(false)" role="presentation"></div>
          <div class="menu" role="menu">
            <div class="menu-header">
              <div class="weight-semibold">{{ auth.username() ?? 'Signed out' }}</div>
              <div class="text-xs muted">{{ tenantName() }}</div>
            </div>
            <button type="button" class="menu-item" role="menuitem" (click)="signOut()">
              <ir-icon name="log-out" [size]="15" />
              Sign out
            </button>
          </div>
        }
      </div>
    </header>
  `,
  styles: [
    `
      .topbar {
        grid-area: topbar;
        position: sticky;
        top: 0;
        z-index: var(--z-topbar);
        display: flex;
        align-items: center;
        gap: var(--space-2);
        height: var(--topbar-height);
        padding-inline: var(--space-4);
        background: color-mix(in srgb, var(--surface) 88%, transparent);
        backdrop-filter: blur(10px);
        border-bottom: 1px solid var(--border);
      }

      .crumbs { min-width: 0; overflow: hidden; }
      .spacer { flex: 1 1 auto; }

      .menu-button { display: none; }

      /* ---- search ---- */
      .search-trigger {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        height: 30px;
        padding-inline: var(--space-3);
        border: 1px solid var(--border);
        border-radius: var(--radius-sm);
        background: var(--surface-sunken);
        color: var(--text-muted);
        font-size: var(--text-sm);
        transition: border-color var(--duration-fast) var(--ease-out),
                    color var(--duration-fast) var(--ease-out);
      }

      .search-trigger:hover {
        border-color: var(--border-strong);
        color: var(--text-secondary);
      }

      .kbd {
        display: inline-grid;
        place-items: center;
        height: 17px;
        padding-inline: 5px;
        border: 1px solid var(--border);
        border-radius: var(--radius-xs);
        background: var(--surface);
        font-size: var(--text-2xs);
      }

      /* ---- status ---- */
      .status-pills {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }

      .pill {
        display: inline-flex;
        align-items: center;
        gap: var(--space-1);
        height: 22px;
        padding-inline: var(--space-2);
        border-radius: var(--radius-pill);
        font-size: var(--text-2xs);
        font-weight: var(--weight-semibold);
        letter-spacing: 0.02em;
        white-space: nowrap;
      }

      /* ---- user ---- */
      .user-menu { position: relative; }

      .user-button {
        display: flex;
        align-items: center;
        gap: var(--space-1);
        padding: 2px;
        border-radius: var(--radius-pill);
      }

      .avatar {
        width: 27px;
        height: 27px;
        display: grid;
        place-items: center;
        border-radius: 50%;
        background: var(--accent-subtle-bg);
        color: var(--accent-subtle-fg);
        font-size: var(--text-2xs);
        font-weight: var(--weight-bold);
      }

      .menu-scrim { position: fixed; inset: 0; z-index: var(--z-drawer); }

      .menu {
        position: absolute;
        right: 0;
        top: calc(100% + 6px);
        min-width: 210px;
        z-index: calc(var(--z-drawer) + 1);
        background: var(--surface-raised);
        border: 1px solid var(--border);
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-lg);
        overflow: hidden;
        animation: fade-in var(--duration-fast) var(--ease-out);
      }

      .menu-header {
        padding: var(--space-3) var(--space-4);
        border-bottom: 1px solid var(--border-subtle);
      }

      .menu-item {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        width: 100%;
        padding: var(--space-2) var(--space-4);
        font-size: var(--text-md);
        color: var(--text-secondary);
        text-align: left;
      }

      .menu-item:hover { background: var(--surface-hover); color: var(--text); }

      @media (max-width: 960px) {
        .menu-button { display: inline-flex; }
      }

      @media (max-width: 720px) {
        .search-text { display: none; }
        .kbd { display: none; }
        .pill-text { display: none; }
        .pill { padding-inline: 6px; }
      }
    `,
  ],
})
export class TopbarComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly auth = inject(AuthService);

  readonly backendStatus = input<HealthStatus>('UNKNOWN');
  readonly aiAvailable = input<boolean | null>(null);
  readonly aiFallback = input(false);
  readonly tenantName = input('IntelliRelease');

  readonly openPalette = output<void>();
  readonly openMobileNav = output<void>();

  protected readonly userMenuOpen = signal(false);

  protected readonly shortcutLabel =
    typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform ?? '') ? '⌘K' : 'Ctrl K';

  protected readonly initials = computed(() => {
    const name = this.auth.username();
    if (!name) {
      return '—';
    }
    return name
      .split(/[\s._-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join('');
  });

  protected readonly backendTone = computed(() => {
    switch (this.backendStatus()) {
      case 'HEALTHY': return 'success';
      case 'DEGRADED': return 'warning';
      case 'UNHEALTHY': return 'danger';
      default: return 'neutral';
    }
  });

  protected readonly backendTitle = computed(() => {
    switch (this.backendStatus()) {
      case 'HEALTHY': return 'Backend reachable';
      case 'DEGRADED': return 'Backend reachable but reporting a degraded dependency';
      case 'UNHEALTHY': return 'Backend unreachable — data on screen may be stale';
      default: return 'Checking the backend…';
    }
  });

  protected readonly aiTone = computed(() => {
    if (this.aiAvailable() === null) return 'neutral';
    if (!this.aiAvailable()) return this.aiFallback() ? 'warning' : 'danger';
    return 'ai';
  });

  protected readonly aiLabel = computed(() => {
    if (this.aiAvailable() === null) return 'AI';
    return this.aiAvailable() ? 'AI' : 'Deterministic';
  });

  protected readonly aiTitle = computed(() => {
    if (this.aiAvailable() === null) {
      return 'Checking the AI service…';
    }
    if (this.aiAvailable()) {
      return 'AI explanation available. Scores and decisions remain deterministic.';
    }
    return this.aiFallback()
      ? 'AI service unavailable — narration is coming from the deterministic narrator. No analysis is lost; only the prose changes.'
      : 'AI service unavailable and no fallback configured.';
  });

  protected readonly themeIcon = computed(() => {
    switch (this.theme.preference()) {
      case 'light': return 'sun';
      case 'dark': return 'moon';
      default: return 'monitor';
    }
  });

  protected readonly themeLabel = computed(() => {
    switch (this.theme.preference()) {
      case 'light': return 'Theme: light — click for dark';
      case 'dark': return 'Theme: dark — click to follow system';
      default: return 'Theme: following system — click for light';
    }
  });

  protected signOut(): void {
    this.userMenuOpen.set(false);
    this.auth.logout();
  }
}
