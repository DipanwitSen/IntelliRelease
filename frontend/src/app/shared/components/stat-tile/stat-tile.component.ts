import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Kpi } from '../../../core/models/dashboard';
import { IconComponent } from '../icon/icon.component';
import { ProvenanceBadgeComponent } from '../badge/badge.component';
import { SparklineComponent } from '../charts/sparkline.component';

/**
 * A KPI tile: label, value, optional delta, optional sparkline.
 *
 * Two decisions worth naming:
 *
 * 1. The delta's colour comes from *direction × whether up is good*, never
 *    from direction alone. More merged PRs is green; more failed tests is red;
 *    both are "up". `deltaIsGood` on the KPI says which, and the backend owns
 *    that call because it owns the metric's meaning.
 *
 * 2. `unavailableReason` renders instead of the value. A dashboard that shows
 *    "0 failed tests" when no CI is connected is actively lying — this shows
 *    "Not connected" and says why on hover.
 */
@Component({
  selector: 'ir-stat-tile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IconComponent, SparklineComponent, ProvenanceBadgeComponent,
    RouterLink, DecimalPipe, NgTemplateOutlet,
  ],
  template: `
    <div class="tile card" [class.card-interactive]="!!kpi().routerLink">
      @if (kpi().routerLink) {
        <a class="tile-link" [routerLink]="kpi().routerLink">
          <ng-container *ngTemplateOutlet="content" />
        </a>
      } @else {
        <div class="tile-link">
          <ng-container *ngTemplateOutlet="content" />
        </div>
      }
    </div>

    <ng-template #content>
      <div class="tile-top">
        <span class="tile-icon" [class]="'tile-icon tone-' + kpi().tone">
          <ir-icon [name]="kpi().icon" [size]="15" />
        </span>
        <span class="tile-label">{{ kpi().label }}</span>
        <ir-provenance [value]="kpi().provenance" [showIcon]="false" class="tile-provenance" />
      </div>

      @if (kpi().unavailableReason) {
        <div class="tile-unavailable" [title]="kpi().unavailableReason!">
          <ir-icon name="minus" [size]="15" />
          Not available
        </div>
        <div class="tile-detail">{{ kpi().unavailableReason }}</div>
      } @else {
        <div class="tile-value-row">
          <span class="tile-value">{{ kpi().value }}</span>
          @if (kpi().unit) {
            <span class="tile-unit">{{ kpi().unit }}</span>
          }
        </div>

        <div class="tile-bottom">
          @if (kpi().deltaPercent !== undefined && kpi().deltaPercent !== null) {
            <span class="delta" [class]="'delta ' + deltaClass()">
              <ir-icon [name]="deltaIcon()" [size]="12" />
              {{ absDelta() | number: '1.0-1' }}%
            </span>
          }
          @if (kpi().detail) {
            <span class="tile-detail truncate">{{ kpi().detail }}</span>
          }
          @if (kpi().sparkline?.length) {
            <ir-sparkline
              class="tile-spark"
              [values]="kpi().sparkline!"
              [color]="sparkColor()"
              [height]="24"
              [ariaLabel]="kpi().label + ' trend'"
            />
          }
        </div>
      }
    </ng-template>
  `,
  styles: [
    `
      :host { display: block; min-width: 0; }

      .tile { height: 100%; }

      .tile-link {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding: var(--space-4);
        height: 100%;
        color: inherit;
        text-decoration: none;
      }

      a.tile-link:hover { text-decoration: none; }

      .tile-top {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        min-width: 0;
      }

      .tile-icon {
        width: 24px;
        height: 24px;
        display: grid;
        place-items: center;
        border-radius: var(--radius-sm);
        flex: 0 0 auto;
      }

      .tile-label {
        font-size: var(--text-xs);
        font-weight: var(--weight-medium);
        color: var(--text-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .tile-provenance { margin-left: auto; flex: 0 0 auto; }

      .tile-value-row {
        display: flex;
        align-items: baseline;
        gap: var(--space-1);
      }

      /* Proportional figures: tabular-nums makes a large standalone number
         look loose at display sizes. Columns get tabular; this does not. */
      .tile-value {
        font-size: var(--text-2xl);
        font-weight: var(--weight-semibold);
        line-height: 1.1;
        letter-spacing: -0.02em;
      }

      .tile-unit {
        font-size: var(--text-sm);
        color: var(--text-muted);
      }

      .tile-bottom {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        margin-top: auto;
        min-width: 0;
      }

      .delta {
        display: inline-flex;
        align-items: center;
        gap: 2px;
        font-size: var(--text-xs);
        font-weight: var(--weight-semibold);
        font-variant-numeric: tabular-nums;
        flex: 0 0 auto;
      }

      .delta-good { color: var(--success-fg); }
      .delta-bad { color: var(--danger-fg); }
      .delta-flat { color: var(--text-muted); }

      .tile-detail {
        font-size: var(--text-xs);
        color: var(--text-muted);
        min-width: 0;
      }

      .tile-spark { margin-left: auto; width: 84px; flex: 0 0 auto; }

      .tile-unavailable {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        font-size: var(--text-lg);
        font-weight: var(--weight-medium);
        color: var(--text-muted);
      }
    `,
  ],
})
export class StatTileComponent {
  readonly kpi = input.required<Kpi>();

  protected readonly absDelta = computed(() => Math.abs(this.kpi().deltaPercent ?? 0));

  protected readonly deltaIcon = computed(() => {
    const delta = this.kpi().deltaPercent ?? 0;
    if (delta > 0) return 'trending-up';
    if (delta < 0) return 'trending-down';
    return 'minus';
  });

  protected readonly deltaClass = computed(() => {
    const kpi = this.kpi();
    const delta = kpi.deltaPercent ?? 0;
    if (delta === 0) {
      return 'delta-flat';
    }
    // Default to "up is good" when the backend did not say — the common case
    // for throughput metrics, and the tile shows the raw sign either way.
    const upIsGood = kpi.deltaIsGood ?? true;
    const rising = delta > 0;
    return rising === upIsGood ? 'delta-good' : 'delta-bad';
  });

  protected readonly sparkColor = computed(() => {
    switch (this.kpi().tone) {
      case 'success': return 'var(--success-solid)';
      case 'warning': return 'var(--warning-solid)';
      case 'danger': return 'var(--danger-solid)';
      case 'ai': return 'var(--ai-solid)';
      default: return 'var(--series-1)';
    }
  });
}
