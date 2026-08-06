import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { DeploymentStrategyResult, DeploymentStrategyType } from '../../core/models/deployment-strategy';
import { PullRequestDetail } from '../../core/models/delivery';
import { ContextPackage } from '../../core/models/intelligence';
import { ApiService } from '../../core/services/api.service';
import { BreadcrumbService } from '../../core/services/breadcrumb.service';
import { RequestState } from '../../core/services/request-state';
import { ToastService } from '../../core/services/toast.service';
import { BadgeComponent, ProvenanceBadgeComponent } from '../../shared/components/badge/badge.component';
import { CodeViewerComponent } from '../../shared/components/code-viewer/code-viewer.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { AbsoluteTimePipe, FileSizePipe } from '../../shared/pipes/relative-time.pipe';
import { confidenceTone, deploymentStrategyTone, humanise, readinessTone, riskScoreTone, riskTone, severityTone } from '../../shared/tone';

/**
 * One pull request, in full.
 *
 * The Context Package section is the accountability feature: it shows exactly
 * what the deterministic pipeline handed to the model, so a reviewer can
 * confirm no raw GitHub payload and no source file was ever sent.
 */
@Component({
  selector: 'ir-pull-request-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BadgeComponent, ProvenanceBadgeComponent,
    CodeViewerComponent, IconComponent, SkeletonComponent, ErrorPanelComponent,
    EmptyStateComponent, AbsoluteTimePipe, FileSizePipe, RouterLink,
  ],
  template: `
    <div class="page">
      @if (state.showSkeleton()) {
        <div class="card"><ir-skeleton [rows]="8" /></div>
      } @else if (state.error()) {
        <ir-error-panel [message]="state.error()!" (retry)="reload()" />
      } @else {
      @if (pr(); as detail) {
        <ir-page-header [title]="detail.title" [subtitle]="detail.description ?? null" icon="git-pull-request">
          <div titleSuffix class="row-2">
            <span class="chip chip-mono">#{{ detail.prNumber }}</span>
            @if (detail.ticketKey) {
              <span class="chip chip-mono">{{ detail.ticketKey }}</span>
            }
          </div>
          <div actions>
            <button type="button" class="btn btn-sm btn-secondary" (click)="reanalyze()">
              <ir-icon name="refresh-cw" [size]="14" />
              Re-analyse
            </button>
          </div>
        </ir-page-header>

        <section class="card">
          <div class="card-body def-grid">
            <div>
              <div class="def-label">Repository</div>
              <div class="def-value">{{ detail.repoName }}</div>
            </div>
            <div>
              <div class="def-label">Author</div>
              <div class="def-value">{{ detail.author }}</div>
            </div>
            <div>
              <div class="def-label">Branch</div>
              <div class="def-value def-value-mono">{{ detail.branch ?? '—' }}</div>
            </div>
            <div>
              <div class="def-label">Merge SHA</div>
              <div class="def-value def-value-mono">{{ (detail.mergeSha ?? '—').slice(0, 12) }}</div>
            </div>
            <div>
              <div class="def-label">Merged</div>
              <div class="def-value text-sm">{{ detail.mergedAt | absoluteTime }}</div>
            </div>
            <div>
              <div class="def-label">Provenance</div>
              <div class="def-value"><ir-provenance [value]="detail.provenanceClass" /></div>
            </div>
          </div>
        </section>

        @if (!detail.analyzed) {
          <div class="callout tone-info">
            <ir-icon name="clock" [size]="16" class="callout-icon" />
            <div>
              <div class="callout-title">Awaiting analysis</div>
              This pull request has been captured but not yet analysed. Analysis runs asynchronously
              from the PostgreSQL job queue; risk, impact and readiness appear when it completes.
            </div>
          </div>
        } @else {
          <section class="grid grid-3">
            <ir-section-card title="Risk" icon="shield">
              <div class="row-2">
                <ir-badge [label]="detail.riskLevel" [tone]="riskTone(detail.riskLevel)" [outlined]="detail.riskLevel === 'HIGH'" />
                <span class="big-score">{{ detail.riskScore }}</span>
              </div>
              <div class="meter" style="margin-top: var(--space-3)">
                <div class="meter-fill" [class]="'meter-fill tone-' + riskScoreTone(detail.riskScore)" [style.width.%]="detail.riskScore ?? 0"></div>
              </div>
              @if (detail.riskPolicyVersion) {
                <div class="text-xs muted" style="margin-top: var(--space-2)">Policy {{ detail.riskPolicyVersion }}</div>
              }
              @if (detail.riskReasons?.length) {
                <ul class="reason-list">
                  @for (reason of detail.riskReasons!; track reason.description) {
                    <li>
                      <span class="reason-points">+{{ reason.points ?? 0 }}</span>
                      {{ reason.description }}
                    </li>
                  }
                </ul>
              }
            </ir-section-card>

            <ir-section-card title="Deployment readiness" icon="rocket">
              <div class="row-2">
                <ir-badge [label]="detail.deploymentReadinessStatus" [tone]="readinessTone(detail.deploymentReadinessStatus)" />
                <span class="big-score">{{ detail.deploymentReadinessScore }}</span>
              </div>
            </ir-section-card>

            <ir-section-card title="SAP Commerce context" icon="layers">
              @if (detail.sapCommerceContext; as context) {
                <div class="def-grid">
                  <div>
                    <div class="def-label">Files</div>
                    <div class="def-value">{{ context.totalFiles }}</div>
                  </div>
                  <div>
                    <div class="def-label">Unclassified</div>
                    <div class="def-value">{{ context.unclassifiedFiles }}</div>
                  </div>
                  <div>
                    <div class="def-label">Tests included</div>
                    <div class="def-value">{{ context.testsIncluded ? 'Yes' : 'No' }}</div>
                  </div>
                </div>
                <div class="chip-row" style="margin-top: var(--space-3)">
                  @for (capability of context.capabilities; track capability) {
                    <span class="chip">{{ humanise(capability) }}</span>
                  }
                </div>
              }
            </ir-section-card>
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

              @if (detail.confirmedDeploymentStrategy) {
                <div class="callout tone-success" style="margin-top: var(--space-4)">
                  <ir-icon name="check-circle" [size]="16" class="callout-icon" />
                  <div>
                    Confirmed: {{ detail.confirmedDeploymentStrategy }} deployment by {{ detail.confirmedBy }}
                    &middot; {{ detail.confirmedAt | absoluteTime }}
                  </div>
                </div>
              } @else {
                <div class="strategy-block row-2">
                  <button type="button" class="btn btn-sm btn-primary" [disabled]="confirming()"
                          (click)="confirmStrategy('ROLLING')">
                    Confirm Rolling Deployment
                    @if (advisory.strategy === 'ROLLING') { <span class="chip">Recommended</span> }
                  </button>
                  <button type="button" class="btn btn-sm btn-secondary" [disabled]="confirming()"
                          (click)="confirmStrategy('MIGRATE')">
                    Confirm Migrate Deployment
                    @if (advisory.strategy === 'MIGRATE') { <span class="chip">Recommended</span> }
                  </button>
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
              <ir-empty-state icon="git-branch" title="No recommendation yet" body="Deployment strategy is computed as part of analysis." />
            }
          </ir-section-card>

          @if (detail.integrationContext; as integration) {
            <ir-section-card title="Integration impact" icon="network">
              <div actions><ir-provenance [value]="integration.provenance" /></div>
              @if (!integration.touched) {
                <ir-empty-state icon="check-circle" title="No integration surface touched" body="Nothing in this change reaches an interface, payload or mapping." />
              } @else {
                <div class="stack-6">
                  @if (integration.impactedInterfaces.length) {
                    <div>
                      <div class="section-title">Impacted interfaces</div>
                      <div class="stack-2" style="margin-top: var(--space-2)">
                        @for (item of integration.impactedInterfaces; track item.interfaceId) {
                          <div class="row-2 impact-row row-wrap">
                            <ir-badge [label]="item.severity" [tone]="severityTone(item.severity)" />
                            <a [routerLink]="['/integration/interfaces', item.interfaceId]" class="weight-medium">{{ item.name }}</a>
                            <span class="chip">{{ humanise(item.direction) }}</span>
                            <span class="text-sm secondary truncate">{{ item.reason }}</span>
                            <span class="spacer"></span>
                            <a
                              [routerLink]="['/errors']"
                              [queryParams]="{ interfaceId: item.interfaceId, interfaceName: item.name }"
                              class="btn btn-ghost btn-sm"
                            >
                              <ir-icon name="alert-triangle" [size]="12" />
                              Diagnose an error
                            </a>
                          </div>
                        }
                      </div>
                    </div>
                  }
                  @if (integration.impactedMappings.length) {
                    <div>
                      <div class="section-title">Mapping sets touched</div>
                      <div class="stack-2" style="margin-top: var(--space-2)">
                        @for (mapping of integration.impactedMappings; track mapping.id) {
                          <div class="row-2 impact-row">
                            <a [routerLink]="['/mappings']" [queryParams]="{ set: mapping.id }" class="weight-medium">{{ mapping.name }}</a>
                            @if (mapping.issueCount > 0) {
                              <ir-badge [label]="mapping.issueCount + ' known issue' + (mapping.issueCount === 1 ? '' : 's')" tone="warning" [humanize]="false" />
                            } @else {
                              <span class="text-xs secondary">no known issues</span>
                            }
                          </div>
                        }
                      </div>
                    </div>
                  }
                  <div class="grid grid-3">
                    @for (group of impactGroups(); track group.label) {
                      @if (group.values.length) {
                        <div>
                          <div class="section-title">{{ group.label }}</div>
                          <div class="chip-row" style="margin-top: var(--space-2)">
                            @for (value of group.values; track value) {
                              <span class="chip chip-mono">{{ value }}</span>
                            }
                          </div>
                        </div>
                      }
                    }
                  </div>
                </div>
              }
            </ir-section-card>
          }

          @if (detail.confirmedDeploymentStrategy && detail.aiSummary; as ai) {
            <ir-section-card title="Release notes" icon="file-text">
              <div actions class="row-2">
                @if (ai.fallback) {
                  <ir-badge label="Deterministic fallback" tone="warning" [humanize]="false" />
                }
                <ir-provenance [value]="ai.provenanceClass" />
              </div>
              <div class="stack-6">
                @for (section of aiSections(); track section.label) {
                  @if (section.body) {
                    <div>
                      <div class="section-title">{{ section.label }}</div>
                      <p class="text-base">{{ section.body }}</p>
                    </div>
                  }
                }
              </div>
            </ir-section-card>
          }

          <ir-section-card title="Context package sent to the model" icon="file-code" [collapsible]="true" [expanded]="false">
            @if (contextPackage.showSkeleton()) {
              <ir-skeleton [rows]="5" />
            } @else {
      @if (contextPackage.data(); as pkg) {
              <div class="def-grid" style="margin-bottom: var(--space-4)">
                <div>
                  <div class="def-label">Files examined / included</div>
                  <div class="def-value">{{ pkg.stats.filesExamined }} / {{ pkg.stats.filesIncluded }}</div>
                </div>
                <div>
                  <div class="def-label">Raw → packaged</div>
                  <div class="def-value">{{ pkg.stats.rawBytes | fileSize }} → {{ pkg.stats.packagedBytes | fileSize }}</div>
                </div>
                <div>
                  <div class="def-label">Estimated tokens</div>
                  <div class="def-value">{{ pkg.stats.estimatedTokens }}</div>
                </div>
                <div>
                  <div class="def-label">Engine</div>
                  <div class="def-value def-value-mono">{{ pkg.engineVersion }}</div>
                </div>
              </div>
              @for (section of pkg.sections; track section.key) {
                <div style="margin-bottom: var(--space-4)">
                  <div class="row-2" style="margin-bottom: var(--space-2)">
                    <span class="weight-semibold text-sm">{{ section.label }}</span>
                    <ir-provenance [value]="section.provenance" [showIcon]="false" />
                    <span class="text-xs muted">{{ section.itemCount }} item(s)</span>
                  </div>
                  <ir-code-viewer [content]="section.content" language="json" [showLineNumbers]="false" [maxLines]="200" />
                </div>
              }
            } @else {
              <ir-empty-state icon="file-code" title="No context package recorded" body="This pull request was analysed before context packages were captured, or analysis has not run." />
            }
            }
          </ir-section-card>
        }
      }
      }
    </div>
  `,
  styles: [
    `
      .big-score { margin-left: auto; font-size: var(--text-2xl); font-weight: var(--weight-semibold); }
      .reason-list { display: flex; flex-direction: column; gap: var(--space-2); margin-top: var(--space-3); font-size: var(--text-sm); }
      .reason-list li { display: flex; gap: var(--space-2); }
      .reason-points { font-variant-numeric: tabular-nums; color: var(--text-muted); flex: 0 0 auto; }
      .impact-row {
        padding: var(--space-2) var(--space-3); flex-wrap: wrap;
        border: 1px solid var(--border-subtle); border-radius: var(--radius-sm);
      }

      .strategy-head { display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-3); }
      .strategy-block { margin-top: var(--space-3); }
      .check-list { display: flex; flex-direction: column; gap: var(--space-1); font-size: var(--text-md); }
      .check-list li { display: flex; align-items: flex-start; gap: var(--space-2); }
      .check-icon { color: var(--tone-success, var(--accent)); margin-top: 2px; flex-shrink: 0; }
    `,
  ],
})
export class PullRequestDetailPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly breadcrumbs = inject(BreadcrumbService);
  private readonly toast = inject(ToastService);

  /** Bound from the `:id` route param by withComponentInputBinding(). */
  readonly id = input.required<string>();

  protected readonly state = new RequestState<PullRequestDetail>();
  protected readonly contextPackage = new RequestState<ContextPackage>();
  protected readonly strategy = new RequestState<DeploymentStrategyResult>();
  protected readonly confirming = signal(false);

  protected readonly pr = this.state.data;

  protected readonly riskTone = riskTone;
  protected readonly riskScoreTone = riskScoreTone;
  protected readonly readinessTone = readinessTone;
  protected readonly severityTone = severityTone;
  protected readonly deploymentStrategyTone = deploymentStrategyTone;
  protected readonly confidenceTone = confidenceTone;
  protected readonly humanise = humanise;

  constructor() {
    effect(() => {
      const detail = this.pr();
      if (detail) {
        this.breadcrumbs.setDetail(`#${detail.prNumber} ${detail.title}`);
      }
    });
  }

  protected readonly impactGroups = computed(() => {
    const integration = this.pr()?.integrationContext;
    return [
      { label: 'Payloads', values: integration?.impactedPayloads ?? [] },
      { label: 'DTOs', values: integration?.impactedDtos ?? [] },
      { label: 'Commerce models', values: integration?.impactedCommerceModels ?? [] },
      { label: 'Target objects', values: integration?.impactedTargetObjects ?? [] },
      { label: 'Middleware flows', values: integration?.impactedMiddlewareFlows ?? [] },
    ];
  });

  /** Release notes: the same AI narration the platform already generates, one section per audience. */
  protected readonly aiSections = computed(() => {
    const ai = this.pr()?.aiSummary;
    return [
      { label: 'Developer notes', body: ai?.technicalSummary },
      { label: 'QA notes', body: ai?.qaSummary },
      { label: 'Business notes', body: ai?.businessSummary },
      { label: 'Client notes', body: ai?.clientSummary },
      { label: 'Deployment strategy', body: ai?.deploymentStrategyExplanation },
    ];
  });

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
    this.contextPackage.destroy();
    this.strategy.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.getPullRequest(this.id()));
    this.contextPackage.load(this.api.getPullRequestContextPackage(this.id()));
    this.strategy.load(this.api.getPullRequestDeploymentStrategy(this.id()));
  }

  protected reanalyze(): void {
    this.state.load(this.api.reanalyzePullRequest(this.id()));
  }

  protected confirmStrategy(choice: DeploymentStrategyType): void {
    if (this.confirming()) {
      return;
    }
    this.confirming.set(true);
    this.api.confirmDeploymentStrategy(this.id(), choice).subscribe({
      next: (detail) => {
        this.confirming.set(false);
        this.state.set(detail);
        this.strategy.load(this.api.getPullRequestDeploymentStrategy(this.id()));
        this.toast.success(`Confirmed ${choice} deployment`, 'Release notes generated.');
      },
      error: (error: { error?: { message?: string } }) => {
        this.confirming.set(false);
        this.toast.error('Could not confirm deployment strategy', error?.error?.message);
      },
    });
  }
}
