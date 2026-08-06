import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Page } from '../../core/models/common';
import { PayloadComparison, PayloadContent, PayloadDocument } from '../../core/models/integration';
import { ApiService, PayloadQuery } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { CodeViewerComponent } from '../../shared/components/code-viewer/code-viewer.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { FileSizePipe } from '../../shared/pipes/relative-time.pipe';
import { languageFor } from '../../shared/highlight';
import { humanise, severityTone } from '../../shared/tone';

type Tab = 'browse' | 'inspect' | 'compare';

/**
 * View, validate and diff payloads in whatever format they arrive in.
 *
 * Format is sniffed by the backend rather than trusted from a file extension —
 * a `.txt` holding an IDoc is still an IDoc, and a `.xml` holding a SOAP fault
 * deserves the fault-aware view rather than a generic XML tree.
 */
@Component({
  selector: 'ir-payload-explorer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, CodeViewerComponent, BadgeComponent,
    IconComponent, SkeletonComponent, ErrorPanelComponent, EmptyStateComponent,
    FileSizePipe, FormsModule,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Payload Explorer"
        subtitle="JSON, XML, SOAP, OData, EDMX, WSDL, XSD, IDoc, CSV, Excel, YAML, properties and flat files — viewed, validated and compared in one place."
        icon="braces"
      />

      <div class="tabs" role="tablist">
        @for (option of tabs; track option.id) {
          <button type="button" class="tab" role="tab" [attr.aria-selected]="tab() === option.id" (click)="tab.set(option.id)">
            {{ option.label }}
          </button>
        }
      </div>

      @switch (tab()) {
        @case ('browse') {
          <div class="grid grid-2">
            <ir-section-card title="Captured payloads" icon="list" [count]="documents().length" [flush]="true">
              <div class="toolbar">
                <label class="search-field">
                  <span class="sr-only">Search payloads</span>
                  <ir-icon name="search" [size]="15" />
                  <input type="search" placeholder="Search payloads…" [value]="query().q ?? ''" (input)="onSearch($event)" />
                </label>
                <select class="filter-select" [value]="query().format ?? ''" (change)="setFormat($event)" aria-label="Format">
                  <option value="">All formats</option>
                  @for (format of formats(); track format) {
                    <option [value]="format">{{ format }}</option>
                  }
                </select>
              </div>

              @if (list.showSkeleton()) {
                <ir-skeleton variant="list" [rows]="6" />
              } @else if (documents().length) {
                <ul class="doc-list">
                  @for (document of documents(); track document.id) {
                    <li>
                      <button type="button" class="doc-row" [class.selected]="document.id === selectedId()" (click)="openDocument(document)">
                        <span class="row-2">
                          <ir-badge [label]="document.format" tone="neutral" [humanize]="false" />
                          <span class="weight-medium truncate">{{ document.name }}</span>
                        </span>
                        <span class="text-xs muted">
                          {{ document.sizeBytes | fileSize }}
                          @if (document.businessObject) { · {{ document.businessObject }} }
                          @if (document.direction) { · {{ humanise(document.direction) }} }
                        </span>
                      </button>
                    </li>
                  }
                </ul>
              } @else {
                <ir-empty-state icon="braces" title="No payloads captured" body="Sample payloads are catalogued per interface. Use the Inspect tab to paste one directly." />
              }
            </ir-section-card>

            <ir-section-card [title]="content.data()?.document?.name ?? 'Select a payload'" icon="file-code" [flush]="true">
              @if (content.showSkeleton()) {
                <ir-skeleton [rows]="8" />
              } @else if (content.error()) {
                <ir-error-panel [message]="content.error()!" [showRetry]="false" />
              } @else {
      @if (content.data(); as payload) {
                <div class="viewer-pane">
                  @if (payload.validation.issues.length) {
                    <div class="callout tone-warning">
                      <ir-icon name="alert-triangle" [size]="16" class="callout-icon" />
                      <div>
                        <div class="callout-title">{{ payload.validation.issues.length }} validation issue(s)</div>
                        <ul class="stack-2" style="margin-top: var(--space-2)">
                          @for (issue of payload.validation.issues; track issue.path + issue.message) {
                            <li class="text-sm">
                              <ir-badge [label]="issue.severity" [tone]="severityTone(issue.severity)" />
                              <span class="mono">{{ issue.path }}</span> — {{ issue.message }}
                            </li>
                          }
                        </ul>
                      </div>
                    </div>
                  }
                  <ir-code-viewer
                    [content]="payload.raw"
                    [language]="languageFor(payload.document.format, payload.document.name)"
                    [filename]="payload.document.name"
                    [highlightedLines]="issueLines()"
                  />
                </div>
              } @else {
                <ir-empty-state icon="eye" title="Nothing selected" body="Pick a payload from the list to view, validate and search it." />
              }
              }
            </ir-section-card>
          </div>
        }

        @case ('inspect') {
          <ir-section-card title="Inspect any payload" subtitle="Paste anything. The format is detected, not assumed." icon="file-search">
            <div class="stack-3">
              <textarea rows="10" class="mono" [(ngModel)]="pastedContent" placeholder="Paste JSON, XML, SOAP, CSV, IDoc, YAML, properties…" spellcheck="false"></textarea>
              <div class="row-2">
                <input [(ngModel)]="pastedName" placeholder="Optional filename (helps the sniffer)" />
                <button type="button" class="btn btn-primary btn-sm" (click)="parse()" [disabled]="!pastedContent.trim()">
                  <ir-icon name="play" [size]="14" />
                  Parse
                </button>
              </div>
            </div>
          </ir-section-card>

          @if (parsed.data(); as payload) {
            <ir-section-card [title]="payload.document.name || 'Parsed payload'" [subtitle]="payload.document.format" icon="file-code" [flush]="true">
              <ir-code-viewer
                [content]="payload.raw"
                [language]="languageFor(payload.document.format, payload.document.name)"
              />
            </ir-section-card>
          } @else if (parsed.error()) {
            <ir-error-panel [message]="parsed.error()!" [showRetry]="false" />
          }
        }

        @default {
          <ir-section-card title="Compare two payloads or schemas" subtitle="Structural diff with breaking-change detection." icon="arrow-left-right">
            <div class="grid grid-2">
              <label class="field">
                <span class="def-label">Left</span>
                <textarea rows="10" class="mono" [(ngModel)]="leftContent" spellcheck="false"></textarea>
              </label>
              <label class="field">
                <span class="def-label">Right</span>
                <textarea rows="10" class="mono" [(ngModel)]="rightContent" spellcheck="false"></textarea>
              </label>
            </div>
            <button type="button" class="btn btn-primary btn-sm" style="margin-top: var(--space-3)" (click)="compare()" [disabled]="!leftContent.trim() || !rightContent.trim()">
              <ir-icon name="arrow-left-right" [size]="14" />
              Compare
            </button>
          </ir-section-card>

          @if (comparison.data(); as diff) {
            <ir-section-card title="Differences" icon="list" [count]="diff.differences.length">
              @if (diff.identical) {
                <ir-empty-state icon="check-circle" title="Identical" body="No structural differences between these two documents." />
              } @else {
                <div class="row-2 row-wrap" style="margin-bottom: var(--space-3)">
                  <ir-badge [label]="diff.summary.added + ' added'" tone="info" [humanize]="false" />
                  <ir-badge [label]="diff.summary.removed + ' removed'" tone="danger" [humanize]="false" />
                  <ir-badge [label]="diff.summary.changed + ' changed'" tone="warning" [humanize]="false" />
                  <ir-badge [label]="diff.summary.typeChanged + ' type changed'" tone="warning" [humanize]="false" />
                  <ir-badge [label]="diff.summary.unchanged + ' unchanged'" tone="neutral" [humanize]="false" />
                </div>
                <div class="stack-2">
                  @for (difference of diff.differences; track difference.path + difference.change) {
                    <div class="diff-row" [class.breaking]="difference.breaking">
                      <ir-badge [label]="difference.change" [tone]="difference.breaking ? 'danger' : 'neutral'" />
                      <span class="mono text-xs">{{ difference.path }}</span>
                      @if (difference.leftValue || difference.rightValue) {
                        <span class="text-xs muted truncate">{{ difference.leftValue ?? '∅' }} → {{ difference.rightValue ?? '∅' }}</span>
                      }
                      @if (difference.breaking) {
                        <span class="badge tone-danger">breaking</span>
                      }
                    </div>
                  }
                </div>
              }
            </ir-section-card>
          } @else if (comparison.error()) {
            <ir-error-panel [message]="comparison.error()!" [showRetry]="false" />
          }
        }
      }
    </div>
  `,
  styles: [
    `
      textarea, .field textarea { width: 100%; font-family: var(--font-mono); font-size: var(--text-xs); resize: vertical; }
      .field { display: flex; flex-direction: column; }
      .doc-list { max-height: 520px; overflow-y: auto; }
      .doc-row {
        display: flex; flex-direction: column; gap: 2px; width: 100%;
        padding: var(--space-3) var(--space-4); text-align: left;
        border-bottom: 1px solid var(--border-subtle);
      }
      .doc-row:hover { background: var(--surface-hover); }
      .doc-row.selected { background: var(--accent-subtle-bg); }
      .viewer-pane { padding: var(--space-4); display: flex; flex-direction: column; gap: var(--space-3); }
      .diff-row {
        display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap;
        padding: var(--space-2) var(--space-3);
        border: 1px solid var(--border-subtle); border-radius: var(--radius-sm);
      }
      .diff-row.breaking { border-color: var(--danger-fg); background: var(--danger-bg); }
    `,
  ],
})
export class PayloadExplorerPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly tab = signal<Tab>('browse');
  protected readonly tabs: readonly { id: Tab; label: string }[] = [
    { id: 'browse', label: 'Browse' },
    { id: 'inspect', label: 'Inspect' },
    { id: 'compare', label: 'Compare' },
  ];

  protected readonly list = new RequestState<Page<PayloadDocument>>();
  protected readonly content = new RequestState<PayloadContent>();
  protected readonly parsed = new RequestState<PayloadContent>();
  protected readonly comparison = new RequestState<PayloadComparison>();

  protected readonly query = signal<PayloadQuery>({ page: 0, size: 200 });
  protected readonly selectedId = signal<string | null>(null);

  protected pastedContent = '';
  protected pastedName = '';
  protected leftContent = '';
  protected rightContent = '';

  protected readonly languageFor = languageFor;
  protected readonly humanise = humanise;
  protected readonly severityTone = severityTone;

  protected readonly documents = computed(() => this.list.data()?.items ?? []);

  protected readonly formats = computed(() =>
    [...new Set(this.documents().map((document) => document.format))].sort(),
  );

  protected readonly issueLines = computed(() =>
    (this.content.data()?.validation.issues ?? [])
      .map((issue) => issue.line)
      .filter((line): line is number => typeof line === 'number'),
  );

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.list.destroy();
    this.content.destroy();
    this.parsed.destroy();
    this.comparison.destroy();
  }

  protected reload(): void {
    this.list.load(this.api.listPayloads(this.query()));
  }

  protected onSearch(event: Event): void {
    const q = (event.target as HTMLInputElement).value;
    this.query.update((current) => ({ ...current, q, page: 0 }));
    this.reload();
  }

  protected setFormat(event: Event): void {
    const format = (event.target as HTMLSelectElement).value || undefined;
    this.query.update((current) => ({ ...current, format, page: 0 }));
    this.reload();
  }

  protected openDocument(document: PayloadDocument): void {
    this.selectedId.set(document.id);
    this.content.load(this.api.getPayload(document.id));
  }

  protected parse(): void {
    this.parsed.load(this.api.parsePayload({ content: this.pastedContent, filename: this.pastedName || undefined }));
  }

  protected compare(): void {
    this.comparison.load(
      this.api.compareRawPayloads({ left: this.leftContent, right: this.rightContent }),
    );
  }
}
