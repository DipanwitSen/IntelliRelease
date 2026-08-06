import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';

import { IconComponent } from '../icon/icon.component';

/**
 * A titled card that can collapse.
 *
 * Collapsing is opt-in (`collapsible`) rather than always-on: a card the user
 * can accidentally fold away is annoying when the content is the point of the
 * page, and genuinely useful when it is one of nine panels on a detail view.
 *
 * When collapsible, the header is a real `<button>` with `aria-expanded` so
 * keyboard and screen-reader users get the same affordance as mouse users.
 */
@Component({
  selector: 'ir-section-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, NgTemplateOutlet],
  template: `
    <section class="card">
      @if (collapsible()) {
        <button
          type="button"
          class="card-header header-button"
          (click)="expanded.set(!expanded())"
          [attr.aria-expanded]="expanded()"
        >
          <ng-container *ngTemplateOutlet="headerContent" />
          <ir-icon
            [name]="expanded() ? 'chevron-up' : 'chevron-down'"
            [size]="15"
            class="muted chevron"
          />
        </button>
      } @else {
        <div class="card-header">
          <ng-container *ngTemplateOutlet="headerContent" />
          <div class="header-actions">
            <ng-content select="[actions]" />
          </div>
        </div>
      }

      <ng-template #headerContent>
        <div class="card-title">
          @if (icon()) {
            <ir-icon [name]="icon()!" [size]="16" class="title-icon" />
          }
          <span class="truncate">{{ title() }}</span>
          @if (count() !== null) {
            <span class="tab-count">{{ count() }}</span>
          }
          @if (subtitle()) {
            <span class="card-subtitle truncate">{{ subtitle() }}</span>
          }
        </div>
      </ng-template>

      @if (expanded()) {
        <div [class]="bodyClass()">
          <ng-content />
        </div>
      }

      <ng-content select="[footer]" />
    </section>
  `,
  styles: [
    `
      :host { display: block; min-width: 0; }

      .header-button {
        width: 100%;
        text-align: left;
        transition: background-color var(--duration-fast) var(--ease-out);
      }

      .header-button:hover { background: var(--surface-hover); }

      .header-button[aria-expanded='false'] { border-bottom: none; }

      .chevron { flex: 0 0 auto; }

      .title-icon { color: var(--text-muted); }

      .header-actions {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        flex: 0 0 auto;
      }
    `,
  ],
})
export class SectionCardComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
  readonly icon = input<string | null>(null);
  /** Rendered as a pill next to the title. Null hides it — 0 is a real count. */
  readonly count = input<number | null>(null);
  readonly collapsible = input(false);
  /** Removes body padding, for cards whose content is a table or a diagram. */
  readonly flush = input(false);
  readonly tight = input(false);

  /** Two-way so a page can programmatically fold a section. */
  readonly expanded = model(true);

  protected readonly bodyClass = computed(() => {
    if (this.flush()) return 'card-body card-body-flush';
    if (this.tight()) return 'card-body card-body-tight';
    return 'card-body';
  });
}
