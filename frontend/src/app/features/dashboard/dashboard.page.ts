import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { DashboardSnapshot, SystemHealth, TimelineEntry } from '../../core/models/dashboard';
import { ApiService } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { BarListComponent } from '../../shared/components/charts/bar-list.component';
import { TrendChartComponent, TrendSeries } from '../../shared/components/charts/trend-chart.component';
import { CellTemplateDirective, DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { SkeletonComponent, ErrorPanelComponent, EmptyStateComponent } from '../../shared/components/states/states.component';
import { StatTileComponent } from '../../shared/components/stat-tile/stat-tile.component';
import { TimelineComponent, TimelineItem } from '../../shared/components/timeline/timeline.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { healthTone, humanise, readinessTone, riskTone, scoreTone, severityTone } from '../../shared/tone';
import { RepositorySummary } from '../../core/models/delivery';

/**
 * The landing page: delivery, change and integration in one screen.
 *
 * Everything comes from a single `/dashboard` call. Fifteen tiles firing
 * fifteen requests would render in fifteen stages and hammer the backend on
 * every visit; one snapshot means the page paints once, consistently, and the
 * backend gets to compute the whole picture against one point in time.
 */
@Component({
  selector: 'ir-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, StatTileComponent, BarListComponent,
    TrendChartComponent, TimelineComponent, DataTableComponent, CellTemplateDirective,
    BadgeComponent, IconComponent, SkeletonComponent, ErrorPanelComponent,
    EmptyStateComponent, RelativeTimePipe, DecimalPipe, RouterLink,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Dashboard"
        [subtitle]="subtitle()"
        icon="grid"
      >
        <div actions class="row-2">
          <div class="btn-group" role="group" aria-label="Time window">
            @for (option of windowOptions; track option.days) {
              <button
                type="button"
                class="btn btn-sm"
                [attr.aria-pressed]="windowDays() === option.days"
                (click)="setWindow(option.days)"
              >
                {{ option.label }}
              </button>
            }
          </div>
          <button type="button" class="btn btn-sm btn-secondary" (click)="reload()" [disabled]="state.loading()">
            <ir-icon name="refresh-cw" [size]="14" [class.spin]="state.refreshing()" />
            Refresh
          </button>
        </div>
      </ir-page-header>

      @if (state.showSkeleton()) {
        <ir-skeleton variant="kpi" [rows]="8" label="Loading dashboard" />
        <div class="grid grid-2">
          <div class="card"><ir-skeleton [rows]="6" /></div>
          <div class="card"><ir-skeleton [rows]="6" /></div>
        </div>
      } @else if (state.error()) {
        <ir-error-panel
          title="The dashboard could not load"
          [message]="state.error()!"
          (retry)="reload()"
        />
      } @else {
      @if (snapshot(); as data) {
        <!-- ---------------------------------------------------------- KPIs -->
        <section class="grid grid-kpi" aria-label="Key metrics">
          @for (kpi of data.kpis; track kpi.key) {
            <ir-stat-tile [kpi]="kpi" />
          }
        </section>

        <!-- ------------------------------------------------ release posture -->
        <section class="grid grid-3">
          <ir-section-card title="Release readiness" icon="rocket">
            <div class="stack-3">
              <div class="row-2">
                <ir-badge
                  [label]="data.delivery.releaseReadiness.status"
                  [tone]="readinessTone(data.delivery.releaseReadiness.status)"
                  [outlined]="data.delivery.releaseReadiness.status === 'NOT_READY'"
                />
                <span class="score">{{ data.delivery.releaseReadiness.score }}<span class="score-of">/100</span></span>
              </div>

              <div class="meter" role="meter" [attr.aria-valuenow]="data.delivery.releaseReadiness.score">
                <div
                  class="meter-fill"
                  [class]="'meter-fill tone-' + scoreTone(data.delivery.releaseReadiness.score)"
                  [style.width.%]="data.delivery.releaseReadiness.score"
                ></div>
              </div>

              @if (data.delivery.releaseReadiness.nextRelease) {
                <div class="text-xs muted">Next: {{ data.delivery.releaseReadiness.nextRelease }}</div>
              }

              @if (data.delivery.releaseReadiness.blockers.length) {
                <ul class="factor-list">
                  @for (blocker of data.delivery.releaseReadiness.blockers; track blocker) {
                    <li class="fg-danger">
                      <ir-icon name="x-circle" [size]="13" />
                      {{ blocker }}
                    </li>
                  }
                </ul>
              }
              @if (data.delivery.releaseReadiness.warnings.length) {
                <ul class="factor-list">
                  @for (warning of data.delivery.releaseReadiness.warnings; track warning) {
                    <li class="fg-warning">
                      <ir-icon name="alert-triangle" [size]="13" />
                      {{ warning }}
                    </li>
                  }
                </ul>
              }
              @if (!data.delivery.releaseReadiness.blockers.length && !data.delivery.releaseReadiness.warnings.length) {
                <p class="text-sm muted">No blockers or warnings recorded for the next release.</p>
              }
            </div>
          </ir-section-card>

          <ir-section-card title="Deployment" icon="activity">
            <div class="stack-3">
              @if (data.delivery.deploymentStatus.unavailableReason) {
                <p class="text-sm muted">{{ data.delivery.deploymentStatus.unavailableReason }}</p>
              } @else {
                <div class="row-2">
                  <ir-badge [label]="data.delivery.deploymentStatus.current" tone="info" />
                  @if (data.delivery.deploymentStatus.environment) {
                    <span class="chip">{{ data.delivery.deploymentStatus.environment }}</span>
                  }
                </div>
                <div class="def-grid">
                  <div>
                    <div class="def-label">Last deployed</div>
                    <div class="def-value text-sm">{{ data.delivery.deploymentStatus.lastDeployedAt | relativeTime }}</div>
                  </div>
                  <div>
                    <div class="def-label">Version</div>
                    <div class="def-value def-value-mono">{{ data.delivery.deploymentStatus.lastVersion ?? '—' }}</div>
                  </div>
                  <div>
                    <div class="def-label">In flight</div>
                    <div class="def-value">{{ data.delivery.deploymentStatus.inFlight }}</div>
                  </div>
                  <div>
                    <div class="def-label">Failed (24h)</div>
                    <div class="def-value" [class.fg-danger]="data.delivery.deploymentStatus.failed24h > 0">
                      {{ data.delivery.deploymentStatus.failed24h }}
                    </div>
                  </div>
                </div>
              }
            </div>
          </ir-section-card>

          <ir-section-card title="Build &amp; tests" icon="check-circle">
            <div class="stack-3">
              @if (data.delivery.buildStatus.unavailableReason) {
                <p class="text-sm muted">{{ data.delivery.buildStatus.unavailableReason }}</p>
              } @else {
                <div class="row-2">
                  <ir-badge [label]="data.delivery.buildStatus.status" [tone]="buildTone()" />
                  <span class="text-xs muted">{{ data.delivery.buildStatus.lastRunAt | relativeTime }}</span>
                </div>
                <div class="def-grid">
                  <div>
                    <div class="def-label">Passing</div>
                    <div class="def-value fg-success">{{ data.delivery.buildStatus.passed | number }}</div>
                  </div>
                  <div>
                    <div class="def-label">Failing</div>
                    <div class="def-value" [class.fg-danger]="data.delivery.buildStatus.failed > 0">
                      {{ data.delivery.buildStatus.failed | number }}
                    </div>
                  </div>
                  <div>
                    <div class="def-label">Failed tests</div>
                    <div class="def-value" [class.fg-danger]="data.delivery.buildStatus.failedTests > 0">
                      {{ data.delivery.buildStatus.failedTests | number }} / {{ data.delivery.buildStatus.totalTests | number }}
                    </div>
                  </div>
                </div>
              }
            </div>
          </ir-section-card>
        </section>

        <!-- ------------------------------------------------ trends + change -->
        <section class="grid grid-2">
          <ir-section-card
            title="Risk trend"
            [subtitle]="'Deterministic scores over the last ' + data.windowDays + ' days'"
            icon="shield"
          >
            <ir-trend-chart [series]="riskSeries()" ariaLabel="Aggregate risk score over time" />
          </ir-section-card>

          <ir-section-card title="Release timeline" icon="history" [count]="data.releaseTimeline.length">
            @if (data.releaseTimeline.length) {
              <ir-timeline [items]="releaseTimelineItems()" />
            } @else {
              <ir-empty-state
                icon="history"
                title="No releases in this window"
                body="Create a release from a Git ref range and it will appear here."
              />
            }
          </ir-section-card>
        </section>

        <!-- ------------------------------------------------------ what moved -->
        <section class="grid grid-3">
          <ir-section-card title="Changed modules" icon="box" [count]="data.change.changedModules.length">
            <ir-bar-list [entries]="data.change.changedModules" emptyMessage="No module changes captured." />
          </ir-section-card>

          <ir-section-card title="Changed APIs &amp; DTOs" icon="plug">
            <div class="stack-3">
              <div>
                <div class="section-title">APIs</div>
                <ir-bar-list [entries]="data.change.changedApis" [limit]="4" emptyMessage="No API contracts changed." />
              </div>
              <div>
                <div class="section-title">DTOs</div>
                <ir-bar-list [entries]="data.change.changedDtos" [limit]="4" emptyMessage="No DTOs changed." />
              </div>
            </div>
          </ir-section-card>

          <ir-section-card title="Impacted interfaces" icon="network" [count]="data.change.impactedInterfaces.length">
            <ir-bar-list
              [entries]="data.change.impactedInterfaces"
              [clickable]="true"
              emptyMessage="No integration interfaces impacted by changes in this window."
            />
          </ir-section-card>
        </section>

        <!-- --------------------------------------------------- integration -->
        <ir-section-card
          title="Integration health"
          icon="network"
          [subtitle]="data.integration.unavailableReason ?? null"
        >
          <div actions>
            <a routerLink="/integration" class="btn btn-sm btn-ghost">
              Integration Center
              <ir-icon name="chevron-right" [size]="13" />
            </a>
          </div>

          @if (data.integration.unavailableReason) {
            <ir-empty-state
              icon="network"
              title="No integration telemetry connected"
              [body]="data.integration.unavailableReason"
            />
          } @else {
            <div class="grid grid-3">
              @for (system of systems(); track system.key) {
                <div class="system-card">
                  <div class="row-2">
                    <span class="dot" [class]="'dot fg-' + healthTone(system.status)"></span>
                    <span class="weight-semibold">{{ system.label }}</span>
                    <ir-badge class="spacer-left" [label]="system.status" [tone]="healthTone(system.status)" />
                  </div>
                  @if (system.detail) {
                    <p class="text-xs muted">{{ system.detail }}</p>
                  }
                  <div class="row" style="gap: var(--space-4)">
                    @if (system.successRate !== undefined && system.successRate !== null) {
                      <div>
                        <div class="def-label">Success</div>
                        <div class="def-value text-sm">{{ system.successRate | number: '1.0-1' }}%</div>
                      </div>
                    }
                    @if (system.avgResponseMs !== undefined && system.avgResponseMs !== null) {
                      <div>
                        <div class="def-label">Avg</div>
                        <div class="def-value text-sm">{{ system.avgResponseMs | number: '1.0-0' }} ms</div>
                      </div>
                    }
                  </div>
                </div>
              }

              <div class="grid-full">
                <div class="section-title">Top failing interfaces</div>
                <ir-bar-list [entries]="data.integration.topFailures" emptyMessage="No interface failures recorded." />
              </div>
            </div>
          }
        </ir-section-card>

        <!-- ------------------------------------------------------- activity -->
        <section class="grid grid-2">
          <ir-section-card title="Recent activity" icon="activity" [count]="data.recentActivity.length">
            @if (data.recentActivity.length) {
              <ir-timeline [items]="activityItems()" />
            } @else {
              <ir-empty-state icon="activity" title="No activity in this window" />
            }
          </ir-section-card>

          <ir-section-card title="Recent errors" icon="alert-triangle" [count]="data.recentErrors.length">
            <div actions>
              <a routerLink="/errors" class="btn btn-sm btn-ghost">
                Error Intelligence
                <ir-icon name="chevron-right" [size]="13" />
              </a>
            </div>
            @if (data.recentErrors.length) {
              <ir-timeline [items]="errorItems()" />
            } @else {
              <ir-empty-state icon="check-circle" title="No errors recorded" body="Nothing has failed in this window." />
            }
          </ir-section-card>
        </section>

        <!-- --------------------------------------------------- repositories -->
        <ir-data-table
          [columns]="repoColumns"
          [rows]="data.repositoryHealth"
          noun="repositories"
          caption="Repository health"
          [rowClickable]="true"
          [trackBy]="repoTrackBy"
          emptyTitle="No repositories connected"
          emptyBody="Connect a GitHub repository and merge a pull request to populate this."
          emptyIcon="repo"
          (rowClick)="openRepository($event)"
        >
          <ng-template irCell="name" let-row>
            <a [routerLink]="['/repositories', row.name]" class="weight-medium">{{ row.name }}</a>
            @if (!row.webhookConnected) {
              <ir-badge label="No webhook" tone="warning" class="ml-2" />
            }
          </ng-template>

          <ng-template irCell="health" let-row>
            <div class="row-2">
              <ir-badge [label]="row.health.status" [tone]="healthTone(row.health.status)" />
              <span class="text-xs muted">{{ row.health.score }}/100</span>
            </div>
          </ng-template>

          <ng-template irCell="activity" let-row>
            {{ row.lastActivityAt | relativeTime }}
          </ng-template>
        </ir-data-table>
      }
      }
    </div>
  `,
  styles: [
    `
      .score {
        font-size: var(--text-2xl);
        font-weight: var(--weight-semibold);
        line-height: 1;
        margin-left: auto;
      }

      .score-of {
        font-size: var(--text-sm);
        color: var(--text-muted);
        font-weight: var(--weight-normal);
      }

      .factor-list {
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
        font-size: var(--text-sm);
      }

      .factor-list li {
        display: flex;
        align-items: flex-start;
        gap: var(--space-2);
      }

      .factor-list ir-icon { margin-top: 2px; flex: 0 0 auto; }

      .system-card {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding: var(--space-3);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-md);
        background: var(--surface-sunken);
      }

      .spacer-left { margin-left: auto; }
      .ml-2 { margin-left: var(--space-2); }

      .spin { animation: spin 1s linear infinite; }
      @keyframes spin { to { transform: rotate(360deg); } }
    `,
  ],
})
export class DashboardPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly state = new RequestState<DashboardSnapshot>();
  protected readonly snapshot = this.state.data;

  protected readonly windowDays = signal(7);
  protected readonly windowOptions = [
    { days: 1, label: '24h' },
    { days: 7, label: '7d' },
    { days: 30, label: '30d' },
    { days: 90, label: '90d' },
  ];

  /** Re-exported so the template can call them; templates cannot see imports. */
  protected readonly readinessTone = readinessTone;
  protected readonly healthTone = healthTone;
  protected readonly scoreTone = scoreTone;

  protected readonly repoColumns: readonly TableColumn<RepositorySummary>[] = [
    { id: 'name', label: 'Repository', value: (row) => row.name },
    { id: 'health', label: 'Health', value: (row) => row.health.score },
    { id: 'openPrs', label: 'Open PRs', align: 'right', value: (row) => row.openPrCount, hideBelow: 'sm' },
    { id: 'merged', label: 'Merged (30d)', align: 'right', value: (row) => row.mergedPrCount30d, hideBelow: 'sm' },
    { id: 'branch', label: 'Default branch', mono: true, value: (row) => row.defaultBranch, hideBelow: 'md' },
    { id: 'activity', label: 'Last activity', value: (row) => row.lastActivityAt ?? '' },
  ];

  protected readonly repoTrackBy = (row: RepositorySummary) => row.name;

  protected readonly subtitle = computed(() => {
    const data = this.snapshot();
    if (!data) {
      return 'Delivery, risk and integration health across every connected repository.';
    }
    return `Last ${data.windowDays} days · generated ${new Date(data.generatedAt).toLocaleString()}`;
  });

  protected readonly riskSeries = computed<readonly TrendSeries[]>(() => {
    const points = this.snapshot()?.riskTrend ?? [];
    return points.length ? [{ key: 'risk', label: 'Aggregate risk score', points }] : [];
  });

  protected readonly buildTone = computed(() => {
    const status = this.snapshot()?.delivery.buildStatus.status;
    switch (status) {
      case 'PASSED': return 'success' as const;
      case 'FAILED': return 'danger' as const;
      case 'RUNNING': return 'info' as const;
      default: return 'neutral' as const;
    }
  });

  /**
   * Only the systems this landscape actually has. A customer with no
   * middleware gets two cards, not three with one reading "unknown" forever.
   */
  protected readonly systems = computed<readonly SystemHealth[]>(() => {
    const integration = this.snapshot()?.integration;
    if (!integration) {
      return [];
    }
    return [integration.commerce, integration.middleware, integration.targetSystem]
      .filter((system): system is SystemHealth => !!system);
  });

  protected readonly releaseTimelineItems = computed<readonly TimelineItem[]>(() =>
    (this.snapshot()?.releaseTimeline ?? []).map((entry: TimelineEntry) => ({
      id: entry.id,
      title: entry.label,
      detail: entry.detail,
      timestamp: entry.timestamp,
      tone: entry.tone,
      icon: entry.icon ?? 'rocket',
      meta: humanise(entry.status),
      routerLink: entry.routerLink,
    })),
  );

  protected readonly activityItems = computed<readonly TimelineItem[]>(() =>
    (this.snapshot()?.recentActivity ?? []).map((event) => ({
      id: event.id,
      title: event.title,
      detail: event.detail,
      timestamp: event.occurredAt,
      tone: event.severity ? severityTone(event.severity) : 'info',
      icon: iconForActivity(event.kind),
      meta: event.actor ? `by ${event.actor}` : undefined,
    })),
  );

  protected readonly errorItems = computed<readonly TimelineItem[]>(() =>
    (this.snapshot()?.recentErrors ?? []).map((error) => ({
      id: error.id,
      title: error.title,
      detail: error.excerpt,
      timestamp: error.occurredAt,
      tone: severityTone(error.severity),
      icon: 'alert-triangle',
      meta: `${humanise(error.category)} · ${error.layer}${error.count > 1 ? ` · ×${error.count}` : ''}`,
    })),
  );

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.dashboard(this.windowDays()));
  }

  protected setWindow(days: number): void {
    if (this.windowDays() === days) {
      return;
    }
    this.windowDays.set(days);
    this.reload();
  }

  /** Mirrors the anchor in the name cell so the whole row is a target. */
  protected openRepository(repository: RepositorySummary): void {
    void this.router.navigate(['/repositories', repository.name]);
  }

  protected riskTone = riskTone;
}

function iconForActivity(kind: string): string {
  switch (kind) {
    case 'PR_MERGED': return 'git-pull-request';
    case 'PR_ANALYZED': return 'sparkles';
    case 'RELEASE_CREATED':
    case 'RELEASE_BUILT': return 'rocket';
    case 'RELEASE_APPROVED': return 'check-circle';
    case 'RELEASE_NOTIFIED': return 'send';
    case 'DEPLOYMENT': return 'activity';
    case 'ROLLBACK': return 'repeat';
    case 'ERROR': return 'alert-triangle';
    case 'DRIFT_DETECTED': return 'alert-circle';
    case 'INTERFACE_CHANGED': return 'network';
    case 'SETTINGS_CHANGED': return 'settings';
    default: return 'clock';
  }
}
