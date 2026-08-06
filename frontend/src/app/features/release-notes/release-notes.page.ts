import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Page } from '../../core/models/common';
import { ReleaseNotes, ReleaseSummary } from '../../core/models/delivery';
import { ApiService } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent, ProvenanceBadgeComponent } from '../../shared/components/badge/badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { releaseStatusTone } from '../../shared/tone';

type Audience = 'executive' | 'technical' | 'qa' | 'business' | 'customer';

/**
 * Release notes, per audience, previewed before anything is sent.
 *
 * The audience tabs are the point: an executive summary and a QA regression
 * list are different documents about the same change, and sending one to the
 * other's distribution list is how release communication loses its readers.
 */
@Component({
  selector: 'ir-release-notes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BadgeComponent, ProvenanceBadgeComponent,
    IconComponent, SkeletonComponent, ErrorPanelComponent, EmptyStateComponent, RouterLink,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Release Notes"
        subtitle="What each audience is told about a release — previewed here, and byte-identical to what actually goes out."
        icon="file-text"
      />

      <div class="toolbar card">
        <label class="row-2">
          <span class="def-label" style="margin: 0">Release</span>
          <select class="filter-select" [value]="selectedId() ?? ''" (change)="onSelect($event)" aria-label="Release">
            <option value="">Select a release…</option>
            @for (release of releases(); track release.releaseId) {
              <option [value]="release.releaseId">{{ release.version }} — {{ release.repoName }}</option>
            }
          </select>
        </label>
        @if (selectedRelease(); as release) {
          <ir-badge [label]="release.status" [tone]="releaseStatusTone(release.status)" />
          <a [routerLink]="['/releases', release.releaseId]" class="btn btn-sm btn-ghost">
            Open release
            <ir-icon name="chevron-right" [size]="13" />
          </a>
        }
      </div>

      @if (!selectedId()) {
        <div class="card">
          <ir-empty-state
            icon="file-text"
            title="Pick a release"
            body="Choose a release above to preview the notes each audience will receive."
          />
        </div>
      } @else if (notes.showSkeleton()) {
        <div class="card"><ir-skeleton [rows]="7" /></div>
      } @else if (notes.error()) {
        <ir-error-panel [message]="notes.error()!" (retry)="loadNotes()" />
      } @else {
      @if (notes.data(); as content) {
        @if (content.aiFallbackUsed) {
          <div class="callout tone-warning">
            <ir-icon name="alert-triangle" [size]="16" class="callout-icon" />
            <div>
              <div class="callout-title">Deterministic narration</div>
              The AI service was unavailable, so these notes came from the deterministic narrator.
              Every fact is still present — only the prose differs.
            </div>
          </div>
        }

        <ir-section-card title="Changelog" subtitle="One bullet per included pull request, from the PR's own title." icon="list">
          <div actions><ir-provenance value="DERIVED_FACT" /></div>
          @if (content.bullets.length) {
            <ul class="notes-list">
              @for (bullet of content.bullets; track bullet) {
                <li>{{ bullet }}</li>
              }
            </ul>
          } @else {
            <ir-empty-state icon="list" title="No entries" body="This release has no resolved pull requests yet. Build it first." />
          }
        </ir-section-card>

        @if (content.ai?.releaseNotes) {
          <ir-section-card title="Audience notes" icon="send">
            <div actions><ir-provenance value="AI_INFERENCE" /></div>

            <div class="tabs" role="tablist">
              @for (option of audiences; track option.id) {
                <button
                  type="button"
                  class="tab"
                  role="tab"
                  [attr.aria-selected]="audience() === option.id"
                  (click)="audience.set(option.id)"
                >
                  {{ option.label }}
                </button>
              }
            </div>

            <div class="audience-body">
              @if (audienceText(); as text) {
                <p class="text-base">{{ text }}</p>
              } @else {
                <ir-empty-state
                  icon="file-text"
                  title="Nothing written for this audience"
                  body="The AI service only writes a section when the deterministic pipeline gave it something to say. An empty section means there was nothing relevant, not that generation failed."
                />
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
      .notes-list { list-style: disc; padding-left: var(--space-5); font-size: var(--text-md); }
      .notes-list li { margin-bottom: var(--space-2); line-height: var(--leading-relaxed); }
      .audience-body { padding-top: var(--space-4); }
    `,
  ],
})
export class ReleaseNotesPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly list = new RequestState<Page<ReleaseSummary>>();
  protected readonly notes = new RequestState<ReleaseNotes>();

  protected readonly selectedId = signal<string | null>(null);
  protected readonly audience = signal<Audience>('executive');

  protected readonly releaseStatusTone = releaseStatusTone;

  protected readonly audiences: readonly { id: Audience; label: string }[] = [
    { id: 'executive', label: 'Executive' },
    { id: 'technical', label: 'Technical' },
    { id: 'qa', label: 'QA' },
    { id: 'business', label: 'Business' },
    { id: 'customer', label: 'Customer' },
  ];

  protected readonly releases = computed(() => this.list.data()?.items ?? []);

  protected readonly selectedRelease = computed(() =>
    this.releases().find((release) => release.releaseId === this.selectedId()) ?? null,
  );

  protected readonly audienceText = computed(() => {
    const notes = this.notes.data()?.ai?.releaseNotes;
    return notes ? notes[this.audience()] ?? null : null;
  });

  ngOnInit(): void {
    this.list.load(this.api.listReleases({ page: 0, size: 200 }));
  }

  ngOnDestroy(): void {
    this.list.destroy();
    this.notes.destroy();
  }

  protected onSelect(event: Event): void {
    const id = (event.target as HTMLSelectElement).value || null;
    this.selectedId.set(id);
    if (id) {
      this.loadNotes();
    } else {
      this.notes.reset();
    }
  }

  protected loadNotes(): void {
    const id = this.selectedId();
    if (id) {
      this.notes.load(this.api.getReleaseNotes(id));
    }
  }
}
