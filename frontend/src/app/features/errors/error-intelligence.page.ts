import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Page } from '../../core/models/common';
import { ErrorExplanation, ErrorOccurrence, ErrorPattern } from '../../core/models/intelligence';
import { ApiService, ErrorQuery } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent, ProvenanceBadgeComponent } from '../../shared/components/badge/badge.component';
import { CellTemplateDirective, DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { humanise, riskTone, severityTone } from '../../shared/tone';

type Tab = 'explain' | 'catalogue' | 'occurrences';

/**
 * Error Intelligence — a raw exception in, a decision out.
 *
 * The explanation is assembled deterministically: signatures match a curated
 * pattern, and the pattern carries the causes, fixes and tests. The AI
 * narrative is additive and clearly labelled — if the model is unavailable,
 * everything that matters is still here. That ordering is the point of the
 * module, not an implementation detail.
 */
@Component({
  selector: 'ir-error-intelligence',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, DataTableComponent, CellTemplateDirective,
    BadgeComponent, ProvenanceBadgeComponent, IconComponent, SkeletonComponent,
    ErrorPanelComponent, EmptyStateComponent, RelativeTimePipe, FormsModule,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Error Intelligence"
        subtitle="Paste an exception, a SOAP fault, a message-processing log or an adapter error. Get what happened, where, why, and what to do — in that order."
        icon="alert-triangle"
      />

      <div class="tabs" role="tablist">
        @for (option of tabs; track option.id) {
          <button
            type="button"
            class="tab"
            role="tab"
            [attr.aria-selected]="tab() === option.id"
            (click)="tab.set(option.id)"
          >
            {{ option.label }}
          </button>
        }
      </div>

      @switch (tab()) {
        @case ('explain') {
          <ir-section-card title="Explain an error" icon="sparkles">
            <div class="stack-3">
              <label class="field">
                <span class="def-label">Raw error text</span>
                <textarea
                  rows="8"
                  [(ngModel)]="rawInput"
                  placeholder="e.g. com.sap.hybris.integration.MappingException: no column found for attribute 'catalogVersion' …"
                  spellcheck="false"
                  class="mono"
                ></textarea>
              </label>

              <div class="row-2 row-wrap">
                <button type="button" class="btn btn-primary btn-sm" (click)="explain()" [disabled]="!rawInput.trim() || explanation.loading()">
                  <ir-icon name="search" [size]="14" />
                  Explain
                </button>
                <label class="row-2 text-sm secondary">
                  <input type="checkbox" [(ngModel)]="includeNarrative" />
                  Include AI narrative
                </label>
                <span class="text-xs muted">
                  Nothing is sent to a model unless you tick that box. Classification is deterministic either way.
                </span>
              </div>
            </div>
          </ir-section-card>

          @if (explanation.showSkeleton()) {
            <div class="card"><ir-skeleton [rows]="6" /></div>
          } @else if (explanation.error()) {
            <ir-error-panel [message]="explanation.error()!" (retry)="explain()" />
          } @else {
      @if (explanation.data(); as result) {
            @if (!result.matched) {
              <ir-section-card title="No catalogued pattern matched" icon="help-circle">
                <p class="text-md secondary">{{ result.unmatchedGuidance ?? 'Nothing in the catalogue recognised this text. That is reported rather than guessed at.' }}</p>
              </ir-section-card>
            } @else {
              <ir-section-card [title]="result.pattern!.title" icon="alert-triangle">
                <div actions class="row-2">
                  <ir-badge [label]="result.pattern!.severity" [tone]="severityTone(result.pattern!.severity)" />
                  <ir-badge [label]="result.pattern!.category" tone="neutral" />
                  <span class="chip">Match {{ result.matchConfidence }}%</span>
                  <ir-provenance [value]="result.provenance" />
                </div>

                <div class="stack-6">
                  <div>
                    <div class="section-title">What happened</div>
                    <p class="text-base">{{ result.whatHappened }}</p>
                  </div>

                  <div>
                    <div class="section-title">In business terms</div>
                    <p class="text-base">{{ result.pattern!.businessExplanation }}</p>
                  </div>

                  @if (result.whereItFailed.length) {
                    <div>
                      <div class="section-title">Where it failed</div>
                      <div class="stack-2">
                        @for (location of result.whereItFailed; track location.layer + location.component) {
                          <div class="location">
                            <div class="row-2">
                              <ir-badge [label]="location.layer" tone="info" [humanize]="false" />
                              <span class="weight-medium">{{ location.component }}</span>
                              <span class="text-xs muted">confidence {{ humanise(location.confidence) }}</span>
                            </div>
                            <p class="text-sm secondary">{{ location.detail }}</p>
                            <p class="text-xs muted">{{ location.evidence }}</p>
                          </div>
                        }
                      </div>
                    </div>
                  }

                  @if (result.pattern!.rootCauses.length) {
                    <div>
                      <div class="section-title">Possible root causes</div>
                      <ul class="stack-2">
                        @for (cause of result.pattern!.rootCauses; track cause.id) {
                          <li class="cause">
                            <div class="row-2">
                              <ir-badge [label]="cause.likelihood" [tone]="cause.likelihood === 'HIGH' ? 'danger' : cause.likelihood === 'MEDIUM' ? 'warning' : 'neutral'" />
                              <span class="weight-medium">{{ cause.cause }}</span>
                            </div>
                            <p class="text-sm secondary">Confirm by: {{ cause.howToConfirm }}</p>
                          </li>
                        }
                      </ul>
                    </div>
                  }

                  @if (result.pattern!.suggestedFixes.length) {
                    <div>
                      <div class="section-title">Suggested fixes</div>
                      <ul class="stack-2">
                        @for (fix of result.pattern!.suggestedFixes; track fix.id) {
                          <li class="cause">
                            <div class="row-2">
                              <ir-badge [label]="fix.risk" [tone]="riskTone(fix.risk)" />
                              <ir-badge [label]="fix.effort" tone="neutral" />
                              <span class="weight-medium">{{ fix.summary }}</span>
                              <ir-provenance [value]="fix.provenance" [showIcon]="false" />
                            </div>
                            <p class="text-sm secondary">{{ fix.detail }}</p>
                          </li>
                        }
                      </ul>
                    </div>
                  }

                  <div class="grid grid-3">
                    @if (result.affectedInterfaces.length) {
                      <div>
                        <div class="section-title">Affected interfaces</div>
                        <div class="chip-row">
                          @for (item of result.affectedInterfaces; track item) {
                            <span class="chip">{{ item }}</span>
                          }
                        </div>
                      </div>
                    }
                    @if (result.affectedPayloadFields.length) {
                      <div>
                        <div class="section-title">Affected payload fields</div>
                        <div class="chip-row">
                          @for (field of result.affectedPayloadFields; track field) {
                            <span class="chip chip-mono">{{ field }}</span>
                          }
                        </div>
                      </div>
                    }
                    @if (result.affectedReleases.length) {
                      <div>
                        <div class="section-title">Affected releases</div>
                        <div class="chip-row">
                          @for (release of result.affectedReleases; track release.releaseId) {
                            <span class="chip chip-mono">{{ release.version }}</span>
                          }
                        </div>
                      </div>
                    }
                  </div>

                  @if (result.pattern!.recommendedTests.length) {
                    <div>
                      <div class="section-title">Recommended tests</div>
                      <ul class="bullet-list">
                        @for (test of result.pattern!.recommendedTests; track test) {
                          <li>{{ test }}</li>
                        }
                      </ul>
                    </div>
                  }

                  @if (result.narrative) {
                    <div class="callout tone-ai">
                      <ir-icon name="sparkles" [size]="16" class="callout-icon" />
                      <div>
                        <div class="callout-title">AI narrative</div>
                        <p>{{ result.narrative }}</p>
                        <p class="text-xs muted">
                          Written by a model to explain the deterministic findings above. It decided nothing — verify before acting.
                        </p>
                      </div>
                    </div>
                  }
                </div>
              </ir-section-card>
            }
          }
      }
        }

        @case ('catalogue') {
          <ir-data-table
            [columns]="patternColumns"
            [rows]="patternRows()"
            [loading]="patterns.showSkeleton()"
            [serverSide]="true"
            [totalCount]="patterns.data()?.total ?? null"
            noun="error patterns"
            caption="Catalogued error patterns"
            [trackBy]="trackPattern"
            [searchTerm]="patternQuery().q ?? ''"
            (searchTermChange)="onPatternSearch($event)"
            searchPlaceholder="Search patterns, codes, symptoms…"
            emptyTitle="No patterns catalogued"
            emptyIcon="book"
          >
            <ng-template irCell="title" let-row>
              <span class="weight-medium">{{ row.title }}</span>
              <div class="text-xs muted clamp-2">{{ row.businessExplanation }}</div>
            </ng-template>
            <ng-template irCell="severity" let-row>
              <ir-badge [label]="row.severity" [tone]="severityTone(row.severity)" />
            </ng-template>
            <ng-template irCell="layers" let-row>
              <div class="chip-row">
                @for (layer of row.layers.slice(0, 3); track layer.layer) {
                  <span class="chip">{{ layer.layer }}</span>
                }
              </div>
            </ng-template>
          </ir-data-table>
        }

        @default {
          <ir-data-table
            [columns]="occurrenceColumns"
            [rows]="occurrenceRows()"
            [loading]="occurrences.showSkeleton()"
            [serverSide]="true"
            [totalCount]="occurrences.data()?.total ?? null"
            noun="occurrences"
            caption="Recorded error occurrences"
            [trackBy]="trackOccurrence"
            emptyTitle="No errors recorded"
            emptyBody="Occurrences are recorded as the platform observes them. Nothing has failed yet."
            emptyIcon="check-circle"
          >
            <ng-template irCell="title" let-row>
              <span class="weight-medium">{{ row.title }}</span>
              <div class="text-xs muted mono clamp-2">{{ row.excerpt }}</div>
            </ng-template>
            <ng-template irCell="severity" let-row>
              <ir-badge [label]="row.severity" [tone]="severityTone(row.severity)" />
            </ng-template>
            <ng-template irCell="occurredAt" let-row>{{ row.occurredAt | relativeTime }}</ng-template>
          </ir-data-table>
        }
      }
    </div>
  `,
  styles: [
    `
      textarea {
        width: 100%;
        font-family: var(--font-mono);
        font-size: var(--text-xs);
        resize: vertical;
      }
      .field { display: flex; flex-direction: column; }
      .location, .cause {
        padding: var(--space-3);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-md);
        background: var(--surface-sunken);
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
      }
      .bullet-list { list-style: disc; padding-left: var(--space-5); font-size: var(--text-md); }
      .bullet-list li { margin-bottom: var(--space-1); }
    `,
  ],
})
export class ErrorIntelligencePage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly tab = signal<Tab>('explain');
  protected readonly tabs: readonly { id: Tab; label: string }[] = [
    { id: 'explain', label: 'Explain an error' },
    { id: 'catalogue', label: 'Pattern catalogue' },
    { id: 'occurrences', label: 'Recent occurrences' },
  ];

  protected rawInput = '';
  protected includeNarrative = false;

  protected readonly explanation = new RequestState<ErrorExplanation>();
  protected readonly patterns = new RequestState<Page<ErrorPattern>>();
  protected readonly occurrences = new RequestState<Page<ErrorOccurrence>>();

  protected readonly patternQuery = signal<ErrorQuery>({ page: 0, size: 100 });

  protected readonly severityTone = severityTone;
  protected readonly riskTone = riskTone;
  protected readonly humanise = humanise;

  protected readonly patternColumns: readonly TableColumn<ErrorPattern>[] = [
    { id: 'code', label: 'Code', mono: true, value: (row) => row.code, hideBelow: 'sm' },
    { id: 'title', label: 'Pattern', value: (row) => row.title },
    { id: 'category', label: 'Category', value: (row) => row.category, hideBelow: 'md' },
    { id: 'severity', label: 'Severity', value: (row) => row.severity },
    { id: 'layers', label: 'Layers', sortable: false, hideBelow: 'md' },
  ];

  protected readonly occurrenceColumns: readonly TableColumn<ErrorOccurrence>[] = [
    { id: 'title', label: 'Error', value: (row) => row.title },
    { id: 'layer', label: 'Layer', value: (row) => row.layer, hideBelow: 'sm' },
    { id: 'severity', label: 'Severity', value: (row) => row.severity },
    { id: 'count', label: 'Count', align: 'right', value: (row) => row.count, hideBelow: 'sm' },
    { id: 'occurredAt', label: 'Last seen', value: (row) => row.occurredAt },
  ];

  protected readonly trackPattern = (row: ErrorPattern) => row.id;
  protected readonly trackOccurrence = (row: ErrorOccurrence) => row.id;

  protected readonly patternRows = computed(() => this.patterns.data()?.items ?? []);
  protected readonly occurrenceRows = computed(() => this.occurrences.data()?.items ?? []);

  ngOnInit(): void {
    this.patterns.load(this.api.listErrorPatterns(this.patternQuery()));
    this.occurrences.load(this.api.listErrorOccurrences({ page: 0, size: 100 }));
  }

  ngOnDestroy(): void {
    this.explanation.destroy();
    this.patterns.destroy();
    this.occurrences.destroy();
  }

  protected explain(): void {
    const content = this.rawInput.trim();
    if (!content) {
      return;
    }
    this.explanation.load(this.api.explainError({ content, includeNarrative: this.includeNarrative }));
  }

  protected onPatternSearch(term: string): void {
    this.patternQuery.update((current) => ({ ...current, q: term, page: 0 }));
    this.patterns.load(this.api.listErrorPatterns(this.patternQuery()));
  }
}
