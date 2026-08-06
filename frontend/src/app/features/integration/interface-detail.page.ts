import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { IntegrationInterface } from '../../core/models/integration';
import { ApiService } from '../../core/services/api.service';
import { BreadcrumbService } from '../../core/services/breadcrumb.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent, ProvenanceBadgeComponent } from '../../shared/components/badge/badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { healthTone, humanise, riskTone, severityTone } from '../../shared/tone';

/** One interface: what it moves, how, between what, and how it is behaving. */
@Component({
  selector: 'ir-interface-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BadgeComponent, ProvenanceBadgeComponent,
    IconComponent, SkeletonComponent, ErrorPanelComponent, EmptyStateComponent,
    RelativeTimePipe, RouterLink,
  ],
  template: `
    <div class="page">
      @if (state.showSkeleton()) {
        <div class="card"><ir-skeleton [rows]="8" /></div>
      } @else if (state.error()) {
        <ir-error-panel [message]="state.error()!" (retry)="reload()" />
      } @else {
      @if (state.data(); as detail) {
        <ir-page-header [title]="detail.name" [subtitle]="detail.description" icon="network">
          <div actions class="row-2">
            <ir-badge [label]="detail.direction" tone="info" />
            <ir-badge [label]="detail.style" tone="accent" />
            <ir-badge [label]="detail.health.status" [tone]="healthTone(detail.health.status)" />
            <ir-provenance [value]="detail.provenance" />
            @if (detail.flowId) {
              <a [routerLink]="['/flows', detail.flowId]" class="btn btn-sm btn-secondary">
                <ir-icon name="workflow" [size]="14" />
                View flow
              </a>
            }
            <a
              [routerLink]="['/errors']"
              [queryParams]="{ interfaceId: detail.id, interfaceName: detail.name }"
              class="btn btn-sm btn-secondary"
            >
              <ir-icon name="alert-triangle" [size]="14" />
              Diagnose an error
            </a>
          </div>
        </ir-page-header>

        <section class="card">
          <div class="card-body def-grid">
            <div>
              <div class="def-label">Business object</div>
              <div class="def-value">{{ detail.businessObject }}</div>
            </div>
            <div>
              <div class="def-label">Domain</div>
              <div class="def-value">{{ detail.domain }}</div>
            </div>
            <div>
              <div class="def-label">Topology</div>
              <div class="def-value">{{ humanise(detail.topology) }}</div>
            </div>
            <div>
              <div class="def-label">Route</div>
              <div class="def-value text-sm">
                {{ detail.sourceSystem }}
                @if (detail.middleware) { → {{ detail.middleware }} }
                → {{ detail.targetSystem }}
              </div>
            </div>
            <div>
              <div class="def-label">Authentication</div>
              <div class="def-value">{{ humanise(detail.auth) }}</div>
            </div>
            <div>
              <div class="def-label">Discovered</div>
              <div class="def-value">{{ detail.discovered ? 'Inferred from repository contents' : 'Declared in the catalogue' }}</div>
            </div>
          </div>
        </section>

        <div class="grid grid-2">
          <ir-section-card title="Protocols &amp; formats" icon="plug">
            <div class="stack-3">
              <div>
                <div class="section-title">Protocols</div>
                <div class="chip-row" style="margin-top: var(--space-2)">
                  @for (protocol of detail.protocols; track protocol) {
                    <span class="chip chip-mono">{{ protocol }}</span>
                  }
                </div>
              </div>
              <div>
                <div class="section-title">Payload formats</div>
                <div class="chip-row" style="margin-top: var(--space-2)">
                  @for (format of detail.formats; track format) {
                    <span class="chip chip-mono">{{ format }}</span>
                  }
                </div>
              </div>
            </div>
          </ir-section-card>

          <ir-section-card title="Health" icon="gauge">
            @if (detail.health.unavailableReason) {
              <ir-empty-state icon="gauge" title="No telemetry" [body]="detail.health.unavailableReason" />
            } @else {
              <div class="def-grid">
                <div>
                  <div class="def-label">Success rate</div>
                  <div class="def-value">{{ detail.health.successRate ?? '—' }}%</div>
                </div>
                <div>
                  <div class="def-label">Avg / p95</div>
                  <div class="def-value">{{ detail.health.avgResponseMs ?? '—' }} / {{ detail.health.p95ResponseMs ?? '—' }} ms</div>
                </div>
                <div>
                  <div class="def-label">Volume (24h)</div>
                  <div class="def-value">{{ detail.health.volume24h ?? '—' }}</div>
                </div>
                <div>
                  <div class="def-label">Failures / retries</div>
                  <div class="def-value">{{ detail.health.failures24h ?? '—' }} / {{ detail.health.retries24h ?? '—' }}</div>
                </div>
                <div>
                  <div class="def-label">Queue depth</div>
                  <div class="def-value">{{ detail.health.queueDepth ?? '—' }}</div>
                </div>
                <div>
                  <div class="def-label">Last success</div>
                  <div class="def-value text-sm">{{ detail.health.lastSuccessAt | relativeTime }}</div>
                </div>
              </div>
            }
          </ir-section-card>
        </div>

        <ir-section-card title="Recent changes" icon="git-pull-request" [count]="detail.recentChanges.length">
          @if (detail.recentChanges.length) {
            <div class="stack-2">
              @for (change of detail.recentChanges; track change.prId) {
                <div class="row-2 change-row row-wrap">
                  <ir-badge [label]="change.severity" [tone]="severityTone(change.severity)" />
                  <a [routerLink]="['/pull-requests', change.prId]" class="weight-medium truncate">
                    @if (change.prNumber) { #{{ change.prNumber }} }
                    {{ change.title }}
                  </a>
                  @if (change.riskLevel) {
                    <ir-badge [label]="change.riskLevel" [tone]="riskTone(change.riskLevel)" />
                  }
                  <span class="text-xs muted">{{ change.repoName }}</span>
                  <span class="spacer"></span>
                  @if (change.mergedAt) {
                    <span class="text-xs muted">{{ change.mergedAt | relativeTime }}</span>
                  }
                </div>
                <p class="text-xs secondary" style="margin: 0 0 var(--space-2) 0;">{{ change.reason }}</p>
              }
            </div>
          } @else {
            <ir-empty-state
              icon="git-pull-request"
              title="No captured changes yet"
              body="No pull request received by webhook has touched this interface — this section fills in as soon as one does."
            />
          }
        </ir-section-card>

        @if (detail.relatedArtifacts.length) {
          <ir-section-card title="Related artifacts" icon="file-code" [count]="detail.relatedArtifacts.length">
            <div class="stack-2">
              @for (artifact of detail.relatedArtifacts; track artifact.name) {
                <div class="row-2 artifact-row">
                  <ir-badge [label]="artifact.kind" tone="neutral" />
                  <span class="mono text-xs">{{ artifact.name }}</span>
                  @if (artifact.path) {
                    <span class="text-xs muted truncate">{{ artifact.path }}</span>
                  }
                  @if (artifact.system) {
                    <span class="chip">{{ artifact.system }}</span>
                  }
                </div>
              }
            </div>
          </ir-section-card>
        }
      }
      }
    </div>
  `,
  styles: [
    `
      .artifact-row {
        padding: var(--space-2) var(--space-3); flex-wrap: wrap;
        border: 1px solid var(--border-subtle); border-radius: var(--radius-sm);
      }
      .change-row {
        padding: var(--space-2) var(--space-3) 0;
      }
    `,
  ],
})
export class InterfaceDetailPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly breadcrumbs = inject(BreadcrumbService);

  readonly id = input.required<string>();

  protected readonly state = new RequestState<IntegrationInterface>();

  protected readonly healthTone = healthTone;
  protected readonly humanise = humanise;
  protected readonly riskTone = riskTone;
  protected readonly severityTone = severityTone;

  constructor() {
    effect(() => {
      const detail = this.state.data();
      if (detail) {
        this.breadcrumbs.setDetail(detail.name);
      }
    });
  }

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.getInterface(this.id()));
  }
}
