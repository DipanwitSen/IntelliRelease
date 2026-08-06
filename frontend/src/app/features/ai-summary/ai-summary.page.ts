import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AiServiceStatus, AssistantMessage, AssistantSuggestion } from '../../core/models/intelligence';
import { ApiService } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { BadgeComponent, ProvenanceBadgeComponent } from '../../shared/components/badge/badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { EmptyStateComponent } from '../../shared/components/states/states.component';

/**
 * The AI assistant, with its boundaries stated on the page rather than in a
 * footnote.
 *
 * The banner is not decoration: it is the product's central claim. A user who
 * asks "should we ship this?" needs to know the answer they get is an
 * explanation of deterministic findings, not a decision.
 */
@Component({
  selector: 'ir-ai-summary',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BadgeComponent, ProvenanceBadgeComponent,
    IconComponent, EmptyStateComponent, FormsModule, RouterLink,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="AI Summary"
        subtitle="Ask about a change, an interface, a failure or a release. Answers are grounded in deterministic findings and cite them."
        icon="sparkles"
      >
        <div actions>
          @if (status.data(); as ai) {
            <ir-badge
              [label]="ai.available ? (ai.provider ?? 'AI') + ' · ' + (ai.model ?? 'model') : 'Deterministic fallback'"
              [tone]="ai.available ? 'ai' : 'warning'"
              [humanize]="false"
            />
          }
        </div>
      </ir-page-header>

      <div class="callout tone-ai">
        <ir-icon name="sparkles" [size]="16" class="callout-icon" />
        <div>
          <div class="callout-title">What this assistant does, and does not, do</div>
          It explains findings the deterministic engines already produced. It does not score risk,
          does not decide readiness, and cannot approve a release — those are
          <a routerLink="/risk-analysis">rule outputs</a> and human decisions. Verify anything you
          intend to act on.
        </div>
      </div>

      @if (status.data(); as ai) {
        @if (!ai.available) {
          <div class="callout tone-warning">
            <ir-icon name="alert-triangle" [size]="16" class="callout-icon" />
            <div>
              <div class="callout-title">AI service unavailable</div>
              {{ ai.message ?? 'The Python AI service is not reachable.' }}
              @if (ai.deterministicFallback) {
                Deterministic narration is still available everywhere in the product — only the
                conversational answers here are affected.
              }
            </div>
          </div>
        }
      }

      <ir-section-card title="Conversation" icon="sparkles" [flush]="true">
        <div class="thread" #thread>
          @if (!messages().length) {
            <ir-empty-state
              icon="sparkles"
              title="Ask something"
              body="Pick a starting point below, or type your own question."
            />
          }

          @for (message of messages(); track message.id) {
            <div class="message" [class.user]="message.role === 'user'">
              <div class="message-avatar">
                <ir-icon [name]="message.role === 'user' ? 'user' : 'sparkles'" [size]="14" />
              </div>
              <div class="message-body">
                @if (message.pending) {
                  <span class="muted">Thinking…</span>
                } @else if (message.error) {
                  <span class="fg-danger">{{ message.error }}</span>
                } @else {
                  <p class="message-text">{{ message.content }}</p>
                  @if (message.role === 'assistant') {
                    <div class="row-2" style="margin-top: var(--space-2)">
                      <ir-provenance [value]="message.provenance ?? 'AI_INFERENCE'" />
                      @if (message.citations?.length) {
                        <span class="text-xs muted">grounded in {{ message.citations!.length }} finding(s)</span>
                      }
                    </div>
                    @if (message.citations?.length) {
                      <div class="chip-row" style="margin-top: var(--space-2)">
                        @for (citation of message.citations!; track citation.label) {
                          @if (citation.routerLink) {
                            <a class="chip" [routerLink]="citation.routerLink">{{ citation.label }}</a>
                          } @else {
                            <span class="chip">{{ citation.label }}</span>
                          }
                        }
                      </div>
                    }
                  }
                }
              </div>
            </div>
          }
        </div>

        <div footer class="composer">
          @if (suggestions.data()?.length && !messages().length) {
            <div class="chip-row" style="margin-bottom: var(--space-3)">
              @for (suggestion of suggestions.data()!; track suggestion.prompt) {
                <button type="button" class="chip suggestion" (click)="ask(suggestion.prompt)">
                  {{ suggestion.label }}
                </button>
              }
            </div>
          }

          <form class="composer-row" (ngSubmit)="submit()">
            <input
              [(ngModel)]="draft"
              name="draft"
              placeholder="Explain this SOAP fault · Why did the target system reject this order · Show the order flow · Find a missing mapping"
              autocomplete="off"
              [disabled]="busy()"
            />
            <button type="submit" class="btn btn-primary" [disabled]="busy() || !draft.trim()">
              <ir-icon name="send" [size]="15" />
              Ask
            </button>
          </form>
        </div>
      </ir-section-card>
    </div>
  `,
  styles: [
    `
      .thread {
        display: flex;
        flex-direction: column;
        gap: var(--space-4);
        padding: var(--space-5);
        min-height: 320px;
        max-height: 60vh;
        overflow-y: auto;
      }

      .message { display: flex; gap: var(--space-3); }

      .message-avatar {
        width: 26px; height: 26px; flex: 0 0 auto;
        display: grid; place-items: center; border-radius: 50%;
        background: var(--ai-bg); color: var(--ai-fg);
      }

      .message.user .message-avatar { background: var(--accent-subtle-bg); color: var(--accent-subtle-fg); }

      .message-body {
        flex: 1; min-width: 0;
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-md);
        background: var(--surface-sunken);
        border: 1px solid var(--border-subtle);
      }

      .message.user .message-body { background: var(--accent-subtle-bg); border-color: transparent; }

      .message-text { font-size: var(--text-md); line-height: var(--leading-relaxed); white-space: pre-wrap; }

      .composer {
        padding: var(--space-4);
        border-top: 1px solid var(--border-subtle);
        background: var(--surface);
        border-radius: 0 0 var(--radius-lg) var(--radius-lg);
      }

      .composer-row { display: flex; gap: var(--space-2); }
      .composer-row input { flex: 1; height: 38px; }
      .suggestion { cursor: pointer; }
      .suggestion:hover { border-color: var(--accent); color: var(--accent); }
    `,
  ],
})
export class AiSummaryPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  private readonly thread = viewChild<ElementRef<HTMLElement>>('thread');

  protected readonly status = new RequestState<AiServiceStatus>();
  protected readonly suggestions = new RequestState<AssistantSuggestion[]>();

  private readonly history = signal<readonly AssistantMessage[]>([]);
  protected readonly messages = computed(() => this.history());

  protected readonly busy = signal(false);
  protected draft = '';

  private nextId = 1;

  ngOnInit(): void {
    this.status.load(this.api.aiStatus());
    this.suggestions.load(this.api.assistantSuggestions());
  }

  ngOnDestroy(): void {
    this.status.destroy();
    this.suggestions.destroy();
  }

  protected submit(): void {
    const message = this.draft.trim();
    if (message) {
      this.ask(message);
    }
  }

  protected ask(message: string): void {
    if (this.busy()) {
      return;
    }
    this.draft = '';
    this.busy.set(true);

    const userMessage: AssistantMessage = {
      id: `local-${this.nextId++}`,
      role: 'user',
      content: message,
      createdAt: new Date().toISOString(),
    };
    const placeholder: AssistantMessage = {
      id: `local-${this.nextId++}`,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
      pending: true,
    };

    this.history.update((current) => [...current, userMessage, placeholder]);
    this.scrollToBottom();

    this.api.askAssistant({ message }).subscribe({
      next: (reply) => {
        this.busy.set(false);
        this.replacePlaceholder(placeholder.id, reply);
      },
      error: (error: { error?: { message?: string } }) => {
        this.busy.set(false);
        this.replacePlaceholder(placeholder.id, {
          ...placeholder,
          pending: false,
          error: error?.error?.message
            ?? 'The assistant could not answer. Deterministic analysis elsewhere in the product is unaffected.',
        });
      },
    });
  }

  private replacePlaceholder(id: string, reply: AssistantMessage): void {
    this.history.update((current) =>
      current.map((message) => (message.id === id ? { ...reply, id } : message)),
    );
    this.scrollToBottom();
  }

  private scrollToBottom(): void {
    // Deferred so the new message is in the DOM before we measure its height.
    setTimeout(() => {
      const element = this.thread()?.nativeElement;
      if (element) {
        element.scrollTop = element.scrollHeight;
      }
    }, 0);
  }
}
