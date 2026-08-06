import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, signal } from '@angular/core';

import { TrendPoint } from '../../../core/models/common';

export interface TrendSeries {
  readonly key: string;
  readonly label: string;
  readonly points: readonly TrendPoint[];
}

interface Plotted {
  readonly key: string;
  readonly label: string;
  readonly color: string;
  readonly line: string;
  readonly area: string;
  readonly dots: readonly { x: number; y: number; value: number; label: string }[];
}

/**
 * Line chart over time.
 *
 * Deliberately single-axis: two measures on different scales get two charts,
 * never a second y-axis. Every series is plotted against one shared domain, so
 * comparing heights is always a valid thing to do.
 *
 * A crosshair and tooltip ship by default — an SVG chart in a browser is
 * interactive whether or not you designed for it, and a chart that ignores the
 * pointer feels broken. The hit layer is a full-height invisible rect per
 * column, so the target is far larger than the 8px dot it selects.
 */
@Component({
  selector: 'ir-trend-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (hasData()) {
      <figure class="chart">
        @if (series().length > 1) {
          <figcaption class="legend">
            @for (plot of plotted(); track plot.key) {
              <span class="legend-item">
                <span class="legend-key" [style.background]="plot.color"></span>
                {{ plot.label }}
              </span>
            }
          </figcaption>
        }

        <div class="plot-wrap">
          <svg
            [attr.viewBox]="'0 0 ' + w + ' ' + h"
            preserveAspectRatio="none"
            role="img"
            [attr.aria-label]="ariaLabel()"
            (pointermove)="onPointerMove($event)"
            (pointerleave)="activeIndex.set(null)"
          >
            <!-- Gridlines: hairline, solid, one step off the surface. -->
            @for (tick of yTicks(); track tick.value) {
              <line
                [attr.x1]="pad.left" [attr.x2]="w - pad.right"
                [attr.y1]="tick.y" [attr.y2]="tick.y"
                stroke="var(--chart-grid)" stroke-width="1" vector-effect="non-scaling-stroke"
              />
              <text
                [attr.x]="pad.left - 6" [attr.y]="tick.y + 3"
                text-anchor="end" class="tick"
              >{{ tick.label }}</text>
            }

            @if (activeIndex() !== null) {
              <line
                [attr.x1]="xFor(activeIndex()!)" [attr.x2]="xFor(activeIndex()!)"
                [attr.y1]="pad.top" [attr.y2]="h - pad.bottom"
                stroke="var(--chart-axis)" stroke-width="1" vector-effect="non-scaling-stroke"
              />
            }

            @for (plot of plotted(); track plot.key) {
              @if (showArea() && series().length === 1) {
                <path [attr.d]="plot.area" [attr.fill]="plot.color" opacity="0.1" />
              }
              <path
                [attr.d]="plot.line" fill="none"
                [attr.stroke]="plot.color" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round"
                vector-effect="non-scaling-stroke"
              />
              @if (activeIndex() !== null && plot.dots[activeIndex()!]) {
                <circle
                  [attr.cx]="plot.dots[activeIndex()!].x"
                  [attr.cy]="plot.dots[activeIndex()!].y"
                  r="4" [attr.fill]="plot.color"
                  stroke="var(--surface)" stroke-width="2"
                  vector-effect="non-scaling-stroke"
                />
              }
            }

            @for (label of xLabels(); track label.index) {
              <text [attr.x]="label.x" [attr.y]="h - 4" text-anchor="middle" class="tick">{{ label.text }}</text>
            }
          </svg>

          @if (activeIndex() !== null) {
            <div class="tooltip" [style.left.%]="tooltipLeft()" [class.flip]="tooltipLeft() > 60">
              <div class="tooltip-title">{{ labelAt(activeIndex()!) }}</div>
              @for (plot of plotted(); track plot.key) {
                <div class="tooltip-row">
                  <span class="legend-key" [style.background]="plot.color"></span>
                  <span class="tooltip-label">{{ plot.label }}</span>
                  <span class="tooltip-value">{{ valueAt(plot, activeIndex()) }}</span>
                </div>
              }
            </div>
          }
        </div>
      </figure>
    } @else {
      <p class="no-data">{{ emptyMessage() }}</p>
    }
  `,
  styles: [
    `
      :host { display: block; }

      .chart { display: flex; flex-direction: column; gap: var(--space-2); }

      .legend {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-4);
        font-size: var(--text-xs);
        color: var(--text-secondary);
      }

      .legend-item { display: inline-flex; align-items: center; gap: var(--space-2); }

      .legend-key {
        width: 12px;
        height: 3px;
        border-radius: var(--radius-pill);
        flex: 0 0 auto;
      }

      .plot-wrap { position: relative; }

      svg { display: block; width: 100%; height: 180px; touch-action: none; }

      .tick {
        font-size: 9px;
        fill: var(--chart-muted);
        font-variant-numeric: tabular-nums;
      }

      .tooltip {
        position: absolute;
        top: 0;
        transform: translateX(-50%);
        min-width: 130px;
        padding: var(--space-2) var(--space-3);
        border-radius: var(--radius-sm);
        border: 1px solid var(--border);
        background: var(--surface-raised);
        box-shadow: var(--shadow-md);
        font-size: var(--text-xs);
        pointer-events: none;
        z-index: var(--z-tooltip);
      }

      .tooltip.flip { transform: translateX(-100%); }

      .tooltip-title {
        font-weight: var(--weight-semibold);
        color: var(--text);
        margin-bottom: 4px;
      }

      .tooltip-row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }

      .tooltip-label { color: var(--text-secondary); }

      .tooltip-value {
        margin-left: auto;
        font-weight: var(--weight-semibold);
        font-variant-numeric: tabular-nums;
      }

      .no-data {
        padding: var(--space-8);
        text-align: center;
        color: var(--text-muted);
        font-size: var(--text-md);
      }
    `,
  ],
})
export class TrendChartComponent {
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly series = input<readonly TrendSeries[]>([]);
  readonly showArea = input(true);
  readonly ariaLabel = input('Trend over time');
  readonly emptyMessage = input('No trend data for this window yet.');

  protected readonly activeIndex = signal<number | null>(null);

  /** ViewBox units. The SVG scales to its container; only ratios matter here. */
  protected readonly w = 600;
  protected readonly h = 180;
  protected readonly pad = { top: 10, right: 10, bottom: 20, left: 34 };

  protected readonly hasData = computed(() =>
    this.series().some((entry) => entry.points.length > 1),
  );

  private readonly pointCount = computed(() =>
    Math.max(...this.series().map((entry) => entry.points.length), 0),
  );

  /**
   * A shared domain across every series, always anchored at zero.
   *
   * Anchoring at zero is the honest default for the counts and rates this
   * product plots — a truncated axis turns a 2% move into a cliff.
   */
  private readonly domain = computed(() => {
    const values = this.series().flatMap((entry) => entry.points.map((point) => point.value));
    const max = values.length ? Math.max(...values) : 0;
    return { min: 0, max: max === 0 ? 1 : max };
  });

  protected readonly plotted = computed<readonly Plotted[]>(() =>
    this.series().map((entry, seriesIndex) => {
      const color = `var(--series-${(seriesIndex % 8) + 1})`;
      const dots = entry.points.map((point, index) => ({
        x: this.xFor(index),
        y: this.yFor(point.value),
        value: point.value,
        label: point.label,
      }));

      const line = dots
        .map((dot, index) => `${index === 0 ? 'M' : 'L'}${dot.x.toFixed(2)},${dot.y.toFixed(2)}`)
        .join(' ');

      const baseline = this.h - this.pad.bottom;
      const area = dots.length
        ? `${line} L${dots[dots.length - 1].x.toFixed(2)},${baseline} L${dots[0].x.toFixed(2)},${baseline} Z`
        : '';

      return { key: entry.key, label: entry.label, color, line, area, dots };
    }),
  );

  /** Four gridlines at clean, rounded values. */
  protected readonly yTicks = computed(() => {
    const { max } = this.domain();
    const magnitude = Math.pow(10, Math.floor(Math.log10(max || 1)));
    const step = Math.ceil(max / 4 / magnitude) * magnitude;
    const ticks: { value: number; y: number; label: string }[] = [];

    for (let value = 0; value <= max + step / 2; value += step) {
      ticks.push({ value, y: this.yFor(value), label: formatTick(value) });
    }
    return ticks;
  });

  /**
   * Thins x labels so they never collide: at most six, evenly spaced, always
   * including the last point so "now" is labelled.
   */
  protected readonly xLabels = computed(() => {
    const count = this.pointCount();
    if (!count) {
      return [];
    }
    const stride = Math.max(1, Math.ceil(count / 6));
    const points = this.series()[0]?.points ?? [];

    return points
      .map((point, index) => ({ index, x: this.xFor(index), text: point.label }))
      .filter((entry) => entry.index % stride === 0 || entry.index === count - 1);
  });

  protected xFor(index: number): number {
    const count = this.pointCount();
    if (count <= 1) {
      return this.pad.left;
    }
    const usable = this.w - this.pad.left - this.pad.right;
    return this.pad.left + (index / (count - 1)) * usable;
  }

  protected labelAt(index: number): string {
    return this.series()[0]?.points[index]?.label ?? '';
  }

  /**
   * `.at()` rather than `[index]`: it is typed as possibly-undefined, which is
   * the truth for an out-of-range index and lets the template stay honest
   * without a non-null assertion.
   */
  protected valueAt(plot: Plotted, index: number | null): string {
    if (index === null) {
      return '—';
    }
    const dot = plot.dots.at(index);
    return dot === undefined ? '—' : String(dot.value);
  }

  protected tooltipLeft(): number {
    const index = this.activeIndex();
    if (index === null) {
      return 0;
    }
    return (this.xFor(index) / this.w) * 100;
  }

  /**
   * Maps a pointer position to the nearest column. Uses the rendered width
   * rather than the viewBox width, because the SVG stretches.
   */
  protected onPointerMove(event: PointerEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const rect = svg.getBoundingClientRect();
    const count = this.pointCount();
    if (count < 2 || rect.width === 0) {
      return;
    }

    const relative = (event.clientX - rect.left) / rect.width;
    const padLeftRatio = this.pad.left / this.w;
    const padRightRatio = this.pad.right / this.w;
    const usable = 1 - padLeftRatio - padRightRatio;
    const position = (relative - padLeftRatio) / usable;

    const index = Math.round(position * (count - 1));
    this.activeIndex.set(Math.min(count - 1, Math.max(0, index)));
  }

  private yFor(value: number): number {
    const { min, max } = this.domain();
    const usable = this.h - this.pad.top - this.pad.bottom;
    const ratio = (value - min) / (max - min);
    return this.pad.top + usable - ratio * usable;
  }
}

/** 1200 -> "1.2k". Axis ticks stay short so they never crowd the plot. */
function formatTick(value: number): string {
  if (Math.abs(value) >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (Math.abs(value) >= 1000) return `${(value / 1000).toFixed(value % 1000 === 0 ? 0 : 1)}k`;
  return String(Math.round(value));
}
