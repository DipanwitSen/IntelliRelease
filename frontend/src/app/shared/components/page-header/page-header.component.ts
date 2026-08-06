import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { IconComponent } from '../icon/icon.component';

/**
 * The top of every page.
 *
 * A component rather than a copied markup block so the title/subtitle rhythm,
 * the action alignment and the responsive wrap behave identically on all
 * seventeen modules — the difference between a product and a collection of
 * screens.
 */
@Component({
  selector: 'ir-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <header class="page-header">
      <div class="heading">
        @if (icon()) {
          <div class="heading-icon">
            <ir-icon [name]="icon()!" [size]="19" />
          </div>
        }
        <div class="heading-text">
          <div class="title-row">
            <h1 class="page-title">{{ title() }}</h1>
            <ng-content select="[titleSuffix]" />
          </div>
          @if (subtitle()) {
            <p class="page-subtitle">{{ subtitle() }}</p>
          }
        </div>
      </div>

      <div class="page-actions">
        <ng-content select="[actions]" />
      </div>
    </header>
  `,
  styles: [
    `
      :host { display: block; }

      .heading {
        display: flex;
        align-items: flex-start;
        gap: var(--space-3);
        min-width: 0;
      }

      .heading-icon {
        width: 36px;
        height: 36px;
        flex: 0 0 auto;
        display: grid;
        place-items: center;
        border-radius: var(--radius-md);
        background: var(--accent-subtle-bg);
        color: var(--accent-subtle-fg);
      }

      .heading-text { min-width: 0; }

      .title-row {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        flex-wrap: wrap;
        min-width: 0;
      }
    `,
  ],
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
  readonly icon = input<string | null>(null);
}
