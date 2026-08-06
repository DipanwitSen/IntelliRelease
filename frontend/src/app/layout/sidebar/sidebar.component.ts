import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { NAV_GROUPS } from '../../core/navigation';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Left navigation.
 *
 * Collapsed mode keeps the icons and drops the labels, using the native
 * `title` attribute for the tooltip — a custom tooltip here would have to
 * escape the sidebar's stacking context and is not worth the complexity for
 * seventeen links.
 *
 * On small screens the same markup becomes an off-canvas drawer; `mobileOpen`
 * drives that, and the backdrop is rendered by the shell so it can cover the
 * topbar too.
 */
@Component({
  selector: 'ir-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, IconComponent],
  template: `
    <nav class="sidebar" [class.collapsed]="collapsed()" [class.mobile-open]="mobileOpen()" aria-label="Main">
      <div class="brand">
        <a class="brand-mark" routerLink="/dashboard" [title]="collapsed() ? 'IntelliRelease' : ''">
          <span class="brand-glyph" aria-hidden="true">IR</span>
          @if (!collapsed()) {
            <span class="brand-text">
              <span class="brand-name">IntelliRelease</span>
              <span class="brand-tag">Release &amp; Integration Intelligence</span>
            </span>
          }
        </a>
      </div>

      <div class="nav-scroll">
        @for (group of groups; track group.id) {
          <div class="nav-group">
            @if (!collapsed()) {
              <div class="nav-group-label">{{ group.label }}</div>
            } @else {
              <div class="nav-group-rule" role="presentation"></div>
            }

            <ul>
              @for (item of group.items; track item.id) {
                <li>
                  <a
                    class="nav-link"
                    [routerLink]="item.route"
                    routerLinkActive="active"
                    [routerLinkActiveOptions]="{ exact: false }"
                    [title]="collapsed() ? item.label + ' — ' + item.description : ''"
                    (click)="navigated.emit()"
                  >
                    <ir-icon [name]="item.icon" [size]="17" />
                    @if (!collapsed()) {
                      <span class="nav-label">{{ item.label }}</span>
                    }
                  </a>
                </li>
              }
            </ul>
          </div>
        }
      </div>

      <button
        type="button"
        class="collapse-toggle"
        (click)="toggleCollapsed.emit()"
        [attr.aria-label]="collapsed() ? 'Expand navigation' : 'Collapse navigation'"
      >
        <ir-icon [name]="collapsed() ? 'chevron-right' : 'chevron-left'" [size]="15" />
        @if (!collapsed()) {
          <span>Collapse</span>
        }
      </button>
    </nav>
  `,
  styles: [
    `
      :host {
        display: contents;
      }

      .sidebar {
        grid-area: sidebar;
        display: flex;
        flex-direction: column;
        width: var(--sidebar-width);
        height: 100vh;
        position: sticky;
        top: 0;
        background: var(--surface);
        border-right: 1px solid var(--border);
        z-index: var(--z-sidebar);
        transition: width var(--duration-normal) var(--ease-out);
      }

      .sidebar.collapsed {
        width: var(--sidebar-width-collapsed);
      }

      /* ---- brand ---- */
      .brand {
        height: var(--topbar-height);
        display: flex;
        align-items: center;
        padding-inline: var(--space-3);
        border-bottom: 1px solid var(--border-subtle);
        flex: 0 0 auto;
      }

      .brand-mark {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        min-width: 0;
        color: inherit;
        text-decoration: none;
      }

      .brand-mark:hover {
        text-decoration: none;
      }

      .brand-glyph {
        width: 30px;
        height: 30px;
        flex: 0 0 auto;
        display: grid;
        place-items: center;
        border-radius: var(--radius-md);
        background: linear-gradient(135deg, var(--brand-500), var(--brand-700));
        color: #fff;
        font-size: var(--text-xs);
        font-weight: var(--weight-bold);
        letter-spacing: 0.02em;
      }

      .brand-text {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }

      .brand-name {
        font-size: var(--text-md);
        font-weight: var(--weight-semibold);
        line-height: 1.2;
      }

      .brand-tag {
        font-size: var(--text-2xs);
        color: var(--text-muted);
        line-height: 1.3;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      /* ---- nav ---- */
      .nav-scroll {
        flex: 1 1 auto;
        overflow-y: auto;
        overflow-x: hidden;
        padding: var(--space-3) var(--space-2);
      }

      .nav-group + .nav-group {
        margin-top: var(--space-4);
      }

      .nav-group-label {
        padding: var(--space-2) var(--space-3) var(--space-1);
        font-size: var(--text-2xs);
        font-weight: var(--weight-semibold);
        text-transform: uppercase;
        letter-spacing: 0.07em;
        color: var(--text-muted);
      }

      .nav-group-rule {
        height: 1px;
        margin: var(--space-3) var(--space-2);
        background: var(--border-subtle);
      }

      .nav-link {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        padding: var(--space-2) var(--space-3);
        border-radius: var(--radius-sm);
        color: var(--text-secondary);
        font-size: var(--text-md);
        font-weight: var(--weight-medium);
        text-decoration: none;
        white-space: nowrap;
        position: relative;
        transition: background-color var(--duration-fast) var(--ease-out),
                    color var(--duration-fast) var(--ease-out);
      }

      .collapsed .nav-link {
        justify-content: center;
        padding-inline: 0;
      }

      .nav-link:hover {
        background: var(--surface-hover);
        color: var(--text);
        text-decoration: none;
      }

      .nav-link.active {
        background: var(--accent-subtle-bg);
        color: var(--accent-subtle-fg);
        font-weight: var(--weight-semibold);
      }

      /* Active marker rides the left edge so the state is legible even when
         the sidebar is collapsed and the label is gone. */
      .nav-link.active::before {
        content: '';
        position: absolute;
        left: -8px;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 18px;
        border-radius: 0 var(--radius-pill) var(--radius-pill) 0;
        background: var(--accent);
      }

      .collapsed .nav-link.active::before {
        left: -2px;
      }

      .nav-label {
        overflow: hidden;
        text-overflow: ellipsis;
      }

      /* ---- collapse toggle ---- */
      .collapse-toggle {
        flex: 0 0 auto;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-2);
        height: 40px;
        border-top: 1px solid var(--border-subtle);
        color: var(--text-muted);
        font-size: var(--text-xs);
        transition: color var(--duration-fast) var(--ease-out),
                    background-color var(--duration-fast) var(--ease-out);
      }

      .collapse-toggle:hover {
        background: var(--surface-hover);
        color: var(--text);
      }

      /* ---- mobile: off-canvas ---- */
      @media (max-width: 960px) {
        .sidebar {
          position: fixed;
          left: 0;
          top: 0;
          width: var(--sidebar-width);
          transform: translateX(-100%);
          box-shadow: var(--shadow-xl);
          transition: transform var(--duration-normal) var(--ease-out);
        }

        .sidebar.collapsed {
          width: var(--sidebar-width);
        }

        .sidebar.mobile-open {
          transform: translateX(0);
        }

        .collapsed .nav-link {
          justify-content: flex-start;
          padding-inline: var(--space-3);
        }

        .collapse-toggle {
          display: none;
        }
      }
    `,
  ],
})
export class SidebarComponent {
  readonly collapsed = input(false);
  readonly mobileOpen = input(false);

  readonly toggleCollapsed = output<void>();
  /** Emitted on any link click so the shell can close the mobile drawer. */
  readonly navigated = output<void>();

  protected readonly groups = NAV_GROUPS;
}
