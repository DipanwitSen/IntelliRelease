import { ChangeDetectionStrategy, Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { HealthStatus } from '../../core/models/common';
import { ApiService } from '../../core/services/api.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ToastHostComponent } from '../../shared/components/toast-host/toast-host.component';
import { CommandPaletteComponent } from '../command-palette/command-palette.component';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';

const SIDEBAR_KEY = 'intellirelease.sidebar.collapsed';

/**
 * The application frame: sidebar, topbar, routed content, overlays.
 *
 * Layout is a CSS grid with named areas rather than nested flex wrappers, so
 * the sidebar can be `position: sticky` at full height while the content
 * column scrolls independently — and collapsing the sidebar is a single
 * grid-template change instead of a cascade of width overrides.
 */
@Component({
  selector: 'ir-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet, SidebarComponent, TopbarComponent, CommandPaletteComponent,
    ToastHostComponent, IconComponent,
  ],
  template: `
    <a class="skip-link" href="#main-content">Skip to content</a>

    <div class="shell" [class.sidebar-collapsed]="sidebarCollapsed()">
      <ir-sidebar
        [collapsed]="sidebarCollapsed()"
        [mobileOpen]="mobileNavOpen()"
        (toggleCollapsed)="toggleSidebar()"
        (navigated)="mobileNavOpen.set(false)"
      />

      @if (mobileNavOpen()) {
        <div class="mobile-scrim" (click)="mobileNavOpen.set(false)" role="presentation"></div>
      }

      <ir-topbar
        [backendStatus]="backendStatus()"
        [aiAvailable]="aiAvailable()"
        [aiFallback]="aiFallback()"
        [tenantName]="tenantName()"
        (openPalette)="paletteOpen.set(true)"
        (openMobileNav)="mobileNavOpen.set(true)"
      />

      <main class="content" id="main-content" tabindex="-1">
        @if (backendStatus() === 'UNHEALTHY') {
          <div class="offline-banner" role="status">
            <ir-icon name="alert-triangle" [size]="16" />
            <div>
              <strong>Backend unreachable.</strong>
              Spring Boot orchestrates every read in this product, so nothing on screen will refresh
              until it is back. Start the backend on
              <code class="code-inline">:8080</code> and this banner will clear itself.
            </div>
            <button type="button" class="btn btn-sm btn-secondary" (click)="checkHealth()">
              <ir-icon name="refresh-cw" [size]="14" />
              Retry
            </button>
          </div>
        }

        <router-outlet />
      </main>
    </div>

    <ir-command-palette [(open)]="paletteOpen" />
    <ir-toast-host />
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
      }

      .shell {
        display: grid;
        grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
        grid-template-rows: var(--topbar-height) minmax(0, 1fr);
        grid-template-areas:
          'sidebar topbar'
          'sidebar content';
        min-height: 100vh;
        transition: grid-template-columns var(--duration-normal) var(--ease-out);
      }

      .shell.sidebar-collapsed {
        grid-template-columns: var(--sidebar-width-collapsed) minmax(0, 1fr);
      }

      .content {
        grid-area: content;
        min-width: 0;
      }

      .content:focus-visible {
        outline: none;
      }

      .mobile-scrim {
        position: fixed;
        inset: 0;
        z-index: calc(var(--z-sidebar) - 1);
        background: var(--overlay-scrim);
        animation: fade-in var(--duration-fast) var(--ease-out);
      }

      .offline-banner {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        margin: var(--space-4) var(--space-6) 0;
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-md);
        border: 1px solid var(--danger-fg);
        background: var(--danger-bg);
        color: var(--danger-fg);
        font-size: var(--text-md);
      }

      .offline-banner div { flex: 1; }
      .offline-banner strong { color: inherit; }

      @media (max-width: 960px) {
        .shell,
        .shell.sidebar-collapsed {
          grid-template-columns: minmax(0, 1fr);
          grid-template-areas:
            'topbar'
            'content';
        }

        .offline-banner {
          margin-inline: var(--space-3);
          flex-wrap: wrap;
        }
      }
    `,
  ],
})
export class ShellComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly sidebarCollapsed = signal(this.readStoredCollapsed());
  protected readonly mobileNavOpen = signal(false);
  protected readonly paletteOpen = signal(false);

  protected readonly backendStatus = signal<HealthStatus>('UNKNOWN');
  protected readonly aiAvailable = signal<boolean | null>(null);
  protected readonly aiFallback = signal(false);
  protected readonly tenantName = signal('IntelliRelease');

  constructor() {
    // Move focus to the content region on every navigation. Without this a
    // keyboard user who followed a sidebar link stays parked in the sidebar
    // and has to tab through it again to reach the page they just opened.
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => {
        this.mobileNavOpen.set(false);
        document.getElementById('main-content')?.focus({ preventScroll: true });
        window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
      });
  }

  ngOnInit(): void {
    this.checkHealth();
    this.checkAi();
  }

  /** Ctrl/Cmd-K anywhere opens the palette; Escape closes the mobile drawer. */
  @HostListener('document:keydown', ['$event'])
  protected onKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.paletteOpen.update((open) => !open);
      return;
    }
    if (event.key === 'Escape' && this.mobileNavOpen()) {
      this.mobileNavOpen.set(false);
    }
  }

  protected toggleSidebar(): void {
    const next = !this.sidebarCollapsed();
    this.sidebarCollapsed.set(next);
    try {
      localStorage.setItem(SIDEBAR_KEY, String(next));
    } catch {
      // Persistence is a nicety; the toggle still works for this session.
    }
  }

  protected checkHealth(): void {
    this.backendStatus.set('UNKNOWN');
    this.api.health().subscribe({
      next: (response) => this.backendStatus.set(response.status === 'UP' ? 'HEALTHY' : 'DEGRADED'),
      error: () => this.backendStatus.set('UNHEALTHY'),
    });
  }

  private checkAi(): void {
    this.api.aiStatus().subscribe({
      next: (status) => {
        this.aiAvailable.set(status.available);
        this.aiFallback.set(status.deterministicFallback);
      },
      // A missing AI status endpoint is not an app-level failure. The product
      // is designed to work without the model at all, so we simply report it.
      error: () => {
        this.aiAvailable.set(false);
        this.aiFallback.set(true);
      },
    });
  }

  private readStoredCollapsed(): boolean {
    try {
      return localStorage.getItem(SIDEBAR_KEY) === 'true';
    } catch {
      return false;
    }
  }
}
