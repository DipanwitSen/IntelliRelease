import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';

import { Page } from '../../core/models/common';
import { MappingLink, MappingSet } from '../../core/models/integration';
import { ApiService, MappingLinkQuery, MappingQuery } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { humanise, mappingStatusTone } from '../../shared/tone';

/**
 * Where a field goes, and where it stops going.
 *
 * The chain is rendered from the link's own node list rather than from a fixed
 * set of columns, so a five-hop CPI mapping and a two-hop CSV column mapping
 * both render truthfully. Missing, renamed and deleted mappings are what the
 * page is really for — they are the defects that reach production silently.
 */
@Component({
  selector: 'ir-mapping-explorer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BadgeComponent, IconComponent,
    SkeletonComponent, ErrorPanelComponent, EmptyStateComponent,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Mapping Explorer"
        subtitle="Field journeys end to end, with missing, renamed and deleted mappings called out rather than left to be discovered in production."
        icon="arrow-left-right"
      />

      <div class="grid grid-2">
        <ir-section-card title="Mapping sets" icon="list" [count]="sets().length" [flush]="true">
          <div class="toolbar">
            <label class="search-field">
              <span class="sr-only">Search mapping sets</span>
              <ir-icon name="search" [size]="15" />
              <input type="search" placeholder="Search mapping sets…" [value]="setQuery().q ?? ''" (input)="onSetSearch($event)" />
            </label>
          </div>

          @if (setsState.showSkeleton()) {
            <ir-skeleton variant="list" [rows]="5" />
          } @else if (sets().length) {
            <ul>
              @for (set of sets(); track set.id) {
                <li>
                  <button type="button" class="set-row" [class.selected]="set.id === selectedSetId()" (click)="openSet(set)">
                    <span class="row-2 row-between">
                      <span class="weight-medium truncate">{{ set.name }}</span>
                      @if (set.issueCount > 0) {
                        <span class="badge tone-warning">{{ set.issueCount }} issue{{ set.issueCount === 1 ? '' : 's' }}</span>
                      }
                    </span>
                    <span class="text-xs muted">
                      {{ set.businessObject }} · {{ humanise(set.direction) }} · {{ set.linkCount }} field{{ set.linkCount === 1 ? '' : 's' }}
                    </span>
                    <span class="chip-row">
                      @for (layer of set.layers; track layer) {
                        <span class="chip">{{ layer }}</span>
                      }
                    </span>
                  </button>
                </li>
              }
            </ul>
          } @else {
            <ir-empty-state icon="arrow-left-right" title="No mapping sets" body="Mapping sets are catalogued per interface. None have been captured yet." />
          }
        </ir-section-card>

        <ir-section-card [title]="selectedSet()?.name ?? 'Select a mapping set'" icon="workflow" [flush]="true">
          <div actions class="row-2">
            <label class="row-2 text-xs secondary">
              <input type="checkbox" [checked]="linkQuery().issuesOnly === true" (change)="toggleIssuesOnly($event)" />
              Issues only
            </label>
          </div>

          @if (linksState.showSkeleton()) {
            <ir-skeleton variant="list" [rows]="6" />
          } @else if (linksState.error()) {
            <ir-error-panel [message]="linksState.error()!" [showRetry]="false" />
          } @else if (links().length) {
            <ul class="link-list">
              @for (link of links(); track link.id) {
                <li class="link-row">
                  <div class="row-2 row-wrap">
                    <ir-badge [label]="link.status" [tone]="mappingStatusTone(link.status)" />
                    @if (link.required) {
                      <span class="badge tone-neutral">required</span>
                    }
                    @if (link.transformation) {
                      <span class="chip chip-mono" [title]="link.transformation">{{ link.transformation }}</span>
                    }
                  </div>

                  <div class="chain">
                    @for (node of link.nodes; track node.layer + node.field; let last = $last) {
                      <span class="node" [title]="node.layer + (node.type ? ' · ' + node.type : '')">
                        <span class="node-layer">{{ node.layer }}</span>
                        <span class="node-field mono">{{ node.field }}</span>
                      </span>
                      @if (!last) {
                        <ir-icon name="arrow-right" [size]="13" class="muted" />
                      }
                    }
                  </div>

                  @if (link.notes) {
                    <p class="text-xs muted">{{ link.notes }}</p>
                  }
                </li>
              }
            </ul>
          } @else if (selectedSetId()) {
            <ir-empty-state icon="check-circle" title="No mappings to show" body="Nothing matches the current filter for this set." />
          } @else {
            <ir-empty-state icon="eye" title="Nothing selected" body="Pick a mapping set to trace its fields end to end." />
          }
        </ir-section-card>
      </div>
    </div>
  `,
  styles: [
    `
      .set-row {
        display: flex; flex-direction: column; gap: var(--space-1); width: 100%;
        padding: var(--space-3) var(--space-4); text-align: left;
        border-bottom: 1px solid var(--border-subtle);
      }
      .set-row:hover { background: var(--surface-hover); }
      .set-row.selected { background: var(--accent-subtle-bg); }
      .link-list { max-height: 620px; overflow-y: auto; }
      .link-row {
        display: flex; flex-direction: column; gap: var(--space-2);
        padding: var(--space-3) var(--space-4);
        border-bottom: 1px solid var(--border-subtle);
      }
      .chain { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
      .node {
        display: flex; flex-direction: column;
        padding: var(--space-1) var(--space-2);
        border: 1px solid var(--border); border-radius: var(--radius-sm);
        background: var(--surface-sunken);
      }
      .node-layer { font-size: 9px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }
      .node-field { font-size: var(--text-xs); color: var(--text); }
    `,
  ],
})
export class MappingExplorerPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly setsState = new RequestState<Page<MappingSet>>();
  protected readonly linksState = new RequestState<Page<MappingLink>>();

  protected readonly setQuery = signal<MappingQuery>({ page: 0, size: 200 });
  protected readonly linkQuery = signal<MappingLinkQuery>({ page: 0, size: 500 });
  protected readonly selectedSetId = signal<string | null>(null);

  protected readonly humanise = humanise;
  protected readonly mappingStatusTone = mappingStatusTone;

  protected readonly sets = computed(() => this.setsState.data()?.items ?? []);
  protected readonly links = computed(() => this.linksState.data()?.items ?? []);

  protected readonly selectedSet = computed(() =>
    this.sets().find((set) => set.id === this.selectedSetId()) ?? null,
  );

  ngOnInit(): void {
    this.setsState.load(this.api.listMappingSets(this.setQuery()));
  }

  ngOnDestroy(): void {
    this.setsState.destroy();
    this.linksState.destroy();
  }

  protected onSetSearch(event: Event): void {
    const q = (event.target as HTMLInputElement).value;
    this.setQuery.update((current) => ({ ...current, q, page: 0 }));
    this.setsState.load(this.api.listMappingSets(this.setQuery()));
  }

  protected openSet(set: MappingSet): void {
    this.selectedSetId.set(set.id);
    this.loadLinks();
  }

  protected toggleIssuesOnly(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.linkQuery.update((current) => ({ ...current, issuesOnly: checked || undefined, page: 0 }));
    this.loadLinks();
  }

  private loadLinks(): void {
    const setId = this.selectedSetId();
    if (!setId) {
      return;
    }
    this.linksState.load(this.api.getMappingLinks(setId, this.linkQuery()));
  }
}
