import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../../core/services/api.service';
import { ThemeService } from '../../core/services/theme.service';
import { AuthService } from '../../services/auth.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Sign-in.
 *
 * Credentials are verified by actually calling the backend rather than being
 * accepted locally — otherwise the shell loads, every panel fails with 401,
 * and the user is left guessing which of fifteen requests was the real
 * problem. One clear failure here beats fifteen unclear ones there.
 */
@Component({
  selector: 'ir-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="login-page">
      <button
        type="button"
        class="btn btn-ghost btn-icon theme-toggle"
        (click)="theme.cycle()"
        aria-label="Toggle theme"
      >
        <ir-icon [name]="theme.isDark() ? 'moon' : 'sun'" [size]="17" />
      </button>

      <div class="login-panel">
        <aside class="login-brand">
          <div class="brand-row">
            <span class="brand-glyph">IR</span>
            <span class="brand-name">IntelliRelease</span>
          </div>

          <h1 class="brand-headline">
            Release intelligence that explains itself.
          </h1>

          <p class="brand-copy">
            Deterministic SAP Commerce and integration analysis first. AI explains what the
            rules found — it never scores a change, and it never approves a release.
          </p>

          <ul class="brand-points">
            <li>
              <ir-icon name="shield" [size]="15" />
              <span>Risk scores come from versioned rules, not a model</span>
            </li>
            <li>
              <ir-icon name="network" [size]="15" />
              <span>Interfaces, payloads and mappings mapped end to end</span>
            </li>
            <li>
              <ir-icon name="check-circle" [size]="15" />
              <span>Nothing reaches a customer without human approval</span>
            </li>
          </ul>
        </aside>

        <div class="login-form-side">
          <h2 class="form-title">Sign in</h2>
          <p class="form-subtitle">Use your IntelliRelease operator credentials.</p>

          <form (ngSubmit)="submit()">
            <label class="field">
              <span class="def-label">Username</span>
              <input
                name="username"
                [(ngModel)]="username"
                autocomplete="username"
                required
                [disabled]="busy()"
                autofocus
              />
            </label>

            <label class="field">
              <span class="def-label">Password</span>
              <input
                name="password"
                type="password"
                [(ngModel)]="password"
                autocomplete="current-password"
                required
                [disabled]="busy()"
              />
            </label>

            @if (error()) {
              <div class="callout tone-danger" role="alert">
                <ir-icon name="alert-circle" [size]="16" class="callout-icon" />
                <span>{{ error() }}</span>
              </div>
            }

            <button type="submit" class="btn btn-primary btn-lg full-width" [disabled]="busy() || !canSubmit()">
              @if (busy()) {
                <ir-icon name="refresh-cw" [size]="15" class="spin" />
                Verifying…
              } @else {
                Sign in
              }
            </button>
          </form>

          <p class="form-footnote">
            Authentication is enforced by the backend. This screen only collects credentials —
            it does not decide whether they are valid.
          </p>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .login-page {
        min-height: 100vh;
        display: grid;
        place-items: center;
        padding: var(--space-6);
        position: relative;
        background:
          radial-gradient(ellipse 80% 60% at 15% 0%, var(--accent-subtle-bg), transparent 60%),
          radial-gradient(ellipse 60% 50% at 100% 100%, var(--ai-bg), transparent 60%),
          var(--canvas);
      }

      .theme-toggle {
        position: absolute;
        top: var(--space-4);
        right: var(--space-4);
      }

      .login-panel {
        display: grid;
        grid-template-columns: 1.05fr 1fr;
        width: min(940px, 100%);
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius-xl);
        box-shadow: var(--shadow-lg);
        overflow: hidden;
      }

      /* ---- brand side ---- */
      .login-brand {
        padding: var(--space-10);
        background: var(--surface-sunken);
        border-right: 1px solid var(--border-subtle);
        display: flex;
        flex-direction: column;
        gap: var(--space-5);
      }

      .brand-row {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }

      .brand-glyph {
        width: 34px;
        height: 34px;
        display: grid;
        place-items: center;
        border-radius: var(--radius-md);
        background: linear-gradient(135deg, var(--brand-500), var(--brand-700));
        color: #fff;
        font-weight: var(--weight-bold);
        font-size: var(--text-sm);
      }

      .brand-name {
        font-size: var(--text-lg);
        font-weight: var(--weight-semibold);
      }

      .brand-headline {
        font-size: var(--text-2xl);
        line-height: 1.25;
        letter-spacing: -0.02em;
      }

      .brand-copy {
        color: var(--text-secondary);
        font-size: var(--text-base);
        line-height: var(--leading-relaxed);
      }

      .brand-points {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        margin-top: auto;
      }

      .brand-points li {
        display: flex;
        align-items: flex-start;
        gap: var(--space-3);
        font-size: var(--text-md);
        color: var(--text-secondary);
      }

      .brand-points ir-icon { color: var(--accent); margin-top: 2px; }

      /* ---- form side ---- */
      .login-form-side {
        padding: var(--space-10);
        display: flex;
        flex-direction: column;
        justify-content: center;
      }

      .form-title { font-size: var(--text-xl); }

      .form-subtitle {
        margin-top: var(--space-1);
        margin-bottom: var(--space-6);
        color: var(--text-secondary);
        font-size: var(--text-md);
      }

      form {
        display: flex;
        flex-direction: column;
        gap: var(--space-4);
      }

      .field {
        display: flex;
        flex-direction: column;
      }

      .field input { height: 38px; }

      .form-footnote {
        margin-top: var(--space-6);
        font-size: var(--text-xs);
        color: var(--text-muted);
        line-height: var(--leading-normal);
      }

      .spin { animation: spin 1s linear infinite; }

      @keyframes spin {
        to { transform: rotate(360deg); }
      }

      @media (max-width: 800px) {
        .login-panel { grid-template-columns: 1fr; }
        .login-brand { display: none; }
        .login-form-side { padding: var(--space-8) var(--space-6); }
      }
    `,
  ],
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  protected readonly theme = inject(ThemeService);

  protected username = '';
  protected password = '';

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected canSubmit(): boolean {
    return this.username.trim().length > 0 && this.password.length > 0;
  }

  protected submit(): void {
    if (!this.canSubmit() || this.busy()) {
      return;
    }

    this.busy.set(true);
    this.error.set(null);

    // Set the credentials first so the interceptor attaches them to the probe,
    // then roll back if the backend rejects them — otherwise the shell would
    // mount on bad credentials.
    this.auth.login(this.username.trim(), this.password);

    this.api.health().subscribe({
      next: () => this.busy.set(false),
      error: (response: { status?: number }) => {
        this.auth.logout();
        this.busy.set(false);
        this.error.set(
          response?.status === 401 || response?.status === 403
            ? 'Those credentials were rejected by the backend.'
            : 'Cannot reach the IntelliRelease backend. Check that it is running on port 8080.',
        );
      },
    });
  }
}
