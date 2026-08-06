import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';

import { Page } from '../../core/models/common';
import { GlossaryTerm, KnowledgeArticle } from '../../core/models/intelligence';
import { ApiService, KnowledgeQuery } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent, ProvenanceBadgeComponent } from '../../shared/components/badge/badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent, ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { humanise } from '../../shared/tone';

/** Patterns, best practices, known issues, notes and glossary — searchable. */
@Component({
  selector: 'ir-knowledge-base',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BadgeComponent, ProvenanceBadgeComponent,
    IconComponent, SkeletonComponent, ErrorPanelComponent, EmptyStateComponent,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Knowledge Base"
        subtitle="The curated knowledge the deterministic engines reason from — readable by the people who maintain it."
        icon="book"
      />

      <div class="toolbar card">
        <label class="search-field">
          <span class="sr-only">Search the knowledge base</span>
          <ir-icon name="search" [size]="15" />
          <input
            type="search"
            placeholder="Search articles, patterns, notes, glossary…"
            [value]="query().q ?? ''"
            (input)="onSearch($event)"
            autocomplete="off"
          />
        </label>

        <select class="filter-select" [value]="query().category ?? ''" (change)="setCategory($event)" aria-label="Category">
          <option value="">All categories</option>
          @for (category of categories(); track category) {
            <option [value]="category">{{ humanise(category) }}</option>
          }
        </select>

        <span class="toolbar-spacer"></span>
        <span class="text-xs muted">{{ articles().length }} article{{ articles().length === 1 ? '' : 's' }}</span>
      </div>

      @if (state.showSkeleton()) {
        <div class="grid grid-3">
          <div class="card"><ir-skeleton [rows]="4" /></div>
          <div class="card"><ir-skeleton [rows]="4" /></div>
          <div class="card"><ir-skeleton [rows]="4" /></div>
        </div>
      } @else if (state.error()) {
        <ir-error-panel [message]="state.error()!" (retry)="reload()" />
      } @else if (articles().length) {
        <section class="grid grid-3">
          @for (article of articles(); track article.id) {
            <article class="card card-interactive" (click)="select(article)">
              <div class="card-body">
                <div class="row-2 row-wrap" style="margin-bottom: var(--space-2)">
                  <ir-badge [label]="article.category" tone="accent" />
                  <ir-provenance [value]="article.provenance" [showIcon]="false" />
                </div>
                <h3 class="card-title">{{ article.title }}</h3>
                <p class="text-sm secondary clamp-2" style="margin-top: var(--space-2)">{{ article.summary }}</p>
                @if (article.tags.length) {
                  <div class="chip-row" style="margin-top: var(--space-3)">
                    @for (tag of article.tags.slice(0, 4); track tag) {
                      <span class="chip">{{ tag }}</span>
                    }
                  </div>
                }
              </div>
            </article>
          }
        </section>
      } @else {
        <div class="card">
          <ir-empty-state
            icon="book"
            title="No articles found"
            body="The knowledge base is loaded from curated data files. Nothing matched this search."
          />
        </div>
      }

      @if (selected(); as article) {
        <ir-section-card [title]="article.title" [subtitle]="humanise(article.category)" icon="file-text">
          <div actions>
            <button type="button" class="btn btn-sm btn-ghost btn-icon" (click)="selected.set(null)" aria-label="Close">
              <ir-icon name="x" [size]="14" />
            </button>
          </div>
          <p class="text-base">{{ article.summary }}</p>
          @if (article.body) {
            <pre class="article-body">{{ article.body }}</pre>
          }
          @if (article.references.length) {
            <div style="margin-top: var(--space-4)">
              <div class="section-title">References</div>
              <ul class="stack-2" style="margin-top: var(--space-2)">
                @for (reference of article.references; track reference.label) {
                  <li class="row-2 text-sm">
                    <ir-icon name="external-link" [size]="13" class="muted" />
                    @if (reference.url) {
                      <a [href]="reference.url" target="_blank" rel="noopener noreferrer">{{ reference.label }}</a>
                    } @else {
                      <span>{{ reference.label }}</span>
                    }
                    <span class="chip">{{ humanise(reference.kind) }}</span>
                  </li>
                }
              </ul>
            </div>
          }
        </ir-section-card>
      }

      <ir-section-card title="Glossary" icon="list" [collapsible]="true" [expanded]="false" [count]="glossary.data()?.length ?? null">
        <div class="grid grid-3">
          @for (term of glossary.data() ?? []; track term.term) {
            <div>
              <div class="weight-semibold text-sm">{{ term.term }}</div>
              <div class="text-sm secondary">{{ term.definition }}</div>
              @if (term.aliases.length) {
                <div class="text-xs muted">Also: {{ term.aliases.join(', ') }}</div>
              }
            </div>
          }
        </div>
      </ir-section-card>
    </div>
  `,
  styles: [
    `
      .article-body {
        margin-top: var(--space-3);
        padding: var(--space-4);
        border-radius: var(--radius-md);
        background: var(--surface-sunken);
        font-family: var(--font-sans);
        font-size: var(--text-md);
        line-height: var(--leading-relaxed);
        white-space: pre-wrap;
        overflow-wrap: anywhere;
      }
    `,
  ],
})
export class KnowledgeBasePage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  protected readonly state = new RequestState<Page<KnowledgeArticle>>();
  protected readonly glossary = new RequestState<GlossaryTerm[]>();
  protected readonly query = signal<KnowledgeQuery>({ page: 0, size: 200 });
  protected readonly selected = signal<KnowledgeArticle | null>(null);

  protected readonly humanise = humanise;

  protected readonly articles = computed(() => this.state.data()?.items ?? []);

  protected readonly categories = computed(() =>
    [...new Set(this.articles().map((article) => article.category))].sort(),
  );

  ngOnInit(): void {
    this.reload();
    this.glossary.load(this.api.listGlossary());
  }

  ngOnDestroy(): void {
    this.state.destroy();
    this.glossary.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.listKnowledgeArticles(this.query()));
  }

  protected onSearch(event: Event): void {
    const q = (event.target as HTMLInputElement).value;
    this.query.update((current) => ({ ...current, q, page: 0 }));
    this.reload();
  }

  protected setCategory(event: Event): void {
    const category = (event.target as HTMLSelectElement).value || undefined;
    this.query.update((current) => ({ ...current, category, page: 0 }));
    this.reload();
  }

  /** Fetches the full body — the list endpoint returns summaries only. */
  protected select(article: KnowledgeArticle): void {
    this.selected.set(article);
    this.api.getKnowledgeArticle(article.id).subscribe({
      next: (full) => this.selected.set(full),
      error: () => {
        // The summary is already on screen; a failed body fetch is not worth
        // tearing the panel down for.
      },
    });
  }
}
