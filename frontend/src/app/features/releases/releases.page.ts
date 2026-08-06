import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Page } from '../../core/models/common';
import { CreateReleaseRequest, ReleaseSummary } from '../../core/models/delivery';
import { ApiService, ReleaseQuery } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { ToastService } from '../../core/services/toast.service';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { CellTemplateDirective, DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { readinessTone, releaseStatusTone, riskTone } from '../../shared/tone';

/** Release history, plus the form that cuts a new one from a Git ref range. */
@Component({
  selector: 'ir-releases',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, DataTableComponent, CellTemplateDirective,
    BadgeComponent, IconComponent, RelativeTimePipe, RouterLink, FormsModule,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Release History"
        subtitle="Every release, its resolved contents, its approvals and whether it actually reached an environment."
        icon="history"
      >
        <div actions>
          <button type="button" class="btn btn-sm btn-primary" (click)="showCreate.set(!showCreate())">
            <ir-icon name="plus" [size]="14" />
            New release
          </button>
        </div>
      </ir-page-header>

      @if (showCreate()) {
        <ir-section-card
          title="Create a release"
          subtitle="Contents are resolved from the commit graph — cherry-pick aware and revert-netted — not typed by hand."
          icon="rocket"
        >
          <form class="create-form" (ngSubmit)="create()">
            <label class="field">
              <span class="def-label">Repository</span>
              <input name="repoName" [(ngModel)]="draft.repoName" placeholder="commerce-platform" required />
            </label>
            <label class="field">
              <span class="def-label">Version</span>
              <input name="version" [(ngModel)]="draft.version" placeholder="2026.08.1" required />
            </label>
            <label class="field">
              <span class="def-label">From ref</span>
              <input name="fromRef" [(ngModel)]="draft.fromRef" placeholder="v2026.07.3" required />
            </label>
            <label class="field">
              <span class="def-label">To ref</span>
              <input name="toRef" [(ngModel)]="draft.toRef" placeholder="release/2026.08" required />
            </label>
            <div class="row-2">
              <button type="submit" class="btn btn-primary" [disabled]="busy() || !canCreate()">Create</button>
              <button type="button" class="btn btn-secondary" (click)="showCreate.set(false)">Cancel</button>
            </div>
          </form>
        </ir-section-card>
      }

      <ir-data-table
        [columns]="columns"
        [rows]="rows()"
        [loading]="state.showSkeleton()"
        [serverSide]="true"
        [totalCount]="state.data()?.total ?? null"
        noun="releases"
        caption="Releases"
        [rowClickable]="true"
        [trackBy]="trackRelease"
        [searchTerm]="query().q ?? ''"
        (searchTermChange)="onSearch($event)"
        searchPlaceholder="Search by version or repository…"
        emptyTitle="No releases yet"
        emptyBody="Create one from a Git ref range and IntelliRelease will resolve exactly which merged pull requests it contains."
        emptyIcon="rocket"
        (rowClick)="open($event)"
      >
        <div filters>
          <select class="filter-select" [value]="query().status ?? ''" (change)="setStatus($event)" aria-label="Status">
            <option value="">Any status</option>
            <option value="DRAFT">Draft</option>
            <option value="BUILT">Built</option>
            <option value="PENDING_APPROVAL">Pending approval</option>
            <option value="APPROVED">Approved</option>
            <option value="RELEASED">Released</option>
            <option value="DEPLOYED">Deployed</option>
            <option value="ROLLED_BACK">Rolled back</option>
          </select>
        </div>

        <ng-template irCell="version" let-row>
          <a [routerLink]="['/releases', row.releaseId]" class="weight-medium mono">{{ row.version }}</a>
          <div class="text-xs muted">{{ row.repoName }}</div>
        </ng-template>

        <ng-template irCell="status" let-row>
          <ir-badge [label]="row.status" [tone]="releaseStatusTone(row.status)" />
        </ng-template>

        <ng-template irCell="risk" let-row>
          @if (row.aggregateRiskLevel) {
            <div class="row-2">
              <ir-badge [label]="row.aggregateRiskLevel" [tone]="riskTone(row.aggregateRiskLevel)" />
              <span class="text-xs muted">{{ row.aggregateRiskScore }}</span>
            </div>
          } @else {
            <span class="muted">—</span>
          }
        </ng-template>

        <ng-template irCell="readiness" let-row>
          @if (row.readinessStatus) {
            <ir-badge [label]="row.readinessStatus" [tone]="readinessTone(row.readinessStatus)" />
          } @else {
            <span class="muted">—</span>
          }
        </ng-template>

        <ng-template irCell="created" let-row>{{ row.createdAt | relativeTime }}</ng-template>
      </ir-data-table>
    </div>
  `,
  styles: [
    `
      .create-form {
        display: grid;
        gap: var(--space-4);
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        align-items: end;
      }
      .field { display: flex; flex-direction: column; }
    `,
  ],
})
export class ReleasesPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly state = new RequestState<Page<ReleaseSummary>>();
  protected readonly query = signal<ReleaseQuery>({ page: 0, size: 100 });
  protected readonly showCreate = signal(false);
  protected readonly busy = signal(false);

  protected draft: CreateReleaseRequest = { repoName: '', version: '', fromRef: '', toRef: '' };

  protected readonly riskTone = riskTone;
  protected readonly readinessTone = readinessTone;
  protected readonly releaseStatusTone = releaseStatusTone;

  protected readonly columns: readonly TableColumn<ReleaseSummary>[] = [
    { id: 'version', label: 'Version', value: (row) => row.version },
    { id: 'status', label: 'Status', value: (row) => row.status },
    { id: 'prs', label: 'PRs', align: 'right', value: (row) => row.resolvedPrCount, hideBelow: 'sm' },
    { id: 'risk', label: 'Risk', value: (row) => row.aggregateRiskScore },
    { id: 'readiness', label: 'Readiness', value: (row) => row.readinessScore, hideBelow: 'md' },
    { id: 'created', label: 'Created', value: (row) => row.createdAt ?? '' },
  ];

  protected readonly trackRelease = (row: ReleaseSummary) => row.releaseId;
  protected readonly rows = computed(() => this.state.data()?.items ?? []);

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.listReleases(this.query()));
  }

  protected onSearch(term: string): void {
    this.query.update((current) => ({ ...current, q: term, page: 0 }));
    this.reload();
  }

  protected setStatus(event: Event): void {
    const status = (event.target as HTMLSelectElement).value || undefined;
    this.query.update((current) => ({ ...current, status, page: 0 }));
    this.reload();
  }

  protected canCreate(): boolean {
    return !!(this.draft.repoName && this.draft.version && this.draft.fromRef && this.draft.toRef);
  }

  protected create(): void {
    if (!this.canCreate() || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.createRelease(this.draft).subscribe({
      next: (release) => {
        this.busy.set(false);
        this.showCreate.set(false);
        this.toast.success(`Release ${release.version} created`, 'Build it to resolve its contents from the commit graph.');
        void this.router.navigate(['/releases', release.releaseId]);
      },
      error: (error: { error?: { message?: string } }) => {
        this.busy.set(false);
        this.toast.error('Could not create the release', error?.error?.message);
      },
    });
  }

  protected open(row: ReleaseSummary): void {
    void this.router.navigate(['/releases', row.releaseId]);
  }
}
