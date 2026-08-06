import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { CountEntry } from '../../../core/models/common';

/**
 * A ranked list of counts — "top failures", "changed modules", "protocols in
 * use". The workhorse of this product's dashboards.
 *
 * Built as a list of rows rather than an SVG bar chart on purpose: the label
 * is the thing being read, the bar is context behind it, and rows give every
 * entry a direct value label for free. That direct labelling is also what
 * satisfies the contrast relief for the lighter palette slots.
 *
 * Single hue by default — this encodes magnitude, and magnitude is a
 * sequential job, not a categorical one. Pass `colorful` for genuine category
 * breakdowns where each row is a different *kind* of thing.
 */
@Component({
  selector: 'ir-bar-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    @if (visible().length) {
      <ul class="bar-list">
        @for (row of visible(); track row.key; let index = $index) {
          <li>
            <button
              type="button"
              class="bar-row"
              [class.clickable]="clickable()"
              [disabled]="!clickable()"
              (click)="rowClick.emit(row.entry)"
              [attr.title]="row.entry.label + ' — ' + row.entry.count"
            >
              <span class="bar-track">
                <span
                  class="bar-fill"
                  [style.width.%]="row.percent"
                  [style.background]="colorFor(index, row.entry)"
                ></span>
              </span>

              <span class="bar-label">{{ row.entry.label }}</span>
              <span class="bar-value">{{ row.entry.count | number }}</span>
            </button>
          </li>
        }

        @if (overflowCount() > 0) {
          <li>
            <div class="bar-row overflow-row">
              <span class="bar-track">
                <span class="bar-fill" [style.width.%]="overflowPercent()" [style.background]="'var(--series-other)'"></span>
              </span>
              <span class="bar-label muted">Other ({{ overflowCount() }})</span>
              <span class="bar-value muted">{{ overflowTotal() | number }}</span>
            </div>
          </li>
        }
      </ul>
    } @else {
      <p class="no-data">{{ emptyMessage() }}</p>
    }
  `,
  styles: [
    `
      :host { display: block; }

      .bar-list {
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
      }

      .bar-row {
        position: relative;
        display: grid;
        grid-template-columns: 1fr auto;
        align-items: center;
        gap: var(--space-3);
        width: 100%;
        padding: var(--space-2) var(--space-2);
        border-radius: var(--radius-sm);
        text-align: left;
        font-size: var(--text-sm);
        color: var(--text);
        overflow: hidden;
      }

      .bar-row.clickable:hover { background: var(--surface-hover); }

      /* The bar sits behind the text rather than beside it — the label stays
         full width and readable however long the value is. */
      .bar-track {
        position: absolute;
        inset: 0;
        display: block;
        pointer-events: none;
      }

      .bar-fill {
        position: absolute;
        left: 0;
        top: 0;
        bottom: 0;
        display: block;
        /* Square where it meets the baseline, rounded at the data end. */
        border-radius: 0 var(--radius-xs) var(--radius-xs) 0;
        opacity: 0.16;
        transition: width var(--duration-slow) var(--ease-out);
      }

      .bar-label {
        position: relative;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        min-width: 0;
      }

      .bar-value {
        position: relative;
        font-variant-numeric: tabular-nums;
        font-weight: var(--weight-semibold);
        color: var(--text-secondary);
      }

      .overflow-row { cursor: default; }

      .no-data {
        padding: var(--space-6);
        text-align: center;
        color: var(--text-muted);
        font-size: var(--text-md);
      }
    `,
  ],
})
export class BarListComponent {
  readonly entries = input<readonly CountEntry[]>([]);
  /** Rows past this fold into a single "Other" row rather than growing the list. */
  readonly limit = input(6);
  readonly colorful = input(false);
  readonly clickable = input(false);
  readonly emptyMessage = input('Nothing recorded in this window.');

  readonly rowClick = output<CountEntry>();

  private readonly sorted = computed(() =>
    [...this.entries()].sort((a, b) => b.count - a.count),
  );

  private readonly max = computed(() => this.sorted()[0]?.count ?? 0);

  protected readonly visible = computed(() => {
    const max = this.max();
    return this.sorted()
      .slice(0, this.limit())
      .map((entry) => ({
        key: entry.key,
        entry,
        percent: max === 0 ? 0 : (entry.count / max) * 100,
      }));
  });

  private readonly overflow = computed(() => this.sorted().slice(this.limit()));

  protected readonly overflowCount = computed(() => this.overflow().length);

  protected readonly overflowTotal = computed(() =>
    this.overflow().reduce((total, entry) => total + entry.count, 0),
  );

  protected readonly overflowPercent = computed(() => {
    const max = this.max();
    return max === 0 ? 0 : (this.overflowTotal() / max) * 100;
  });

  /**
   * A tone declared on the entry always wins — that is the backend saying
   * "this row is a failure", and severity outranks slot order.
   */
  protected colorFor(index: number, entry: CountEntry): string {
    if (entry.tone) {
      switch (entry.tone) {
        case 'success': return 'var(--success-solid)';
        case 'warning': return 'var(--warning-solid)';
        case 'danger': return 'var(--danger-solid)';
        case 'info': return 'var(--info-solid)';
        case 'ai': return 'var(--ai-solid)';
        default: return 'var(--series-1)';
      }
    }
    return this.colorful() ? `var(--series-${(index % 8) + 1})` : 'var(--series-1)';
  }
}
