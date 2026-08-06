import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';

import { FlowDefinition, FlowStage } from '../../core/models/integration';
import { ApiService } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { DiagramBranch, DiagramStage, FlowDiagramComponent } from '../../shared/components/flow-diagram/flow-diagram.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { humanise, severityTone } from '../../shared/tone';

/**
 * Search a business flow, get its real chain of stages.
 *
 * Selecting a stage opens what a developer actually needs at 2am: what this
 * hop is for, what goes in, what comes out, what breaks, where the logs are,
 * and which classes to open.
 */
@Component({
  selector: 'ir-flow-visualizer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, FlowDiagramComponent, BadgeComponent,
    IconComponent, SkeletonComponent, ErrorPanelComponent, EmptyStateComponent,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Flow Visualizer"
        subtitle="Pick a business flow and see every hop it actually takes — however many there are, and whatever sits between."
        icon="workflow"
      />

      <div class="toolbar card">
        <label class="search-field">
          <span class="sr-only">Search flows</span>
          <ir-icon name="search" [size]="15" />
          <input type="search" placeholder="Create Order, Invoice, Shipment, Customer Sync, Product Sync, Pricing, Returns…" [value]="search()" (input)="onSearch($event)" />
        </label>
        <span class="toolbar-spacer"></span>
        <span class="text-xs muted">{{ flows().length }} flow{{ flows().length === 1 ? '' : 's' }}</span>
      </div>

      @if (state.showSkeleton()) {
        <div class="card"><ir-skeleton [rows]="6" /></div>
      } @else if (state.error()) {
        <ir-error-panel [message]="state.error()!" (retry)="reload()" />
      } @else if (flows().length) {
        <div class="chip-row">
          @for (flow of flows(); track flow.id) {
            <button
              type="button"
              class="btn btn-sm"
              [class.btn-primary]="selected()?.id === flow.id"
              [class.btn-secondary]="selected()?.id !== flow.id"
              (click)="select(flow)"
            >
              {{ flow.name }}
            </button>
          }
        </div>

        @if (selected(); as flow) {
          <ir-section-card [title]="flow.name" [subtitle]="flow.summary" icon="workflow">
            <div actions class="row-2">
              <ir-badge [label]="flow.direction" tone="info" />
              <ir-badge [label]="flow.style" tone="accent" />
              <ir-badge [label]="flow.topology" tone="neutral" />
            </div>

            <ir-flow-diagram
              [stages]="diagramStages()"
              [branches]="diagramBranches()"
              [selectedStageId]="selectedStageId()"
              (stageSelect)="selectStage($event)"
            />
          </ir-section-card>

          @if (selectedStage(); as stage) {
            <ir-section-card [title]="stage.name" [subtitle]="humanise(stage.kind) + ' · ' + stage.owner" icon="info">
              <div actions>
                <button type="button" class="btn btn-ghost btn-sm btn-icon" (click)="selectedStageId.set(null)" aria-label="Close">
                  <ir-icon name="x" [size]="14" />
                </button>
              </div>

              <div class="stack-6">
                <div>
                  <div class="section-title">Purpose</div>
                  <p class="text-base">{{ stage.purpose }}</p>
                </div>

                <div class="def-grid">
                  <div>
                    <div class="def-label">Input</div>
                    <div class="def-value text-sm">{{ stage.input ?? '—' }}</div>
                  </div>
                  <div>
                    <div class="def-label">Output</div>
                    <div class="def-value text-sm">{{ stage.output ?? '—' }}</div>
                  </div>
                  <div>
                    <div class="def-label">Format</div>
                    <div class="def-value def-value-mono">{{ stage.format ?? '—' }}</div>
                  </div>
                  <div>
                    <div class="def-label">Protocol</div>
                    <div class="def-value def-value-mono">{{ stage.protocol ?? '—' }}</div>
                  </div>
                </div>

                @if (stage.possibleErrors.length) {
                  <div>
                    <div class="section-title">What can go wrong here</div>
                    <div class="stack-2" style="margin-top: var(--space-2)">
                      @for (error of stage.possibleErrors; track error.code) {
                        <div class="row-2 stage-error">
                          <ir-badge [label]="error.severity" [tone]="severityTone(error.severity)" />
                          <span class="mono text-xs">{{ error.code }}</span>
                          <span class="text-sm">{{ error.title }}</span>
                        </div>
                      }
                    </div>
                  </div>
                }

                @if (stage.debugTips.length) {
                  <div>
                    <div class="section-title">Debug tips</div>
                    <ul class="bullet-list">
                      @for (tip of stage.debugTips; track tip) {
                        <li>{{ tip }}</li>
                      }
                    </ul>
                  </div>
                }

                @if (stage.logLocations.length) {
                  <div>
                    <div class="section-title">Where the logs are</div>
                    <div class="chip-row">
                      @for (location of stage.logLocations; track location) {
                        <span class="chip chip-mono">{{ location }}</span>
                      }
                    </div>
                  </div>
                }

                @if (stage.relatedClasses.length) {
                  <div>
                    <div class="section-title">Related artifacts</div>
                    <div class="chip-row">
                      @for (artifact of stage.relatedClasses; track artifact.name) {
                        <span class="chip chip-mono" [title]="artifact.path ?? artifact.kind">{{ artifact.name }}</span>
                      }
                    </div>
                  </div>
                }
              </div>
            </ir-section-card>
          }
        }
      } @else {
        <div class="card">
          <ir-empty-state
            icon="workflow"
            title="No flows catalogued yet"
            body="Flows are assembled from the integration catalogue and from what the context engine discovers in your repositories."
          />
        </div>
      }
    </div>
  `,
  styles: [
    `
      .stage-error {
        padding: var(--space-2) var(--space-3);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-sm);
        flex-wrap: wrap;
      }
      .bullet-list { list-style: disc; padding-left: var(--space-5); font-size: var(--text-md); margin-top: var(--space-2); }
      .bullet-list li { margin-bottom: var(--space-1); }
    `,
  ],
})
export class FlowVisualizerPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly state = new RequestState<FlowDefinition[]>();
  protected readonly search = signal('');
  protected readonly selected = signal<FlowDefinition | null>(null);
  protected readonly selectedStageId = signal<string | null>(null);

  protected readonly humanise = humanise;
  protected readonly severityTone = severityTone;

  protected readonly flows = computed(() => this.state.data() ?? []);

  protected readonly selectedStage = computed<FlowStage | null>(() => {
    const id = this.selectedStageId();
    if (!id) {
      return null;
    }
    return this.selected()?.stages.find((stage) => stage.id === id) ?? null;
  });

  protected readonly diagramStages = computed<readonly DiagramStage[]>(() =>
    (this.selected()?.stages ?? []).map((stage) => ({
      id: stage.id,
      label: stage.name,
      kind: stage.kind,
      detail: stage.purpose,
      badges: [stage.protocol, stage.format].filter((value): value is string => !!value),
      optional: stage.optional,
      issueCount: stage.possibleErrors.length,
      tone: this.toneFor(stage),
    })),
  );

  /**
   * Failure modes outrank stage kind — a stage with a known CRITICAL/HIGH
   * error reads as danger even if it's otherwise a routine hop. Beyond that,
   * kind carries the color: MIDDLEWARE/QUEUE hops are the integration seam,
   * TARGET/ACKNOWLEDGEMENT is where the chain lands.
   */
  private toneFor(stage: FlowStage): DiagramStage['tone'] {
    if (stage.possibleErrors.some((error) => error.severity === 'HIGH' || error.severity === 'CRITICAL')) {
      return 'danger';
    }
    if (stage.possibleErrors.length) {
      return 'warning';
    }
    switch (stage.kind) {
      case 'TARGET':
      case 'ACKNOWLEDGEMENT':
        return 'success';
      case 'MIDDLEWARE':
      case 'QUEUE':
      case 'ROUTING':
      case 'TRANSPORT':
        return 'accent';
      case 'VALIDATION':
        return 'info';
      default:
        return undefined;
    }
  }

  protected readonly diagramBranches = computed<readonly DiagramBranch[]>(() =>
    (this.selected()?.branches ?? []).map((branch) => ({
      id: branch.id,
      label: branch.label,
      fromStageId: branch.fromStageId,
      toStageId: branch.toStageId,
      kind: branch.kind,
    })),
  );

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.listFlows(this.search() || undefined));
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
    this.reload();
  }

  protected select(flow: FlowDefinition): void {
    this.selected.set(flow);
    this.selectedStageId.set(null);
  }

  protected selectStage(stage: DiagramStage): void {
    this.selectedStageId.set(this.selectedStageId() === stage.id ? null : stage.id);
  }
}
