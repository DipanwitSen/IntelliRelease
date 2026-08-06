import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import {
  IntegrationInterface, IntegrationOverview, TopologyStage, TopologySummary,
} from '../../core/models/integration';
import { Page } from '../../core/models/common';
import { ApiService, InterfaceQuery } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { BarListComponent } from '../../shared/components/charts/bar-list.component';
import { CellTemplateDirective, DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { DiagramStage, FlowDiagramComponent } from '../../shared/components/flow-diagram/flow-diagram.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { healthTone, humanise } from '../../shared/tone';

/**
 * Integration Center — the landscape, then the interfaces in it.
 *
 * The overview draws one clickable stage chain **per topology present**, not
 * one canonical Commerce → CPI → S/4 diagram. A company running CPI for orders
 * and a nightly CSV drop for price files sees two accurate diagrams instead of
 * one diagram that is half wrong. If a landscape has no middleware at all, no
 * middleware stage is drawn — that is the difference between describing a
 * customer's system and describing SAP's reference architecture.
 */
@Component({
  selector: 'ir-integration-center',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, FlowDiagramComponent, BarListComponent,
    DataTableComponent, CellTemplateDirective, BadgeComponent, IconComponent,
    SkeletonComponent, ErrorPanelComponent, EmptyStateComponent, DecimalPipe, RouterLink,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Integration Center"
        subtitle="Every interface between commerce and the systems around it — inbound and outbound, synchronous and batch, whatever protocol it speaks."
        icon="network"
      >
        <div actions>
          <button type="button" class="btn btn-sm btn-secondary" (click)="reload()">
            <ir-icon name="refresh-cw" [size]="14" />
            Refresh
          </button>
        </div>
      </ir-page-header>

      <!-- ------------------------------------------------------- overview -->
      @if (overview.showSkeleton()) {
        <div class="card"><ir-skeleton [rows]="5" /></div>
      } @else if (overview.error()) {
        <ir-error-panel title="Could not load the integration landscape" [message]="overview.error()!" (retry)="reload()" />
      } @else {
      @if (overview.data(); as data) {
        <section class="grid grid-4">
          <div class="stat">
            <span class="stat-label">Interfaces</span>
            <span class="stat-value">{{ data.totalInterfaces | number }}</span>
          </div>
          <div class="stat">
            <span class="stat-label">Inbound / outbound</span>
            <span class="stat-value">{{ data.inboundCount }} / {{ data.outboundCount }}</span>
          </div>
          <div class="stat">
            <span class="stat-label">Sync / async / batch</span>
            <span class="stat-value">{{ data.syncCount }} / {{ data.asyncCount }} / {{ data.batchCount }}</span>
          </div>
          <div class="stat">
            <span class="stat-label">Health</span>
            <span class="stat-value row-2">
              <span class="dot" [class]="'dot fg-' + healthTone(data.health.status)"></span>
              {{ humanise(data.health.status) }}
            </span>
          </div>
        </section>

        @if (data.topologies.length) {
          @for (topology of data.topologies; track topology.topology) {
            <ir-section-card
              [title]="topology.label"
              [subtitle]="topology.description"
              icon="workflow"
              [count]="topology.interfaceCount"
              [collapsible]="true"
            >
              <ir-flow-diagram
                [stages]="stagesFor(topology)"
                [selectedStageId]="selectedStageId()"
                (stageSelect)="onStageSelect(topology, $event)"
              />

              @if (selectedStage(); as stage) {
                <div class="stage-detail card">
                  <div class="row-2 row-between">
                    <div class="card-title">
                      <ir-icon name="info" [size]="15" />
                      {{ stage.label }}
                    </div>
                    <button type="button" class="btn btn-ghost btn-sm btn-icon" (click)="clearStage()" aria-label="Close">
                      <ir-icon name="x" [size]="14" />
                    </button>
                  </div>
                  <p class="text-sm secondary">{{ stage.detail }}</p>
                  @if (stage.protocols.length) {
                    <div class="chip-row">
                      @for (protocol of stage.protocols; track protocol) {
                        <span class="chip chip-mono">{{ protocol }}</span>
                      }
                    </div>
                  }
                  <button type="button" class="btn btn-sm btn-secondary" (click)="filterByStage(stage)">
                    <ir-icon name="filter" [size]="13" />
                    Show the {{ stage.interfaceCount }} interfaces through this stage
                  </button>
                </div>
              }
            </ir-section-card>
          }
        } @else {
          <ir-section-card title="Landscape" icon="workflow">
            <ir-empty-state
              icon="network"
              title="No integration topology detected yet"
              body="Topologies are inferred from repository contents and from Settings. Merge a change that touches an integration artifact, or declare your landscape under Settings → Integration."
            >
              <a routerLink="/settings" class="btn btn-sm btn-secondary">Open settings</a>
            </ir-empty-state>
          </ir-section-card>
        }

        <section class="grid grid-3">
          <ir-section-card title="Protocols in use" icon="plug">
            <ir-bar-list [entries]="data.protocolBreakdown" [colorful]="true" emptyMessage="No protocols recorded." />
          </ir-section-card>
          <ir-section-card title="Payload formats" icon="braces">
            <ir-bar-list [entries]="data.formatBreakdown" [colorful]="true" emptyMessage="No formats recorded." />
          </ir-section-card>
          <ir-section-card title="Business domains" icon="layers">
            <ir-bar-list [entries]="data.domainBreakdown" emptyMessage="No domains recorded." />
          </ir-section-card>
        </section>
      }
      }

      <!-- ----------------------------------------------------- interfaces -->
      <ir-data-table
        [columns]="columns"
        [rows]="interfaceRows()"
        [loading]="interfaces.showSkeleton()"
        [serverSide]="true"
        [totalCount]="interfaces.data()?.total ?? null"
        noun="interfaces"
        caption="Integration interfaces"
        [rowClickable]="true"
        [trackBy]="trackInterface"
        [searchTerm]="query().q ?? ''"
        (searchTermChange)="onSearch($event)"
        searchPlaceholder="Search interfaces, objects, systems…"
        emptyTitle="No interfaces catalogued"
        emptyBody="Interfaces are discovered from changed integration artifacts and from the curated catalogue. Nothing has matched yet."
        emptyIcon="network"
        (rowClick)="open($event)"
      >
        <div filters class="row-2 row-wrap">
          <select class="filter-select" [value]="query().direction ?? ''" (change)="setFilter('direction', $event)" aria-label="Direction">
            <option value="">All directions</option>
            <option value="INBOUND">Inbound</option>
            <option value="OUTBOUND">Outbound</option>
            <option value="BIDIRECTIONAL">Bidirectional</option>
          </select>

          <select class="filter-select" [value]="query().style ?? ''" (change)="setFilter('style', $event)" aria-label="Exchange style">
            <option value="">All styles</option>
            <option value="SYNC">Synchronous</option>
            <option value="ASYNC">Asynchronous</option>
            <option value="BATCH">Batch</option>
            <option value="STREAMING">Streaming</option>
          </select>

          <select class="filter-select" [value]="query().topology ?? ''" (change)="setFilter('topology', $event)" aria-label="Topology">
            <option value="">All topologies</option>
            @for (topology of topologyOptions(); track topology) {
              <option [value]="topology">{{ humanise(topology) }}</option>
            }
          </select>

          <select class="filter-select" [value]="query().health ?? ''" (change)="setFilter('health', $event)" aria-label="Health">
            <option value="">Any health</option>
            <option value="HEALTHY">Healthy</option>
            <option value="DEGRADED">Degraded</option>
            <option value="UNHEALTHY">Unhealthy</option>
            <option value="UNKNOWN">Unknown</option>
          </select>

          @if (hasFilters()) {
            <button type="button" class="btn btn-sm btn-ghost" (click)="clearFilters()">
              <ir-icon name="x" [size]="13" />
              Clear
            </button>
          }
        </div>

        <ng-template irCell="name" let-row>
          <a [routerLink]="['/integration/interfaces', row.id]" class="weight-medium">{{ row.name }}</a>
          <div class="text-xs muted truncate">{{ row.description }}</div>
        </ng-template>

        <ng-template irCell="direction" let-row>
          <span class="row-2">
            <ir-icon [name]="row.direction === 'INBOUND' ? 'arrow-down' : 'arrow-right'" [size]="13" class="muted" />
            {{ humanise(row.direction) }}
          </span>
        </ng-template>

        <ng-template irCell="protocols" let-row>
          <div class="chip-row">
            @for (protocol of row.protocols.slice(0, 3); track protocol) {
              <span class="chip chip-mono">{{ protocol }}</span>
            }
            @if (row.protocols.length > 3) {
              <span class="chip chip-mono muted">+{{ row.protocols.length - 3 }}</span>
            }
          </div>
        </ng-template>

        <ng-template irCell="route" let-row>
          <span class="text-xs muted truncate">
            {{ row.sourceSystem }}
            @if (row.middleware) { → {{ row.middleware }} }
            → {{ row.targetSystem }}
          </span>
        </ng-template>

        <ng-template irCell="health" let-row>
          <ir-badge [label]="row.health.status" [tone]="healthTone(row.health.status)" />
        </ng-template>
      </ir-data-table>
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

      .stat-label {
        font-size: var(--text-xs);
        color: var(--text-muted);
        text-transform: uppercase;
        letter-spacing: 0.04em;
      }

      .stat-value {
        font-size: var(--text-xl);
        font-weight: var(--weight-semibold);
      }

      .stage-detail {
        margin-top: var(--space-4);
        padding: var(--space-4);
        gap: var(--space-3);
        background: var(--surface-sunken);
      }
    `,
  ],
})
export class IntegrationCenterPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly overview = new RequestState<IntegrationOverview>();
  protected readonly interfaces = new RequestState<Page<IntegrationInterface>>();

  protected readonly query = signal<InterfaceQuery>({ page: 0, size: 100 });
  protected readonly selectedStageId = signal<string | null>(null);
  private readonly selectedStageRef = signal<TopologyStage | null>(null);

  protected readonly humanise = humanise;
  protected readonly healthTone = healthTone;

  protected readonly columns: readonly TableColumn<IntegrationInterface>[] = [
    { id: 'name', label: 'Interface', value: (row) => row.name },
    { id: 'businessObject', label: 'Object', value: (row) => row.businessObject, hideBelow: 'sm' },
    { id: 'direction', label: 'Direction', value: (row) => row.direction },
    { id: 'style', label: 'Style', value: (row) => row.style, hideBelow: 'sm' },
    { id: 'protocols', label: 'Protocol', sortable: false, hideBelow: 'md' },
    { id: 'route', label: 'Route', sortable: false, hideBelow: 'md' },
    { id: 'health', label: 'Health', value: (row) => row.health.status },
  ];

  protected readonly trackInterface = (row: IntegrationInterface) => row.id;

  protected readonly interfaceRows = computed(() => this.interfaces.data()?.items ?? []);

  protected readonly selectedStage = computed(() => this.selectedStageRef());

  protected readonly topologyOptions = computed(() =>
    (this.overview.data()?.topologies ?? []).map((topology) => topology.topology),
  );

  protected readonly hasFilters = computed(() => {
    const query = this.query();
    return !!(query.q || query.direction || query.style || query.topology || query.health);
  });

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.overview.destroy();
    this.interfaces.destroy();
  }

  protected reload(): void {
    this.overview.load(this.api.integrationOverview());
    this.loadInterfaces();
  }

  private loadInterfaces(): void {
    this.interfaces.load(this.api.listInterfaces(this.query()));
  }

  protected stagesFor(topology: TopologySummary): readonly DiagramStage[] {
    return topology.stageChain.map((stage) => ({
      id: stage.id,
      label: stage.label,
      kind: stage.kind,
      detail: stage.detail,
      badges: stage.protocols,
      tone: stage.tone,
      issueCount: undefined,
    }));
  }

  protected onStageSelect(topology: TopologySummary, stage: DiagramStage): void {
    if (this.selectedStageId() === stage.id) {
      this.clearStage();
      return;
    }
    this.selectedStageId.set(stage.id);
    this.selectedStageRef.set(topology.stageChain.find((entry) => entry.id === stage.id) ?? null);
  }

  protected clearStage(): void {
    this.selectedStageId.set(null);
    this.selectedStageRef.set(null);
  }

  /** Clicking a stage filters the table below to what flows through it. */
  protected filterByStage(stage: TopologyStage): void {
    const protocol = stage.protocols[0];
    this.query.update((current) => ({ ...current, protocol, page: 0 }));
    this.loadInterfaces();
  }

  protected onSearch(term: string): void {
    this.query.update((current) => ({ ...current, q: term, page: 0 }));
    this.loadInterfaces();
  }

  protected setFilter(key: keyof InterfaceQuery, event: Event): void {
    const value = (event.target as HTMLSelectElement).value || undefined;
    this.query.update((current) => ({ ...current, [key]: value, page: 0 }));
    this.loadInterfaces();
  }

  protected clearFilters(): void {
    this.query.set({ page: 0, size: 100 });
    this.loadInterfaces();
  }

  protected open(row: IntegrationInterface): void {
    void this.router.navigate(['/integration/interfaces', row.id]);
  }
}
