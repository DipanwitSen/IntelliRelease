import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { RepositoryDetail } from '../../core/models/delivery';
import { ApiService } from '../../core/services/api.service';
import { BreadcrumbService } from '../../core/services/breadcrumb.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { BarListComponent } from '../../shared/components/charts/bar-list.component';
import { TrendChartComponent, TrendSeries } from '../../shared/components/charts/trend-chart.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { healthTone, riskTone, scoreTone } from '../../shared/tone';

/** One repository: health, contributors, risk trend and recent changes. */
@Component({
  selector: 'ir-repository-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BarListComponent, TrendChartComponent,
    BadgeComponent, IconComponent, SkeletonComponent, ErrorPanelComponent,
    EmptyStateComponent, RelativeTimePipe, RouterLink,
  ],
  template: `
    <div class="page">
      @if (state.showSkeleton()) {
        <div class="card"><ir-skeleton [rows]="8" /></div>
      } @else if (state.error()) {
        <ir-error-panel [message]="state.error()!" (retry)="reload()" />
      } @else {
      @if (state.data(); as repo) {
        <ir-page-header [title]="repo.name" [subtitle]="repo.description ?? null" icon="repo">
          <div actions class="row-2">
            <ir-badge [label]="repo.health.status" [tone]="healthTone(repo.health.status)" />
            <span class="chip chip-mono">{{ repo.defaultBranch }}</span>
          </div>
        </ir-page-header>

        <section class="grid grid-4">
          <div class="card"><div class="card-body">
            <div class="def-label">Health score</div>
            <div class="big">{{ repo.health.score }}<span class="of">/100</span></div>
            <div class="meter" style="margin-top: var(--space-2)">
              <div class="meter-fill" [class]="'meter-fill tone-' + scoreTone(repo.health.score)" [style.width.%]="repo.health.score"></div>
            </div>
          </div></div>
          <div class="card"><div class="card-body">
            <div class="def-label">Open PRs</div>
            <div class="big">{{ repo.openPrCount }}</div>
          </div></div>
          <div class="card"><div class="card-body">
            <div class="def-label">Merged (30d)</div>
            <div class="big">{{ repo.mergedPrCount30d }}</div>
          </div></div>
          <div class="card"><div class="card-body">
            <div class="def-label">Last activity</div>
            <div class="text-sm">{{ repo.lastActivityAt | relativeTime }}</div>
            <div class="text-xs muted">{{ repo.webhookConnected ? 'Webhook delivering' : 'No webhook deliveries seen' }}</div>
          </div></div>
        </section>

        <div class="grid grid-2">
          <ir-section-card title="Health factors" icon="gauge" [count]="repo.health.factors.length">
            @if (repo.health.factors.length) {
              <div class="stack-2">
                @for (factor of repo.health.factors; track factor.key) {
                  <div class="factor">
                    <span class="dot" [class]="'dot fg-' + factor.tone"></span>
                    <span class="weight-medium text-sm">{{ factor.label }}</span>
                    <span class="spacer"></span>
                    <span class="text-sm secondary">{{ factor.value }}</span>
                  </div>
                  @if (factor.detail) {
                    <p class="text-xs muted" style="margin: -4px 0 0 20px">{{ factor.detail }}</p>
                  }
                }
              </div>
            } @else {
              <ir-empty-state icon="gauge" title="No factors recorded" />
            }
          </ir-section-card>

          <ir-section-card title="Risk trend" icon="shield">
            <ir-trend-chart [series]="riskSeries()" ariaLabel="Risk score over time for this repository" />
          </ir-section-card>
        </div>

        <div class="grid grid-2">
          <ir-section-card title="Top contributors" icon="user" [count]="repo.contributors.length">
            @if (repo.contributors.length) {
              <div class="stack-2">
                @for (contributor of repo.contributors; track contributor.login) {
                  <div class="factor">
                    <span class="avatar">{{ initials(contributor.login) }}</span>
                    <span class="weight-medium text-sm">{{ contributor.displayName ?? contributor.login }}</span>
                    <span class="spacer"></span>
                    <span class="text-xs muted">{{ contributor.prCount }} PRs</span>
                    @if (contributor.avgRiskScore !== undefined && contributor.avgRiskScore !== null) {
                      <ir-badge [label]="'avg risk ' + contributor.avgRiskScore" tone="neutral" [humanize]="false" />
                    }
                  </div>
                }
              </div>
            } @else {
              <ir-empty-state icon="user" title="No contributors recorded" />
            }
          </ir-section-card>

          <ir-section-card title="Capability breakdown" icon="layers">
            <ir-bar-list [entries]="repo.capabilityBreakdown" [colorful]="true" emptyMessage="No capabilities classified yet." />
          </ir-section-card>
        </div>

        <ir-section-card title="Recent pull requests" icon="git-pull-request" [count]="repo.recentPullRequests.length">
          @if (repo.recentPullRequests.length) {
            <div class="stack-2">
              @for (pr of repo.recentPullRequests; track pr.prId) {
                <div class="factor">
                  <span class="mono text-xs">#{{ pr.prNumber }}</span>
                  <a [routerLink]="['/pull-requests', pr.prId]" class="weight-medium truncate">{{ pr.title }}</a>
                  <span class="spacer"></span>
                  @if (pr.riskLevel) {
                    <ir-badge [label]="pr.riskLevel" [tone]="riskTone(pr.riskLevel)" />
                  }
                  <span class="text-xs muted">{{ pr.mergedAt | relativeTime }}</span>
                </div>
              }
            </div>
          } @else {
            <ir-empty-state icon="git-pull-request" title="No pull requests captured" />
          }
        </ir-section-card>
      }
      }
    </div>
  `,
  styles: [
    `
      .big { font-size: var(--text-2xl); font-weight: var(--weight-semibold); }
      .of { font-size: var(--text-sm); color: var(--text-muted); font-weight: var(--weight-normal); }
      .factor {
        display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap;
        padding: var(--space-2) var(--space-3);
        border: 1px solid var(--border-subtle); border-radius: var(--radius-sm);
      }
      .avatar {
        width: 22px; height: 22px; display: grid; place-items: center; border-radius: 50%;
        background: var(--accent-subtle-bg); color: var(--accent-subtle-fg);
        font-size: 9px; font-weight: var(--weight-bold);
      }
    `,
  ],
})
export class RepositoryDetailPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly breadcrumbs = inject(BreadcrumbService);

  readonly name = input.required<string>();

  protected readonly state = new RequestState<RepositoryDetail>();

  protected readonly healthTone = healthTone;
  protected readonly scoreTone = scoreTone;
  protected readonly riskTone = riskTone;

  constructor() {
    effect(() => {
      const repo = this.state.data();
      if (repo) {
        this.breadcrumbs.setDetail(repo.name);
      }
    });
  }

  protected readonly riskSeries = computed<readonly TrendSeries[]>(() => {
    const points = this.state.data()?.riskTrend ?? [];
    return points.length ? [{ key: 'risk', label: 'Risk score', points }] : [];
  });

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.getRepository(this.name()));
  }

  protected initials(login: string): string {
    return login.slice(0, 2).toUpperCase();
  }
}
