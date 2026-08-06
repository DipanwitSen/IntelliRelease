import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ThemeService } from './core/services/theme.service';
import { LoginPage } from './features/auth/login.page';
import { ShellComponent } from './layout/shell/shell.component';
import { AuthService } from './services/auth.service';

/**
 * Root component: the authentication gate, and nothing else.
 *
 * This gate is a convenience, not a security control — the backend enforces
 * authentication and approval on every request regardless of what the UI
 * chooses to render (architecture rule 8). Someone who bypassed this component
 * would reach a shell whose every request comes back 401.
 *
 * ThemeService is injected here purely so it constructs during bootstrap and
 * applies the stored theme before first paint.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ShellComponent, LoginPage],
  template: `
    @if (auth.isAuthenticated()) {
      <ir-shell />
    } @else {
      <ir-login />
    }
  `,
})
export class AppComponent {
  protected readonly auth = inject(AuthService);

  constructor() {
    inject(ThemeService);
  }
}
