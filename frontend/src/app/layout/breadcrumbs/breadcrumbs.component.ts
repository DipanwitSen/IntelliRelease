import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BreadcrumbService } from '../../core/services/breadcrumb.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * The trail under the topbar.
 *
 * The last crumb is rendered as plain text with `aria-current="page"` rather
 * than as a link — linking to the page you are already on is noise for mouse
 * users and a dead end for screen-reader users.
 */
@Component({
  selector: 'ir-breadcrumbs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent],
  template: `
    @if (crumbs().length) {
      <nav class="crumbs" aria-label="Breadcrumb">
        <ol>
          <li>
            <a routerLink="/dashboard" class="crumb crumb-home" aria-label="Dashboard">
              <ir-icon name="grid" [size]="13" />
            </a>
          </li>
          @for (crumb of crumbs(); track $index; let last = $last) {
            <li>
              <ir-icon name="chevron-right" [size]="12" class="sep" />
              @if (last || !crumb.link) {
                <span class="crumb current" aria-current="page">{{ crumb.label }}</span>
              } @else {
                <a class="crumb" [routerLink]="crumb.link">{{ crumb.label }}</a>
              }
            </li>
          }
        </ol>
      </nav>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        min-width: 0;
      }

      .crumbs ol {
        display: flex;
        align-items: center;
        gap: var(--space-1);
        flex-wrap: wrap;
      }

      .crumbs li {
        display: flex;
        align-items: center;
        gap: var(--space-1);
        min-width: 0;
      }

      .crumb {
        display: inline-flex;
        align-items: center;
        padding: 2px var(--space-1);
        border-radius: var(--radius-xs);
        font-size: var(--text-xs);
        color: var(--text-muted);
        text-decoration: none;
        max-width: 32ch;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      a.crumb:hover {
        color: var(--text);
        text-decoration: none;
      }

      .crumb.current {
        color: var(--text-secondary);
        font-weight: var(--weight-medium);
      }

      .sep {
        color: var(--text-muted);
        opacity: 0.55;
      }
    `,
  ],
})
export class BreadcrumbsComponent {
  protected readonly crumbs = inject(BreadcrumbService).crumbs;
}
