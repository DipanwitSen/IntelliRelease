import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';

import { Page } from '../../core/models/common';
import { AuditEntry } from '../../core/models/delivery';
import { ApiService, AuditQuery } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { CellTemplateDirective, DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { AbsoluteTimePipe } from '../../shared/pipes/relative-time.pipe';

/**
 * The governance record.
 *
 * Timestamps are absolute rather than relative here — "3 hours ago" is useless
 * in a compliance conversation, where the question is always "at exactly what
 * time, in what order".
 */
@Component({
  selector: 'ir-audit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, DataTableComponent, CellTemplateDirective,
    BadgeComponent, AbsoluteTimePipe,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Audit Logs"
        subtitle="An append-only record of every governed action: who did it, to what, and whether it was allowed."
        icon="clipboard-list"
      />

      <ir-data-table
        [columns]="columns"
        [rows]="rows()"
        [loading]="state.showSkeleton()"
        [serverSide]="true"
        [totalCount]="state.data()?.total ?? null"
        noun="audit entries"
        caption="Audit log"
        [trackBy]="trackEntry"
        [searchTerm]="query().q ?? ''"
        (searchTermChange)="onSearch($event)"
        searchPlaceholder="Search actor, action, entity…"
        emptyTitle="No audit entries"
        emptyBody="Entries are written as governed actions happen — approvals, notifications, deployment confirmations and settings changes."
        emptyIcon="clipboard-list"
      >
        <div filters>
          <select class="filter-select" [value]="query().outcome ?? ''" (change)="setFilter('outcome', $event)" aria-label="Outcome">
            <option value="">Any outcome</option>
            <option value="SUCCESS">Success</option>
            <option value="FAILURE">Failure</option>
            <option value="DENIED">Denied</option>
          </select>
        </div>

        <ng-template irCell="outcome" let-row>
          <ir-badge
            [label]="row.outcome"
            [tone]="row.outcome === 'SUCCESS' ? 'success' : row.outcome === 'DENIED' ? 'warning' : 'danger'"
          />
        </ng-template>

        <ng-template irCell="entity" let-row>
          <span class="text-xs">{{ row.entityType }}</span>
          @if (row.entityId) {
            <div class="text-xs muted mono truncate">{{ row.entityId }}</div>
          }
        </ng-template>

        <ng-template irCell="occurredAt" let-row>
          <span class="mono text-xs">{{ row.occurredAt | absoluteTime: true }}</span>
        </ng-template>
      </ir-data-table>
    </div>
  `,
})
export class AuditPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly state = new RequestState<Page<AuditEntry>>();
  protected readonly query = signal<AuditQuery>({ page: 0, size: 200 });

  protected readonly columns: readonly TableColumn<AuditEntry>[] = [
    { id: 'occurredAt', label: 'When', value: (row) => row.occurredAt },
    { id: 'actor', label: 'Actor', value: (row) => row.actor },
    { id: 'action', label: 'Action', value: (row) => row.action },
    { id: 'entity', label: 'Entity', value: (row) => row.entityType, hideBelow: 'sm' },
    { id: 'outcome', label: 'Outcome', value: (row) => row.outcome },
    { id: 'correlationId', label: 'Correlation', mono: true, value: (row) => row.correlationId, hideBelow: 'md' },
  ];

  protected readonly trackEntry = (row: AuditEntry) => row.id;
  protected readonly rows = computed(() => this.state.data()?.items ?? []);

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.listAudit(this.query()));
  }

  protected onSearch(term: string): void {
    this.query.update((current) => ({ ...current, q: term, page: 0 }));
    this.reload();
  }

  protected setFilter(key: keyof AuditQuery, event: Event): void {
    const value = (event.target as HTMLSelectElement).value || undefined;
    this.query.update((current) => ({ ...current, [key]: value, page: 0 }));
    this.reload();
  }
}
