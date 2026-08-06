import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { RepositorySummary } from '../../core/models/delivery';
import { ApiService } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { CellTemplateDirective, DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ErrorPanelComponent } from '../../shared/components/states/states.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { healthTone, scoreTone } from '../../shared/tone';

/** Connected repositories and the health signals derived from their history. */
@Component({
  selector: 'ir-repositories',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, DataTableComponent, CellTemplateDirective, BadgeComponent,
    IconComponent, ErrorPanelComponent, RelativeTimePipe, RouterLink,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Repositories"
        subtitle="Every repository IntelliRelease receives webhooks from, with a deterministic health score derived from its own history."
        icon="repo"
      >
        <div actions>
          <button type="button" class="btn btn-sm btn-secondary" (click)="reload()">
            <ir-icon name="refresh-cw" [size]="14" />
            Refresh
          </button>
        </div>
      </ir-page-header>

      @if (state.error()) {
        <ir-error-panel [message]="state.error()!" (retry)="reload()" />
      } @else {
        <ir-data-table
          [columns]="columns"
          [rows]="state.data() ?? []"
          [loading]="state.showSkeleton()"
          noun="repositories"
          caption="Connected repositories"
          [rowClickable]="true"
          [trackBy]="trackRepo"
          searchPlaceholder="Search repositories…"
          emptyTitle="No repositories connected"
          emptyBody="Point a GitHub webhook at this backend's /webhooks/github endpoint and merge a pull request to register a repository."
          emptyIcon="repo"
          (rowClick)="open($event)"
        >
          <ng-template irCell="name" let-row>
            <a [routerLink]="['/repositories', row.name]" class="weight-medium">{{ row.name }}</a>
            @if (row.description) {
              <div class="text-xs muted truncate">{{ row.description }}</div>
            }
          </ng-template>

          <ng-template irCell="health" let-row>
            <div class="health-cell">
              <div class="row-2">
                <ir-badge [label]="row.health.status" [tone]="healthTone(row.health.status)" />
                <span class="text-xs muted">{{ row.health.score }}/100</span>
              </div>
              <div class="meter">
                <div
                  class="meter-fill"
                  [class]="'meter-fill tone-' + scoreTone(row.health.score)"
                  [style.width.%]="row.health.score"
                ></div>
              </div>
            </div>
          </ng-template>

          <ng-template irCell="webhook" let-row>
            @if (row.webhookConnected) {
              <span class="row-2 fg-success text-xs">
                <ir-icon name="check-circle" [size]="13" />
                Connected
              </span>
            } @else {
              <span class="row-2 fg-warning text-xs" title="No webhook delivery has been seen for this repository">
                <ir-icon name="alert-triangle" [size]="13" />
                Not delivering
              </span>
            }
          </ng-template>

          <ng-template irCell="activity" let-row>{{ row.lastActivityAt | relativeTime }}</ng-template>
        </ir-data-table>
      }
    </div>
  `,
  styles: [
    `
      .health-cell { display: flex; flex-direction: column; gap: var(--space-1); min-width: 120px; }
    `,
  ],
})
export class RepositoriesPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly state = new RequestState<RepositorySummary[]>();

  protected readonly healthTone = healthTone;
  protected readonly scoreTone = scoreTone;

  protected readonly columns: readonly TableColumn<RepositorySummary>[] = [
    { id: 'name', label: 'Repository', value: (row) => row.name },
    { id: 'health', label: 'Health', value: (row) => row.health.score },
    { id: 'openPrCount', label: 'Open PRs', align: 'right', value: (row) => row.openPrCount, hideBelow: 'sm' },
    { id: 'merged', label: 'Merged (30d)', align: 'right', value: (row) => row.mergedPrCount30d, hideBelow: 'sm' },
    { id: 'defaultBranch', label: 'Default branch', mono: true, value: (row) => row.defaultBranch, hideBelow: 'md' },
    { id: 'webhook', label: 'Webhook', value: (row) => (row.webhookConnected ? 'yes' : 'no'), hideBelow: 'md' },
    { id: 'activity', label: 'Last activity', value: (row) => row.lastActivityAt ?? '' },
  ];

  protected readonly trackRepo = (row: RepositorySummary) => row.name;

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.listRepositories());
  }

  protected open(row: RepositorySummary): void {
    void this.router.navigate(['/repositories', row.name]);
  }
}
