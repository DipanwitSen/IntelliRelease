import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * A twelve-ish point trend line, sized to sit inside a stat tile.
 *
 * No axes, no labels, no tooltip: a sparkline's job is shape, not value — the
 * tile's own number carries the value. One series, so per the legend rule
 * there is no legend box; the tile's label already says what is plotted.
 *
 * The end-dot carries a 2px ring in the surface colour so it stays legible
 * where it sits on top of the line or against the area wash.
 */
@Component({
  selector: 'ir-sparkline',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (points().length > 1) {
      <svg
        [attr.viewBox]="'0 0 ' + width() + ' ' + height()"
        [attr.width]="width()"
        [attr.height]="height()"
        preserveAspectRatio="none"
        role="img"
        [attr.aria-label]="ariaLabel()"
      >
        @if (showArea()) {
          <path [attr.d]="areaPath()" [attr.fill]="color()" opacity="0.1" />
        }
        <path
          [attr.d]="linePath()"
          fill="none"
          [attr.stroke]="color()"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          vector-effect="non-scaling-stroke"
        />
        <circle
          [attr.cx]="lastPoint().x"
          [attr.cy]="lastPoint().y"
          r="2.6"
          [attr.fill]="color()"
          stroke="var(--surface)"
          stroke-width="2"
          vector-effect="non-scaling-stroke"
        />
      </svg>
    }
  `,
  styles: [
    `
      :host { display: block; line-height: 0; }
      svg { display: block; width: 100%; overflow: visible; }
    `,
  ],
})
export class SparklineComponent {
  readonly values = input<readonly number[]>([]);
  readonly width = input(120);
  readonly height = input(28);
  readonly color = input('var(--series-1)');
  readonly showArea = input(true);
  readonly ariaLabel = input('Trend');

  /**
   * Normalises values into the viewBox. A flat series would divide by zero on
   * `max - min`, so it is pinned to the vertical centre — which is also the
   * honest reading: nothing changed.
   */
  protected readonly points = computed(() => {
    const values = this.values();
    if (values.length < 2) {
      return [];
    }

    const inset = 3; // room for the end-dot's ring
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min;
    const usableHeight = this.height() - inset * 2;
    const step = this.width() / (values.length - 1);

    return values.map((value, index) => ({
      x: index * step,
      y: span === 0
        ? this.height() / 2
        : inset + usableHeight - ((value - min) / span) * usableHeight,
    }));
  });

  protected readonly linePath = computed(() =>
    this.points()
      .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(2)},${point.y.toFixed(2)}`)
      .join(' '),
  );

  protected readonly areaPath = computed(() => {
    const points = this.points();
    if (!points.length) {
      return '';
    }
    const bottom = this.height();
    return `${this.linePath()} L${points[points.length - 1].x.toFixed(2)},${bottom} L${points[0].x.toFixed(2)},${bottom} Z`;
  });

  protected readonly lastPoint = computed(() => this.points()[this.points().length - 1] ?? { x: 0, y: 0 });
}
