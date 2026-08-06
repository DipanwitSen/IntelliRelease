import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';

import { Page } from '../../core/models/common';
import { DeploymentEvent } from '../../core/models/delivery';
import { ApiService, DeploymentQuery } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { TimelineComponent, TimelineItem } from '../../shared/components/timeline/timeline.component';
import { DurationPipe } from '../../shared/pipes/relative-time.pipe';
import { deploymentTone, humanise, riskTone } from '../../shared/tone';

/** What went out, when, to where — and what happened next. */
@Component({
  selector: 'ir-deployments',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, TimelineComponent, BadgeComponent,
    IconComponent, SkeletonComponent, ErrorPanelComponent, EmptyStateComponent, DurationPipe,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Deployment Timeline"
        subtitle="Deployment events in order, with the release each one carried and the outcome it produced."
        icon="activity"
      >
        <div actions class="row-2">
          <select class="filter-select" [value]="query().environment ?? ''" (change)="setFilter('environment', $event)" aria-label="Environment">
            <option value="">All environments</option>
            @for (environment of environments(); track environment) {
              <option [value]="environment">{{ environment }}</option>
            }
          </select>
          <select class="filter-select" [value]="query().status ?? ''" (change)="setFilter('status', $event)" aria-label="Status">
            <option value="">Any outcome</option>
            <option value="SUCCEEDED">Succeeded</option>
            <option value="FAILED">Failed</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="ROLLED_BACK">Rolled back</option>
          </select>
          <button type="button" class="btn btn-sm btn-secondary" (click)="reload()">
            <ir-icon name="refresh-cw" [size]="14" />
            Refresh
          </button>
        </div>
      </ir-page-header>

      @if (state.showSkeleton()) {
        <div class="card"><ir-skeleton variant="list" [rows]="6" /></div>
      } @else if (state.error()) {
        <ir-error-panel [message]="state.error()!" (retry)="reload()" />
      } @else {
        <section class="grid grid-4">
          <div class="stat">
            <span class="def-label">Deployments</span>
            <span class="stat-value">{{ events().length }}</span>
          </div>
          <div class="stat">
            <span class="def-label">Succeeded</span>
            <span class="stat-value fg-success">{{ countBy('SUCCEEDED') }}</span>
          </div>
          <div class="stat">
            <span class="def-label">Failed</span>
            <span class="stat-value" [class.fg-danger]="countBy('FAILED') > 0">{{ countBy('FAILED') }}</span>
          </div>
          <div class="stat">
            <span class="def-label">Rolled back</span>
            <span class="stat-value" [class.fg-warning]="countBy('ROLLED_BACK') > 0">{{ countBy('ROLLED_BACK') }}</span>
          </div>
        </section>

        <ir-section-card title="Timeline" icon="history" [count]="events().length">
          @if (events().length) {
            <ir-timeline [items]="items()" />
          } @else {
            <ir-empty-state
              icon="rocket"
              title="No deployments recorded"
              body="Deployment is human-asserted in this build — confirm one from a release and it will appear here."
            />
          }
        </ir-section-card>

        <ir-section-card title="Detail" icon="list" [collapsible]="true" [expanded]="false">
          <div class="stack-2">
            @for (event of events(); track event.id) {
              <div class="detail-row">
                <ir-badge [label]="event.status" [tone]="deploymentTone(event.status)" />
                <span class="mono weight-medium">{{ event.version }}</span>
                <span class="chip">{{ event.environment }}</span>
                <span class="text-xs muted">{{ event.repoName }}</span>
                @if (event.riskLevel) {
                  <ir-badge [label]="event.riskLevel" [tone]="riskTone(event.riskLevel)" />
                }
                @if (event.durationMs) {
                  <span class="text-xs muted">{{ event.durationMs | duration }}</span>
                }
                <span class="spacer"></span>
                <span class="text-xs muted">by {{ event.triggeredBy }}</span>
              </div>
            }
          </div>
        </ir-section-card>
      }
    </div>
  `,
  styles: [
    `
      .stat {
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
        padding: var(--space-4);
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius-lg);
      }
      .stat-value { font-size: var(--text-xl); font-weight: var(--weight-semibold); }
      .detail-row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        flex-wrap: wrap;
        padding: var(--space-2) var(--space-3);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-sm);
      }
    `,
  ],
})
export class DeploymentsPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly state = new RequestState<Page<DeploymentEvent>>();
  protected readonly query = signal<DeploymentQuery>({ page: 0, size: 200 });

  protected readonly deploymentTone = deploymentTone;
  protected readonly riskTone = riskTone;

  protected readonly events = computed(() => this.state.data()?.items ?? []);

  protected readonly environments = computed(() =>
    [...new Set(this.events().map((event) => event.environment))].sort(),
  );

  protected readonly items = computed<readonly TimelineItem[]>(() =>
    this.events().map((event) => ({
      id: event.id,
      title: `${event.version} → ${event.environment}`,
      detail: event.notes,
      timestamp: event.startedAt,
      tone: deploymentTone(event.status),
      icon: event.status === 'ROLLED_BACK' ? 'repeat' : 'rocket',
      meta: `${humanise(event.status)} · ${event.repoName} · triggered by ${event.triggeredBy}`,
      routerLink: event.releaseId ? ['/releases', event.releaseId] : undefined,
    })),
  );

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.listDeployments(this.query()));
  }

  protected setFilter(key: keyof DeploymentQuery, event: Event): void {
    const value = (event.target as HTMLSelectElement).value || undefined;
    this.query.update((current) => ({ ...current, [key]: value, page: 0 }));
    this.reload();
  }

  protected countBy(status: string): number {
    return this.events().filter((event) => event.status === status).length;
  }
}
