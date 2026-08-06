import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CountEntry, Page } from '../../core/models/common';
import { PullRequestSummary } from '../../core/models/delivery';
import { ApiService } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent, ProvenanceBadgeComponent } from '../../shared/components/badge/badge.component';
import { BarListComponent } from '../../shared/components/charts/bar-list.component';
import { CellTemplateDirective, DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { readinessTone, riskScoreTone, riskTone } from '../../shared/tone';

/**
 * Risk, ranked — and the standing reminder of where the number comes from.
 *
 * Every score on this page is RULE_OUTPUT: a versioned policy produced it from
 * facts, and the same inputs will produce it again. No model was consulted.
 */
@Component({
  selector: 'ir-risk-analysis',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BarListComponent, DataTableComponent,
    CellTemplateDirective, BadgeComponent, ProvenanceBadgeComponent, IconComponent,
    RelativeTimePipe, RouterLink,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Risk Analysis"
        subtitle="Deterministic risk across every analysed change, highest first."
        icon="shield"
      >
        <div actions>
          <ir-provenance value="RULE_OUTPUT" />
        </div>
      </ir-page-header>

      <div class="callout tone-info">
        <ir-icon name="info" [size]="16" class="callout-icon" />
        <div>
          <div class="callout-title">These scores are computed, not predicted</div>
          A versioned rule policy produces every number here from captured facts. Re-running the same
          policy over the same change yields the same score. The AI service explains these results in
          natural language — it never produces or adjusts them.
        </div>
      </div>

      <section class="grid grid-4">
        @for (bucket of buckets(); track bucket.key) {
          <button
            type="button"
            class="card bucket"
            [class.selected]="levelFilter() === bucket.key"
            (click)="toggleLevel(bucket.key)"
          >
            <div class="card-body">
              <div class="row-2">
                <ir-badge [label]="bucket.key" [tone]="riskTone(bucket.key)" [outlined]="bucket.key === 'CRITICAL'" />
                <span class="spacer"></span>
                <span class="big">{{ bucket.count }}</span>
              </div>
            </div>
          </button>
        }
      </section>

      <div class="grid grid-2">
        <ir-section-card title="Highest-risk changes" icon="trending-up" [flush]="true">
          <ir-data-table
            [columns]="columns"
            [rows]="filtered()"
            [loading]="state.showSkeleton()"
            noun="analysed changes"
            caption="Highest-risk pull requests"
            [showToolbar]="false"
            [trackBy]="trackPr"
            emptyTitle="Nothing analysed yet"
            emptyBody="Risk appears once the analysis job has run over a captured pull request."
            emptyIcon="shield"
          >
            <ng-template irCell="title" let-row>
              <a [routerLink]="['/pull-requests', row.prId]" class="weight-medium truncate">{{ row.title }}</a>
              <div class="text-xs muted">#{{ row.prNumber }} · {{ row.repoName }}</div>
            </ng-template>
            <ng-template irCell="risk" let-row>
              <div class="risk-cell">
                <div class="row-2">
                  <ir-badge [label]="row.riskLevel" [tone]="riskTone(row.riskLevel)" [outlined]="row.riskLevel === 'CRITICAL'" />
                  <span class="text-xs muted">{{ row.riskScore }}</span>
                </div>
                <div class="meter">
                  <div class="meter-fill" [class]="'meter-fill tone-' + riskScoreTone(row.riskScore)" [style.width.%]="row.riskScore ?? 0"></div>
                </div>
              </div>
            </ng-template>
            <ng-template irCell="readiness" let-row>
              @if (row.deploymentReadinessStatus) {
                <ir-badge [label]="row.deploymentReadinessStatus" [tone]="readinessTone(row.deploymentReadinessStatus)" />
              } @else {
                <span class="muted">—</span>
              }
            </ng-template>
            <ng-template irCell="merged" let-row>{{ row.mergedAt | relativeTime }}</ng-template>
          </ir-data-table>
        </ir-section-card>

        <div class="stack">
          <ir-section-card title="Risk by repository" icon="repo">
            <ir-bar-list [entries]="byRepository()" emptyMessage="No analysed changes yet." />
          </ir-section-card>

          <ir-section-card title="Risk by author" icon="user">
            <ir-bar-list [entries]="byAuthor()" emptyMessage="No analysed changes yet." />
          </ir-section-card>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .big { font-size: var(--text-2xl); font-weight: var(--weight-semibold); }
      .bucket { text-align: left; transition: border-color var(--duration-fast) var(--ease-out); }
      .bucket:hover { border-color: var(--border-strong); }
      .bucket.selected { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-subtle-bg); }
      .risk-cell { display: flex; flex-direction: column; gap: var(--space-1); min-width: 110px; }
    `,
  ],
})
export class RiskAnalysisPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly state = new RequestState<Page<PullRequestSummary>>();
  protected readonly levelFilter = signal<string | null>(null);

  protected readonly riskTone = riskTone;
  protected readonly riskScoreTone = riskScoreTone;
  protected readonly readinessTone = readinessTone;

  protected readonly columns: readonly TableColumn<PullRequestSummary>[] = [
    { id: 'title', label: 'Change', value: (row) => row.title },
    { id: 'risk', label: 'Risk', value: (row) => row.riskScore },
    { id: 'readiness', label: 'Readiness', value: (row) => row.deploymentReadinessScore, hideBelow: 'md' },
    { id: 'merged', label: 'Merged', value: (row) => row.mergedAt ?? '', hideBelow: 'sm' },
  ];

  protected readonly trackPr = (row: PullRequestSummary) => row.prId;

  private readonly analysed = computed(() =>
    (this.state.data()?.items ?? []).filter((row) => row.analyzed && row.riskScore !== undefined),
  );

  protected readonly buckets = computed<readonly { key: string; count: number }[]>(() => {
    const rows = this.analysed();
    return ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((key) => ({
      key,
      count: rows.filter((row) => row.riskLevel === key).length,
    }));
  });

  protected readonly filtered = computed(() => {
    const level = this.levelFilter();
    const rows = level ? this.analysed().filter((row) => row.riskLevel === level) : this.analysed();
    return [...rows].sort((left, right) => (right.riskScore ?? 0) - (left.riskScore ?? 0));
  });

  protected readonly byRepository = computed<readonly CountEntry[]>(() => this.groupBy((row) => row.repoName));
  protected readonly byAuthor = computed<readonly CountEntry[]>(() => this.groupBy((row) => row.author));

  ngOnInit(): void {
    this.state.load(this.api.listPullRequests({ page: 0, size: 500, analyzed: true }));
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected toggleLevel(level: string): void {
    this.levelFilter.set(this.levelFilter() === level ? null : level);
  }

  /**
   * Groups by mean risk rather than by count — "which repository carries the
   * most risk" is a different and more useful question than "which merges the
   * most", and a busy low-risk repo should not top this list.
   */
  private groupBy(key: (row: PullRequestSummary) => string): readonly CountEntry[] {
    const totals = new Map<string, { sum: number; count: number }>();
    for (const row of this.analysed()) {
      const bucket = totals.get(key(row)) ?? { sum: 0, count: 0 };
      bucket.sum += row.riskScore ?? 0;
      bucket.count += 1;
      totals.set(key(row), bucket);
    }
    return [...totals.entries()]
      .map(([label, bucket]) => ({
        key: label,
        label: `${label} (${bucket.count})`,
        count: Math.round(bucket.sum / bucket.count),
      }))
      .sort((left, right) => right.count - left.count);
  }
}
