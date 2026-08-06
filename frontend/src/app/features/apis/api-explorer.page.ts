import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';

import { Page } from '../../core/models/common';
import { ApiDefinition } from '../../core/models/integration';
import { ApiCatalogQuery, ApiService } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent, ProvenanceBadgeComponent } from '../../shared/components/badge/badge.component';
import { CellTemplateDirective, DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent } from '../../shared/components/states/states.component';
import { humanise } from '../../shared/tone';

/** REST, SOAP, OData, OCC and webhook contracts, with what changed marked. */
@Component({
  selector: 'ir-api-explorer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, DataTableComponent, CellTemplateDirective,
    BadgeComponent, ProvenanceBadgeComponent, IconComponent, EmptyStateComponent,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="API Explorer"
        subtitle="Every contract this landscape exposes or consumes — and which of them a change in the current window touched."
        icon="plug"
      />

      <ir-data-table
        [columns]="columns"
        [rows]="rows()"
        [loading]="state.showSkeleton()"
        [serverSide]="true"
        [totalCount]="state.data()?.total ?? null"
        noun="APIs"
        caption="API catalogue"
        [rowClickable]="true"
        [trackBy]="trackApi"
        [searchTerm]="query().q ?? ''"
        (searchTermChange)="onSearch($event)"
        searchPlaceholder="Search APIs, paths, operations…"
        emptyTitle="No APIs catalogued"
        emptyBody="APIs are discovered from OpenAPI, WSDL and EDMX artifacts in your repositories, plus the curated OCC catalogue."
        emptyIcon="plug"
        (rowClick)="select($event)"
      >
        <div filters class="row-2 row-wrap">
          <select class="filter-select" [value]="query().kind ?? ''" (change)="setFilter('kind', $event)" aria-label="Kind">
            <option value="">All kinds</option>
            <option value="REST">REST</option>
            <option value="SOAP">SOAP</option>
            <option value="ODATA">OData</option>
            <option value="OCC">OCC</option>
            <option value="GRAPHQL">GraphQL</option>
            <option value="WEBHOOK">Webhook</option>
          </select>
          <label class="row-2 text-sm secondary">
            <input type="checkbox" [checked]="query().changedOnly === true" (change)="toggleChanged($event)" />
            Changed in this window
          </label>
        </div>

        <ng-template irCell="name" let-row>
          <span class="weight-medium">{{ row.name }}</span>
          @if (row.changedInWindow) {
            <span class="badge tone-warning" style="margin-left: var(--space-2)">changed</span>
          }
          <div class="text-xs muted clamp-2">{{ row.description }}</div>
        </ng-template>

        <ng-template irCell="kind" let-row>
          <ir-badge [label]="row.kind" tone="accent" [humanize]="false" />
        </ng-template>

        <ng-template irCell="spec" let-row>
          @if (row.specFormat && row.specFormat !== 'NONE') {
            <span class="chip chip-mono">{{ row.specFormat }}</span>
          } @else {
            <span class="muted">—</span>
          }
        </ng-template>
      </ir-data-table>

      @if (selected(); as api) {
        <ir-section-card [title]="api.name" [subtitle]="api.description" icon="file-code" [count]="api.operations.length">
          <div actions class="row-2">
            <ir-badge [label]="api.kind" tone="accent" [humanize]="false" />
            <ir-provenance [value]="api.provenance" />
            <button type="button" class="btn btn-ghost btn-sm btn-icon" (click)="selected.set(null)" aria-label="Close">
              <ir-icon name="x" [size]="14" />
            </button>
          </div>

          <div class="def-grid" style="margin-bottom: var(--space-4)">
            <div>
              <div class="def-label">Base path</div>
              <div class="def-value def-value-mono">{{ api.basePath ?? '—' }}</div>
            </div>
            <div>
              <div class="def-label">System</div>
              <div class="def-value">{{ api.system }}</div>
            </div>
            <div>
              <div class="def-label">Version</div>
              <div class="def-value def-value-mono">{{ api.version ?? '—' }}</div>
            </div>
            <div>
              <div class="def-label">Authentication</div>
              <div class="def-value">{{ humanise(api.auth) }}</div>
            </div>
          </div>

          @if (api.operations.length) {
            <div class="stack-2">
              @for (operation of api.operations; track operation.id) {
                <div class="operation" [class.changed]="operation.changed">
                  <div class="row-2 row-wrap">
                    @if (operation.method) {
                      <span class="badge tone-accent" [class.tone-danger]="operation.method === 'DELETE'">{{ operation.method }}</span>
                    }
                    <span class="mono text-xs">{{ operation.path ?? operation.name }}</span>
                    @if (operation.deprecated) {
                      <span class="badge tone-warning">deprecated</span>
                    }
                    @if (operation.changed) {
                      <span class="badge tone-warning">changed</span>
                    }
                  </div>
                  <p class="text-sm secondary">{{ operation.summary }}</p>
                </div>
              }
            </div>
          } @else {
            <ir-empty-state icon="list" title="No operations recorded" body="This API is catalogued but its operations have not been parsed from a spec." />
          }
        </ir-section-card>
      }
    </div>
  `,
  styles: [
    `
      .operation {
        display: flex; flex-direction: column; gap: var(--space-1);
        padding: var(--space-2) var(--space-3);
        border: 1px solid var(--border-subtle); border-radius: var(--radius-sm);
      }
      .operation.changed { border-color: var(--warning-fg); background: var(--warning-bg); }
    `,
  ],
})
export class ApiExplorerPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly state = new RequestState<Page<ApiDefinition>>();
  protected readonly query = signal<ApiCatalogQuery>({ page: 0, size: 200 });
  protected readonly selected = signal<ApiDefinition | null>(null);

  protected readonly humanise = humanise;

  protected readonly columns: readonly TableColumn<ApiDefinition>[] = [
    { id: 'name', label: 'API', value: (row) => row.name },
    { id: 'kind', label: 'Kind', value: (row) => row.kind },
    { id: 'system', label: 'System', value: (row) => row.system, hideBelow: 'sm' },
    { id: 'basePath', label: 'Base path', mono: true, value: (row) => row.basePath, hideBelow: 'md' },
    { id: 'operations', label: 'Ops', align: 'right', value: (row) => row.operations.length, hideBelow: 'sm' },
    { id: 'spec', label: 'Spec', value: (row) => row.specFormat, hideBelow: 'md' },
  ];

  protected readonly trackApi = (row: ApiDefinition) => row.id;
  protected readonly rows = computed(() => this.state.data()?.items ?? []);

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.listApis(this.query()));
  }

  protected onSearch(term: string): void {
    this.query.update((current) => ({ ...current, q: term, page: 0 }));
    this.reload();
  }

  protected setFilter(key: keyof ApiCatalogQuery, event: Event): void {
    const value = (event.target as HTMLSelectElement).value || undefined;
    this.query.update((current) => ({ ...current, [key]: value, page: 0 }));
    this.reload();
  }

  protected toggleChanged(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.query.update((current) => ({ ...current, changedOnly: checked || undefined, page: 0 }));
    this.reload();
  }

  protected select(row: ApiDefinition): void {
    this.selected.set(row);
    this.api.getApi(row.id).subscribe({
      next: (full) => this.selected.set(full),
      error: () => {
        // The summary row is already rendered; keep it rather than blanking out.
      },
    });
  }
}
