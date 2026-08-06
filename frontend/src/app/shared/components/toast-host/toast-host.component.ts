import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Renders the toast stack.
 *
 * `aria-live="polite"` rather than `assertive`: these announce the result of
 * something the user just did, so interrupting whatever the screen reader is
 * mid-sentence on would be rude without being more informative.
 */
@Component({
  selector: 'ir-toast-host',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="toast-host" role="region" aria-live="polite" aria-label="Notifications">
      @for (toast of toasts(); track toast.id) {
        <div class="toast" [class]="'tone-' + toast.tone">
          <ir-icon [name]="iconFor(toast.tone)" [size]="17" class="toast-icon" />
          <div class="toast-body">
            <div class="toast-title">{{ toast.title }}</div>
            @if (toast.detail) {
              <div class="toast-detail">{{ toast.detail }}</div>
            }
          </div>
          <button type="button" class="toast-close" (click)="toasts$.dismiss(toast.id)" aria-label="Dismiss">
            <ir-icon name="x" [size]="14" />
          </button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .toast-host {
        position: fixed;
        bottom: var(--space-4);
        right: var(--space-4);
        z-index: var(--z-toast);
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        width: min(400px, calc(100vw - 2rem));
        pointer-events: none;
      }

      .toast {
        display: flex;
        align-items: flex-start;
        gap: var(--space-3);
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-md);
        border: 1px solid currentColor;
        background: var(--surface-raised);
        box-shadow: var(--shadow-lg);
        pointer-events: auto;
        animation: toast-in var(--duration-normal) var(--ease-out);
      }

      @keyframes toast-in {
        from { opacity: 0; transform: translateY(8px) scale(0.98); }
        to   { opacity: 1; transform: none; }
      }

      .toast-icon { margin-top: 1px; }

      .toast-body { flex: 1; min-width: 0; }

      .toast-title {
        font-size: var(--text-md);
        font-weight: var(--weight-semibold);
        color: var(--text);
      }

      .toast-detail {
        margin-top: 2px;
        font-size: var(--text-sm);
        color: var(--text-secondary);
        overflow-wrap: anywhere;
      }

      .toast-close {
        color: var(--text-muted);
        margin-top: 1px;
      }

      .toast-close:hover { color: var(--text); }
    `,
  ],
})
export class ToastHostComponent {
  protected readonly toasts$ = inject(ToastService);
  protected readonly toasts = this.toasts$.toasts;

  protected iconFor(tone: string): string {
    switch (tone) {
      case 'success': return 'check-circle';
      case 'warning': return 'alert-triangle';
      case 'danger': return 'x-circle';
      default: return 'info';
    }
  }
}
