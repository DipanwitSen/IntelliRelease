import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { IconComponent } from '../icon/icon.component';

/**
 * The three things that are not "content": nothing here, something broke, and
 * still loading.
 *
 * Grouped in one file because they are always considered together — every list
 * in the product renders exactly one of these or its data, and keeping them
 * adjacent makes it obvious when one has been forgotten.
 */

@Component({
  selector: 'ir-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="empty-state">
      <div class="empty-state-icon">
        <ir-icon [name]="icon()" [size]="22" />
      </div>
      <div class="empty-state-title">{{ title() }}</div>
      @if (body()) {
        <p class="empty-state-body">{{ body() }}</p>
      }
      <ng-content />
    </div>
  `,
})
export class EmptyStateComponent {
  readonly icon = input('inbox');
  readonly title = input('Nothing here yet');
  readonly body = input<string | null>(null);
}

/**
 * A failed read.
 *
 * Always offers a retry: the most common cause in this product is the backend
 * not being up yet, and that fixes itself the moment it is.
 */
@Component({
  selector: 'ir-error-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="callout tone-danger" role="alert">
      <ir-icon name="alert-circle" [size]="17" class="callout-icon" />
      <div class="stack-2">
        <div class="callout-title">{{ title() }}</div>
        <div>{{ message() }}</div>
        @if (showRetry()) {
          <div>
            <button type="button" class="btn btn-sm btn-secondary" (click)="retry.emit()">
              <ir-icon name="refresh-cw" [size]="13" />
              Try again
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`:host { display: block; }`],
})
export class ErrorPanelComponent {
  readonly title = input('Could not load this');
  readonly message = input('');
  readonly showRetry = input(true);
  readonly retry = output<void>();
}

/**
 * Placeholder rows that match the shape of what is coming.
 *
 * `rows` and `variant` exist so a table skeleton looks like a table and a card
 * skeleton looks like a card — a generic grey box makes the page visibly jump
 * when real content replaces it.
 */
@Component({
  selector: 'ir-skeleton',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (variant()) {
      @case ('table') {
        <div class="skeleton-table" [attr.aria-busy]="true" [attr.aria-label]="label()">
          @for (row of rowRange(); track row) {
            <div class="skeleton-row">
              <div class="skeleton skeleton-text" style="width: 26%"></div>
              <div class="skeleton skeleton-text" style="width: 40%"></div>
              <div class="skeleton skeleton-text" style="width: 14%"></div>
              <div class="skeleton skeleton-text" style="width: 12%"></div>
            </div>
          }
        </div>
      }
      @case ('kpi') {
        <div class="skeleton-kpis" [attr.aria-busy]="true" [attr.aria-label]="label()">
          @for (row of rowRange(); track row) {
            <div class="skeleton-kpi">
              <div class="skeleton skeleton-text" style="width: 50%"></div>
              <div class="skeleton" style="height: 26px; width: 40%; margin-top: 10px"></div>
              <div class="skeleton skeleton-text" style="width: 70%; margin-top: 12px"></div>
            </div>
          }
        </div>
      }
      @case ('list') {
        <div class="skeleton-list" [attr.aria-busy]="true" [attr.aria-label]="label()">
          @for (row of rowRange(); track row) {
            <div class="skeleton-list-item">
              <div class="skeleton" style="width: 28px; height: 28px; border-radius: 50%"></div>
              <div style="flex: 1">
                <div class="skeleton skeleton-text skeleton-line-50"></div>
                <div class="skeleton skeleton-text skeleton-line-70"></div>
              </div>
            </div>
          }
        </div>
      }
      @default {
        <div class="skeleton-card" [attr.aria-busy]="true" [attr.aria-label]="label()">
          <div class="skeleton skeleton-title"></div>
          @for (row of rowRange(); track row) {
            <div class="skeleton skeleton-text" [style.width]="row % 2 === 0 ? '92%' : '68%'"></div>
          }
        </div>
      }
    }
  `,
  styles: [
    `
      :host { display: block; }

      .skeleton-card { padding: var(--space-5); display: flex; flex-direction: column; gap: var(--space-2); }

      .skeleton-table { display: flex; flex-direction: column; }

      .skeleton-row {
        display: flex;
        gap: var(--space-4);
        align-items: center;
        padding: var(--space-4);
        border-bottom: 1px solid var(--border-subtle);
      }

      .skeleton-kpis {
        display: grid;
        gap: var(--space-3);
        grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
      }

      .skeleton-kpi {
        padding: var(--space-4);
        border: 1px solid var(--border);
        border-radius: var(--radius-lg);
        background: var(--surface);
      }

      .skeleton-list { display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-4); }

      .skeleton-list-item { display: flex; gap: var(--space-3); align-items: center; }
    `,
  ],
})
export class SkeletonComponent {
  readonly variant = input<'card' | 'table' | 'kpi' | 'list'>('card');
  readonly rows = input(4);
  readonly label = input('Loading');

  protected rowRange(): number[] {
    return Array.from({ length: this.rows() }, (_, index) => index);
  }
}
