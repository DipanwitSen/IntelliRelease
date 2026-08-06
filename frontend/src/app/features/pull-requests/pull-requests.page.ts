import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Page } from '../../core/models/common';
import { PullRequestSummary } from '../../core/models/delivery';
import { ApiService, PullRequestQuery } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { CellTemplateDirective, DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { readinessTone, riskTone } from '../../shared/tone';

/**
 * Every captured pull request, with the deterministic verdict attached.
 *
 * Filtering is server-side because this list is the one that grows without
 * bound — a busy programme merges thousands of PRs a quarter, and pulling them
 * all into the browser to filter locally stops working long before that.
 */
@Component({
  selector: 'ir-pull-requests',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, DataTableComponent, CellTemplateDirective, BadgeComponent,
    IconComponent, RelativeTimePipe, RouterLink,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Pull Requests"
        subtitle="Captured changes with their SAP Commerce meaning, deterministic risk score and readiness verdict."
        icon="git-pull-request"
      >
        <div actions>
          <button type="button" class="btn btn-sm btn-secondary" (click)="reload()">
            <ir-icon name="refresh-cw" [size]="14" />
            Refresh
          </button>
        </div>
      </ir-page-header>

      <ir-data-table
        [columns]="columns"
        [rows]="rows()"
        [loading]="state.showSkeleton()"
        [serverSide]="true"
        [totalCount]="state.data()?.total ?? null"
        noun="pull requests"
        caption="Captured pull requests"
        [rowClickable]="true"
        [trackBy]="trackPr"
        [searchTerm]="query().q ?? ''"
        (searchTermChange)="onSearch($event)"
        searchPlaceholder="Search by title, author, ticket…"
        emptyTitle="No pull requests captured"
        emptyBody="IntelliRelease captures pull requests from GitHub webhooks. Merge one against a connected repository and it will appear here."
        emptyIcon="git-pull-request"
        [footerNote]="footerNote()"
        (rowClick)="open($event)"
      >
        <div filters class="row-2 row-wrap">
          <select class="filter-select" [value]="query().riskLevel ?? ''" (change)="setFilter('riskLevel', $event)" aria-label="Risk level">
            <option value="">Any risk</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>

          <select class="filter-select" [value]="analyzedFilter()" (change)="setAnalyzed($event)" aria-label="Analysis state">
            <option value="">Analysed or not</option>
            <option value="true">Analysed</option>
            <option value="false">Awaiting analysis</option>
          </select>

          <label class="row-2 text-sm secondary">
            <input type="checkbox" [checked]="query().integrationOnly === true" (change)="toggleIntegration($event)" />
            Integration-touching only
          </label>

          @if (hasFilters()) {
            <button type="button" class="btn btn-sm btn-ghost" (click)="clearFilters()">
              <ir-icon name="x" [size]="13" />
              Clear
            </button>
          }
        </div>

        <ng-template irCell="title" let-row>
          <a [routerLink]="['/pull-requests', row.prId]" class="weight-medium">{{ row.title }}</a>
          <div class="row-2 text-xs muted">
            <span class="mono">#{{ row.prNumber }}</span>
            <span>{{ row.repoName }}</span>
            @if (row.ticketKey) {
              <span class="chip chip-mono">{{ row.ticketKey }}</span>
            }
            @if (row.integrationTouched) {
              <span class="row-2 fg-info">
                <ir-icon name="network" [size]="11" />
                integration
              </span>
            }
          </div>
        </ng-template>

        <ng-template irCell="risk" let-row>
          @if (row.analyzed) {
            <div class="row-2">
              <ir-badge
                [label]="row.riskLevel"
                [tone]="riskTone(row.riskLevel)"
                [outlined]="row.riskLevel === 'CRITICAL'"
              />
              <span class="text-xs muted">{{ row.riskScore }}</span>
            </div>
          } @else {
            <span class="text-xs muted">Not analysed</span>
          }
        </ng-template>

        <ng-template irCell="readiness" let-row>
          @if (row.deploymentReadinessStatus) {
            <ir-badge [label]="row.deploymentReadinessStatus" [tone]="readinessTone(row.deploymentReadinessStatus)" />
          } @else {
            <span class="muted">—</span>
          }
        </ng-template>

        <ng-template irCell="merged" let-row>
          {{ row.mergedAt | relativeTime }}
        </ng-template>
      </ir-data-table>
    </div>
  `,
})
export class PullRequestsPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly state = new RequestState<Page<PullRequestSummary>>();
  protected readonly query = signal<PullRequestQuery>({ page: 0, size: 100 });

  protected readonly riskTone = riskTone;
  protected readonly readinessTone = readinessTone;

  protected readonly columns: readonly TableColumn<PullRequestSummary>[] = [
    { id: 'title', label: 'Pull request', value: (row) => row.title },
    { id: 'author', label: 'Author', value: (row) => row.author, hideBelow: 'sm' },
    { id: 'risk', label: 'Risk', value: (row) => row.riskScore },
    { id: 'readiness', label: 'Readiness', value: (row) => row.deploymentReadinessScore, hideBelow: 'md' },
    { id: 'files', label: 'Files', align: 'right', value: (row) => row.changedFileCount, hideBelow: 'md' },
    { id: 'merged', label: 'Merged', value: (row) => row.mergedAt ?? '' },
  ];

  protected readonly trackPr = (row: PullRequestSummary) => row.prId;

  protected readonly rows = computed(() => this.state.data()?.items ?? []);

  protected readonly analyzedFilter = computed(() => {
    const value = this.query().analyzed;
    return value === undefined ? '' : String(value);
  });

  protected readonly hasFilters = computed(() => {
    const query = this.query();
    return !!(query.q || query.riskLevel || query.analyzed !== undefined || query.integrationOnly);
  });

  protected readonly footerNote = computed(() => {
    const unanalysed = this.rows().filter((row) => !row.analyzed).length;
    if (!unanalysed) {
      return null;
    }
    return `${unanalysed} pull request${unanalysed === 1 ? '' : 's'} awaiting analysis. Analysis runs asynchronously from the job queue; risk and readiness appear once it completes.`;
  });

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.listPullRequests(this.query()));
  }

  protected onSearch(term: string): void {
    this.query.update((current) => ({ ...current, q: term, page: 0 }));
    this.reload();
  }

  protected setFilter(key: keyof PullRequestQuery, event: Event): void {
    const value = (event.target as HTMLSelectElement).value || undefined;
    this.query.update((current) => ({ ...current, [key]: value, page: 0 }));
    this.reload();
  }

  protected setAnalyzed(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    const analyzed = raw === '' ? undefined : raw === 'true';
    this.query.update((current) => ({ ...current, analyzed, page: 0 }));
    this.reload();
  }

  protected toggleIntegration(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.query.update((current) => ({ ...current, integrationOnly: checked || undefined, page: 0 }));
    this.reload();
  }

  protected clearFilters(): void {
    this.query.set({ page: 0, size: 100 });
    this.reload();
  }

  protected open(row: PullRequestSummary): void {
    void this.router.navigate(['/pull-requests', row.prId]);
  }
}
