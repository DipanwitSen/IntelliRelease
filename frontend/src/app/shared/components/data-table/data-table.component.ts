import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy, Component, Directive, TemplateRef,
  computed, contentChildren, inject, input, model, output, signal,
} from '@angular/core';

import { EmptyStateComponent, SkeletonComponent } from '../states/states.component';
import { IconComponent } from '../icon/icon.component';

/** Column definition. `value` powers default rendering, sorting and search. */
export interface TableColumn<T = unknown> {
  readonly id: string;
  readonly label: string;
  readonly sortable?: boolean;
  readonly align?: 'left' | 'right' | 'center';
  readonly width?: string;
  readonly mono?: boolean;
  /** Extracts the sortable/searchable scalar. Omit for template-only columns. */
  readonly value?: (row: T) => string | number | null | undefined;
  /** Drops the column on narrow screens rather than letting the table overflow. */
  readonly hideBelow?: 'sm' | 'md';
  readonly tooltip?: string;
}

export interface SortState {
  readonly columnId: string;
  readonly direction: 'asc' | 'desc';
}

/**
 * Marks a custom cell renderer for one column:
 *
 * ```html
 * <ng-template irCell="risk" let-row>
 *   <ir-badge [label]="row.riskLevel" [tone]="riskTone(row.riskLevel)" />
 * </ng-template>
 * ```
 */
@Directive({ selector: '[irCell]', standalone: true })
export class CellTemplateDirective {
  readonly irCell = input.required<string>();
  readonly template = inject(TemplateRef<{ $implicit: unknown; index: number }>);
}

/**
 * The searchable, sortable table used by every list in the product.
 *
 * Sorting and filtering are client-side by default, which is right for the
 * page-sized result sets these endpoints return. When a list outgrows that,
 * set `serverSide` — the component then emits `sortChange` / `searchChange`
 * and renders exactly the rows it is given, so the same markup works either
 * way and no page has to be rewritten to move the work to the backend.
 */
@Component({
  selector: 'ir-data-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, IconComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <div class="table-card card">
      @if (showToolbar()) {
        <div class="toolbar">
          @if (searchable()) {
            <label class="search-field">
              <span class="sr-only">{{ searchLabel() }}</span>
              <ir-icon name="search" [size]="15" />
              <input
                type="search"
                [placeholder]="searchPlaceholder()"
                [value]="searchTerm()"
                (input)="onSearch($event)"
                autocomplete="off"
              />
            </label>
          }

          <ng-content select="[filters]" />

          <span class="toolbar-spacer"></span>

          <span class="result-count">
            {{ resultLabel() }}
          </span>

          <ng-content select="[toolbarActions]" />
        </div>
      }

      <div class="table-wrap">
        @if (loading()) {
          <ir-skeleton variant="table" [rows]="skeletonRows()" [label]="'Loading ' + noun()" />
        } @else if (!displayRows().length) {
          <ir-empty-state
            [icon]="emptyIcon()"
            [title]="searchTerm() ? 'No ' + noun() + ' match “' + searchTerm() + '”' : emptyTitle()"
            [body]="searchTerm() ? 'Try a shorter search, or clear the filters above.' : emptyBody()"
          >
            @if (searchTerm()) {
              <button type="button" class="btn btn-sm btn-secondary" (click)="clearSearch()">Clear search</button>
            }
          </ir-empty-state>
        } @else {
          <table class="data-table">
            <caption class="sr-only">{{ caption() || noun() }}</caption>
            <thead>
              <tr>
                @for (column of columns(); track column.id) {
                  <th
                    [style.width]="column.width || null"
                    [class]="cellClass(column)"
                    [attr.aria-sort]="ariaSort(column)"
                    [title]="column.tooltip || null"
                    scope="col"
                  >
                    @if (column.sortable !== false && column.value) {
                      <button type="button" class="th-sort" (click)="toggleSort(column.id)">
                        {{ column.label }}
                        <ir-icon [name]="sortIcon(column.id)" [size]="12" class="sort-icon" />
                      </button>
                    } @else {
                      {{ column.label }}
                    }
                  </th>
                }
              </tr>
            </thead>

            <tbody>
              @for (row of displayRows(); track trackRow(row, $index); let index = $index) {
                <tr
                  [class.row-clickable]="rowClickable()"
                  [class.row-selected]="isSelected(row)"
                  (click)="onRowClick(row)"
                  [attr.tabindex]="rowClickable() ? 0 : null"
                  (keydown.enter)="onRowClick(row)"
                  (keydown.space)="onRowClick(row); $event.preventDefault()"
                >
                  @for (column of columns(); track column.id) {
                    <td [class]="cellClass(column)">
                      @if (templateFor(column.id); as template) {
                        <ng-container
                          *ngTemplateOutlet="template; context: { $implicit: row, index: index }"
                        />
                      } @else {
                        {{ display(column, row) }}
                      }
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        }
      </div>

      @if (footerNote()) {
        <div class="card-footer">{{ footerNote() }}</div>
      }
    </div>
  `,
  styles: [
    `
      :host { display: block; min-width: 0; }

      .table-card { overflow: hidden; }

      .result-count {
        font-size: var(--text-xs);
        color: var(--text-muted);
        white-space: nowrap;
        font-variant-numeric: tabular-nums;
      }

      /* Columns drop out rather than forcing a horizontal scroll on phones —
         the wrapper still scrolls if the remaining columns are too wide. */
      @media (max-width: 640px) {
        .hide-sm { display: none; }
      }

      @media (max-width: 900px) {
        .hide-md { display: none; }
      }
    `,
  ],
})
export class DataTableComponent<T> {
  readonly columns = input.required<readonly TableColumn<T>[]>();
  readonly rows = input<readonly T[]>([]);
  readonly loading = input(false);

  /** Plural noun for empty states and counts: "pull requests", "interfaces". */
  readonly noun = input('rows');
  readonly caption = input<string | null>(null);
  readonly emptyTitle = input('Nothing captured yet');
  readonly emptyBody = input<string | null>(null);
  readonly emptyIcon = input('inbox');
  readonly footerNote = input<string | null>(null);

  readonly searchable = input(true);
  readonly searchPlaceholder = input('Search…');
  readonly searchLabel = input('Search');
  readonly showToolbar = input(true);
  readonly skeletonRows = input(6);

  readonly rowClickable = input(false);
  /** Stable identity for `track` and selection. Falls back to the index. */
  readonly trackBy = input<((row: T) => string | number) | null>(null);
  readonly selectedId = input<string | number | null>(null);

  /** When true the component stops filtering/sorting and just renders `rows`. */
  readonly serverSide = input(false);
  /** Total across all pages; shown in the count when paging server-side. */
  readonly totalCount = input<number | null>(null);

  /**
   * Named `searchTerm` rather than `search`: `search` is a native DOM event
   * name, so Angular resolves `(search)="..."` on this host to the DOM event
   * and hands the handler an `Event` instead of the string the model emits.
   */
  readonly searchTerm = model('');
  readonly sort = model<SortState | null>(null);

  readonly rowClick = output<T>();

  private readonly cellTemplates = contentChildren(CellTemplateDirective);

  private readonly normalisedTerm = computed(() => this.searchTerm().trim().toLowerCase());

  /**
   * Filter, then sort. Both are skipped entirely in server-side mode so the
   * backend's ordering is never silently re-applied on top of itself.
   */
  protected readonly displayRows = computed<readonly T[]>(() => {
    if (this.serverSide()) {
      return this.rows();
    }

    const term = this.normalisedTerm();
    const columns = this.columns();

    let rows = this.rows();
    if (term) {
      rows = rows.filter((row) =>
        columns.some((column) => {
          const value = column.value?.(row);
          return value !== null && value !== undefined && String(value).toLowerCase().includes(term);
        }),
      );
    }

    const sort = this.sort();
    if (!sort) {
      return rows;
    }

    const column = columns.find((entry) => entry.id === sort.columnId);
    if (!column?.value) {
      return rows;
    }

    const direction = sort.direction === 'asc' ? 1 : -1;
    const extract = column.value;

    return [...rows].sort((left, right) => {
      const a = extract(left);
      const b = extract(right);

      // Empties are pinned to the bottom *before* direction is applied, so an
      // unanalysed PR never floats to the top of "highest risk first" purely
      // because it has no score.
      const aEmpty = isEmpty(a);
      const bEmpty = isEmpty(b);
      if (aEmpty && bEmpty) return 0;
      if (aEmpty) return 1;
      if (bEmpty) return -1;

      return compare(a, b) * direction;
    });
  });

  protected readonly resultLabel = computed(() => {
    const shown = this.displayRows().length;
    const total = this.totalCount() ?? (this.serverSide() ? shown : this.rows().length);
    if (shown === total) {
      return `${total.toLocaleString()} ${this.noun()}`;
    }
    return `${shown.toLocaleString()} of ${total.toLocaleString()} ${this.noun()}`;
  });

  protected onSearch(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value);
  }

  protected clearSearch(): void {
    this.searchTerm.set('');
  }

  /** asc -> desc -> unsorted, so a user can always get back to the default order. */
  protected toggleSort(columnId: string): void {
    const current = this.sort();
    if (current?.columnId !== columnId) {
      this.sort.set({ columnId, direction: 'asc' });
      return;
    }
    this.sort.set(current.direction === 'asc' ? { columnId, direction: 'desc' } : null);
  }

  protected sortIcon(columnId: string): string {
    const current = this.sort();
    if (current?.columnId !== columnId) {
      return 'chevrons-up-down';
    }
    return current.direction === 'asc' ? 'chevron-up' : 'chevron-down';
  }

  protected ariaSort(column: TableColumn<T>): string | null {
    if (column.sortable === false || !column.value) {
      return null;
    }
    const current = this.sort();
    if (current?.columnId !== column.id) {
      return 'none';
    }
    return current.direction === 'asc' ? 'ascending' : 'descending';
  }

  protected cellClass(column: TableColumn<T>): string {
    const classes: string[] = [];
    if (column.align === 'right') classes.push('col-num');
    if (column.align === 'center') classes.push('col-center');
    if (column.mono) classes.push('col-mono');
    if (column.hideBelow === 'sm') classes.push('hide-sm');
    if (column.hideBelow === 'md') classes.push('hide-md');
    return classes.join(' ');
  }

  protected templateFor(columnId: string): TemplateRef<{ $implicit: T; index: number }> | null {
    const match = this.cellTemplates().find((directive) => directive.irCell() === columnId);
    return (match?.template as TemplateRef<{ $implicit: T; index: number }>) ?? null;
  }

  protected display(column: TableColumn<T>, row: T): string {
    const value = column.value?.(row);
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    return String(value);
  }

  protected trackRow(row: T, index: number): string | number {
    return this.trackBy()?.(row) ?? index;
  }

  protected isSelected(row: T): boolean {
    const selected = this.selectedId();
    return selected !== null && this.trackBy()?.(row) === selected;
  }

  protected onRowClick(row: T): void {
    if (this.rowClickable()) {
      this.rowClick.emit(row);
    }
  }
}

function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === '';
}

/**
 * `numeric: true` so "v1.10.0" sorts after "v1.9.0" rather than before it —
 * release versions and PR numbers are everywhere in this product.
 */
function compare(left: unknown, right: unknown): number {
  if (typeof left === 'number' && typeof right === 'number') {
    return left - right;
  }
  return String(left).localeCompare(String(right), undefined, { numeric: true, sensitivity: 'base' });
}
