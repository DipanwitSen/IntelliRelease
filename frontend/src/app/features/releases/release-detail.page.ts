import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BuildResult, NotifyResult, ReleaseDetail, ReleaseNotes } from '../../core/models/delivery';
import { DeploymentStrategyResult } from '../../core/models/deployment-strategy';
import { ApiService } from '../../core/services/api.service';
import { BreadcrumbService } from '../../core/services/breadcrumb.service';
import { RequestState } from '../../core/services/request-state';
import { ToastService } from '../../core/services/toast.service';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { AbsoluteTimePipe } from '../../shared/pipes/relative-time.pipe';
import { confidenceTone, deploymentStrategyTone, readinessTone, releaseStatusTone, riskTone } from '../../shared/tone';

/**
 * One release, and the governed actions available on it.
 *
 * Notify is disabled until the release is approved, mirroring the backend gate
 * rather than replacing it — the backend returns 409 APPROVAL_REQUIRED
 * regardless of what this UI allows (architecture rule 8). Disabling the button
 * is a courtesy, not the control.
 */
@Component({
  selector: 'ir-release-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BadgeComponent, IconComponent,
    SkeletonComponent, ErrorPanelComponent, EmptyStateComponent, AbsoluteTimePipe, RouterLink,
  ],
  template: `
    <div class="page">
      @if (state.showSkeleton()) {
        <div class="card"><ir-skeleton [rows]="8" /></div>
      } @else if (state.error()) {
        <ir-error-panel [message]="state.error()!" (retry)="reload()" />
      } @else {
      @if (release(); as detail) {
        <ir-page-header [title]="detail.version" [subtitle]="detail.repoName + ' · ' + detail.fromRef + ' → ' + detail.toRef" icon="rocket">
          <div titleSuffix>
            <ir-badge [label]="detail.status" [tone]="releaseStatusTone(detail.status)" />
          </div>
          <div actions class="row-2">
            <button type="button" class="btn btn-sm btn-secondary" (click)="build()" [disabled]="busy()">
              <ir-icon name="git-branch" [size]="14" />
              Build contents
            </button>
            <button type="button" class="btn btn-sm btn-secondary" (click)="approve()" [disabled]="busy()">
              <ir-icon name="check-circle" [size]="14" />
              Approve
            </button>
            <button
              type="button"
              class="btn btn-sm btn-primary"
              (click)="notify()"
              [disabled]="busy() || !isApproved(detail)"
              [title]="isApproved(detail) ? 'Send the previewed notes' : 'The backend rejects notifications for an unapproved release'"
            >
              <ir-icon name="send" [size]="14" />
              Send notes
            </button>
            <button type="button" class="btn btn-sm btn-secondary" (click)="markDeployed()" [disabled]="busy() || detail.deployed">
              <ir-icon name="activity" [size]="14" />
              Confirm deployed
            </button>
          </div>
        </ir-page-header>

        <section class="grid grid-4">
          <div class="card"><div class="card-body">
            <div class="def-label">Resolved PRs</div>
            <div class="big">{{ detail.resolvedPrCount ?? '—' }}</div>
          </div></div>
          <div class="card"><div class="card-body">
            <div class="def-label">Aggregate risk</div>
            <div class="row-2">
              <ir-badge [label]="detail.aggregateRiskLevel" [tone]="riskTone(detail.aggregateRiskLevel)" />
              <span class="big">{{ detail.aggregateRiskScore ?? '—' }}</span>
            </div>
          </div></div>
          <div class="card"><div class="card-body">
            <div class="def-label">Readiness</div>
            <div class="row-2">
              <ir-badge [label]="detail.readinessStatus" [tone]="readinessTone(detail.readinessStatus)" />
              <span class="big">{{ detail.readinessScore ?? '—' }}</span>
            </div>
          </div></div>
          <div class="card"><div class="card-body">
            <div class="def-label">Deployed</div>
            <div class="text-sm">{{ detail.deployed ? (detail.deployedAt | absoluteTime) : 'Not confirmed' }}</div>
            @if (detail.deployedBy) {
              <div class="text-xs muted">by {{ detail.deployedBy }}</div>
            }
          </div></div>
        </section>

        <ir-section-card title="Deployment Strategy" icon="git-branch">
          @if (strategy.data(); as advisory) {
            <div class="strategy-head">
              <ir-badge [label]="advisory.strategy" [tone]="deploymentStrategyTone(advisory.strategy)" />
              <span class="def-label">Confidence</span>
              <ir-badge [label]="advisory.confidence" [tone]="confidenceTone(advisory.confidence)" />
            </div>

            @if (advisory.reasons.length) {
              <div class="strategy-block">
                <div class="section-title">Reason</div>
                <ul class="check-list">
                  @for (reason of advisory.reasons; track reason) {
                    <li><ir-icon name="check-circle" [size]="14" class="check-icon" />{{ reason }}</li>
                  }
                </ul>
              </div>
            }

            @if (advisory.recommendedActions.length) {
              <div class="strategy-block">
                <div class="section-title">Recommendations</div>
                <ul class="check-list">
                  @for (action of advisory.recommendedActions; track action) {
                    <li><ir-icon name="check-circle" [size]="14" class="check-icon" />{{ action }}</li>
                  }
                </ul>
              </div>
            }

            <p class="text-xs muted" style="margin-top: var(--space-3)">
              Deterministic — decided by SAP Commerce artifact-type rules, not AI.
              Rule set {{ advisory.knowledgeBaseVersion }}.
              @if (advisory.unclassifiedFileCount) {
                {{ advisory.unclassifiedFileCount }} changed file(s) could not be classified.
              }
            </p>
          } @else if (strategy.showSkeleton()) {
            <ir-skeleton [rows]="4" />
          } @else {
            <ir-empty-state
              icon="git-branch"
              title="No recommendation yet"
              body="Build this release from Git so the Deployment Strategy Engine can evaluate its resolved pull requests."
            />
          }
        </ir-section-card>

        <ir-section-card title="Included pull requests" icon="git-pull-request" [count]="detail.pullRequests?.length ?? 0">
          @if (detail.pullRequests?.length) {
            <div class="stack-2">
              @for (pr of detail.pullRequests!; track pr.prId) {
                <div class="row-2 pr-row">
                  <span class="mono text-xs">#{{ pr.prNumber }}</span>
                  <a [routerLink]="['/pull-requests', pr.prId]" class="weight-medium truncate">{{ pr.title }}</a>
                  @if (pr.ticketKey) {
                    <span class="chip chip-mono">{{ pr.ticketKey }}</span>
                  }
                  <span class="spacer"></span>
                  @if (pr.riskLevel) {
                    <ir-badge [label]="pr.riskLevel" [tone]="riskTone(pr.riskLevel)" />
                  }
                </div>
              }
            </div>
          } @else {
            <ir-empty-state
              icon="git-branch"
              title="Contents not resolved yet"
              body="Build this release to diff its ref range against the commit graph — cherry-pick aware and revert-netted."
            />
          }
        </ir-section-card>

        @if (detail.excludedPrs?.length) {
          <ir-section-card title="Excluded from this release" icon="x-circle" [count]="detail.excludedPrs!.length">
            <div class="stack-2">
              @for (excluded of detail.excludedPrs!; track excluded.sha ?? excluded.prNumber) {
                <div class="row-2 pr-row">
                  @if (excluded.prNumber) {
                    <span class="mono text-xs">#{{ excluded.prNumber }}</span>
                  }
                  <span class="text-sm">{{ excluded.reason }}</span>
                  @if (excluded.detail) {
                    <span class="text-xs muted truncate">{{ excluded.detail }}</span>
                  }
                </div>
              }
            </div>
          </ir-section-card>
        }

        <ir-section-card title="Release notes" icon="file-text">
          <div actions>
            <button type="button" class="btn btn-sm btn-secondary" (click)="loadNotes()">
              <ir-icon name="refresh-cw" [size]="13" />
              Preview
            </button>
          </div>
          @if (notes.showSkeleton()) {
            <ir-skeleton [rows]="5" />
          } @else {
      @if (notes.data(); as content) {
            @if (content.fallback) {
              <div class="callout tone-warning" style="margin-bottom: var(--space-3)">
                <ir-icon name="alert-triangle" [size]="16" class="callout-icon" />
                <div>The AI service was unavailable — these notes came from the deterministic narrator.</div>
              </div>
            }
            <ul class="notes-list">
              @for (bullet of content.bullets; track bullet.text) {
                <li>
                  @if (bullet.prNumber) {
                    <span class="mono text-xs muted">#{{ bullet.prNumber }}</span>
                  }
                  {{ bullet.text }}
                </li>
              }
            </ul>
          } @else {
            <ir-empty-state icon="file-text" title="No preview yet" body="Preview the notes before sending. What you see here is exactly what goes out." />
          }
          }
        </ir-section-card>

        @if (buildResult(); as result) {
          <ir-section-card title="Build result" icon="git-branch" [collapsible]="true">
            <div class="def-grid">
              <div>
                <div class="def-label">Commits examined</div>
                <div class="def-value">{{ result.commitsExamined }}</div>
              </div>
              <div>
                <div class="def-label">Git available</div>
                <div class="def-value">{{ result.gitAvailable ? 'Yes' : 'No' }}</div>
              </div>
            </div>
            @if (result.unmatchedPrNumbers?.length) {
              <div style="margin-top: var(--space-3)">
                <div class="section-title">Resolved but never captured</div>
                <p class="text-sm secondary">
                  These pull request numbers appear in the commit graph but were never received by webhook.
                  They are reported rather than fabricated.
                </p>
                <div class="chip-row" style="margin-top: var(--space-2)">
                  @for (number of result.unmatchedPrNumbers!; track number) {
                    <span class="chip chip-mono">#{{ number }}</span>
                  }
                </div>
              </div>
            }
          </ir-section-card>
        }

        @if (notifyResult(); as result) {
          <ir-section-card title="Notification result" icon="send" [collapsible]="true">
            <div class="stack-2">
              @for (email of result.emails; track email.audience) {
                <div class="row-2 pr-row">
                  <span class="weight-medium">{{ email.audience }}</span>
                  <span class="text-xs muted truncate">{{ email.recipient }}</span>
                  <span class="spacer"></span>
                  <ir-badge [label]="email.sent ? 'MailHog: captured' : 'MailHog: failed'" [tone]="email.sent ? 'success' : 'danger'" />
                  @if (email.relayConfigured) {
                    <ir-badge [label]="email.relayed ? 'Outlook: relayed' : 'Outlook: failed'" [tone]="email.relayed ? 'success' : 'danger'" />
                  }
                </div>
              }
            </div>
            <div class="def-grid" style="margin-top: var(--space-3)">
              <div>
                <div class="def-label">Teams</div>
                <div class="def-value">
                  {{ result.teamsConfigured ? (result.teamsSent ? 'Posted' : 'Failed') : 'Not configured — skipped' }}
                </div>
              </div>
              <div>
                <div class="def-label">Content source</div>
                <div class="def-value">{{ result.fallback ? 'Deterministic fallback' : result.provider }}</div>
              </div>
            </div>
          </ir-section-card>
        }
      }
      }
    </div>
  `,
  styles: [
    `
      .big { font-size: var(--text-2xl); font-weight: var(--weight-semibold); }
      .pr-row {
        padding: var(--space-2) var(--space-3); flex-wrap: wrap;
        border: 1px solid var(--border-subtle); border-radius: var(--radius-sm);
      }
      .notes-list { list-style: disc; padding-left: var(--space-5); font-size: var(--text-md); }
      .notes-list li { margin-bottom: var(--space-2); line-height: var(--leading-relaxed); }

      .strategy-head { display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-3); }
      .strategy-block { margin-top: var(--space-3); }
      .check-list { display: flex; flex-direction: column; gap: var(--space-1); font-size: var(--text-md); }
      .check-list li { display: flex; align-items: flex-start; gap: var(--space-2); }
      .check-icon { color: var(--tone-success, var(--accent)); margin-top: 2px; flex-shrink: 0; }
    `,
  ],
})
export class ReleaseDetailPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly breadcrumbs = inject(BreadcrumbService);

  readonly id = input.required<string>();

  protected readonly state = new RequestState<ReleaseDetail>();
  protected readonly notes = new RequestState<ReleaseNotes>();
  protected readonly strategy = new RequestState<DeploymentStrategyResult>();
  protected readonly buildResult = signal<BuildResult | null>(null);
  protected readonly notifyResult = signal<NotifyResult | null>(null);
  protected readonly busy = signal(false);

  protected readonly release = this.state.data;

  protected readonly riskTone = riskTone;
  protected readonly readinessTone = readinessTone;
  protected readonly releaseStatusTone = releaseStatusTone;
  protected readonly deploymentStrategyTone = deploymentStrategyTone;
  protected readonly confidenceTone = confidenceTone;

  constructor() {
    effect(() => {
      const detail = this.release();
      if (detail) {
        this.breadcrumbs.setDetail(detail.version);
      }
    });
  }

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
    this.notes.destroy();
    this.strategy.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.getRelease(this.id()));
    this.strategy.load(this.api.getReleaseDeploymentStrategy(this.id()));
  }

  protected loadNotes(): void {
    this.notes.load(this.api.getReleaseNotes(this.id()));
  }

  /**
   * Mirrors the backend gate. RELEASED already implies approval, and DEPLOYED
   * is not a status — deployment is the separate `deployed` flag, because
   * "communications went out" and "the code is live" are different facts.
   */
  protected isApproved(detail: ReleaseDetail): boolean {
    return detail.status === 'APPROVED' || detail.status === 'RELEASED';
  }

  protected build(): void {
    this.busy.set(true);
    this.api.buildRelease(this.id()).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.buildResult.set(result);
        this.state.set(result.release);
        this.strategy.load(this.api.getReleaseDeploymentStrategy(this.id()));
        this.toast.success('Contents resolved', `${result.commitsExamined} commits examined.`);
      },
      error: (error: { error?: { message?: string } }) => {
        this.busy.set(false);
        this.toast.error('Build failed', error?.error?.message);
      },
    });
  }

  protected approve(): void {
    this.busy.set(true);
    this.api.approveRelease(this.id()).subscribe({
      next: (release) => {
        this.busy.set(false);
        this.state.set(release);
        this.toast.success('Release approved', 'Notifications are now permitted for this release.');
      },
      error: (error: { error?: { message?: string } }) => {
        this.busy.set(false);
        this.toast.error('Approval failed', error?.error?.message);
      },
    });
  }

  protected notify(): void {
    this.busy.set(true);
    this.api.sendReleaseNotifications(this.id()).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.notifyResult.set(result);
        const sent = result.emails.filter((email) => email.sent).length;
        this.toast.success('Notes sent', `${sent} email(s)${result.teamsSent ? ' and a Teams post' : ''}.`);
        this.reload();
      },
      error: (error: { error?: { message?: string; code?: string } }) => {
        this.busy.set(false);
        this.toast.error('Nothing was sent', error?.error?.message);
      },
    });
  }

  protected markDeployed(): void {
    this.busy.set(true);
    this.api.markReleaseDeployed(this.id()).subscribe({
      next: (release) => {
        this.busy.set(false);
        this.state.set(release);
        this.toast.success('Deployment confirmed');
      },
      error: (error: { error?: { message?: string } }) => {
        this.busy.set(false);
        this.toast.error('Could not confirm deployment', error?.error?.message);
      },
    });
  }
}
