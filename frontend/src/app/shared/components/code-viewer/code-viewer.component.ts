import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

import { ToastService } from '../../../core/services/toast.service';
import { HighlightLanguage, highlight, markMatches, prettyPrint } from '../../highlight';
import { IconComponent } from '../icon/icon.component';

/**
 * Read-only viewer for payloads, specs, stack traces and generated context.
 *
 * Line numbers live in their own column with `user-select: none`, so copying a
 * block from the viewer yields the payload and not "1 2 3 4" down the margin.
 *
 * Long documents are truncated at `maxLines` with an explicit "show all"
 * control. A 200,000-line IDoc dump would otherwise lock the tab, and silently
 * dropping the tail would be worse than saying so.
 */
@Component({
  selector: 'ir-code-viewer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, DecimalPipe],
  template: `
    <div class="viewer">
      @if (showToolbar()) {
        <div class="viewer-toolbar">
          @if (filename()) {
            <span class="filename mono truncate">{{ filename() }}</span>
          }
          <span class="lang">{{ language().toUpperCase() }}</span>
          <span class="viewer-spacer"></span>

          @if (searchable()) {
            <label class="viewer-search">
              <span class="sr-only">Search in document</span>
              <ir-icon name="search" [size]="13" />
              <input
                type="search"
                placeholder="Find…"
                [value]="searchTerm()"
                (input)="onSearch($event)"
                autocomplete="off"
              />
            </label>
          }

          <button
            type="button"
            class="btn btn-ghost btn-sm btn-icon"
            (click)="wrap.set(!wrap())"
            [attr.aria-pressed]="wrap()"
            title="Toggle line wrapping"
          >
            <ir-icon name="list" [size]="14" />
          </button>

          @if (allowPretty()) {
            <button
              type="button"
              class="btn btn-ghost btn-sm btn-icon"
              (click)="pretty.set(!pretty())"
              [attr.aria-pressed]="pretty()"
              title="Toggle pretty printing"
            >
              <ir-icon name="braces" [size]="14" />
            </button>
          }

          <button type="button" class="btn btn-ghost btn-sm btn-icon" (click)="copy()" title="Copy to clipboard">
            <ir-icon name="copy" [size]="14" />
          </button>
        </div>
      }

      <div class="viewer-body" [class.wrap]="wrap()">
        @if (showLineNumbers()) {
          <div class="gutter" aria-hidden="true">
            @for (line of lineNumbers(); track line) {
              <span [class.highlighted]="highlightedLines().includes(line)">{{ line }}</span>
            }
          </div>
        }
        <pre class="code"><code [innerHTML]="rendered()"></code></pre>
      </div>

      @if (truncated()) {
        <div class="viewer-footer">
          Showing the first {{ maxLines() | number }} of {{ totalLines() | number }} lines.
          <button type="button" class="btn btn-sm btn-ghost" (click)="showAll.set(true)">Show all</button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      :host { display: block; min-width: 0; }

      .viewer {
        border: 1px solid var(--border);
        border-radius: var(--radius-md);
        overflow: hidden;
        background: var(--code-bg);
      }

      .viewer-toolbar {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        padding: var(--space-2) var(--space-3);
        background: var(--surface-sunken);
        border-bottom: 1px solid var(--border);
      }

      .filename { font-size: var(--text-xs); color: var(--text-secondary); }

      .lang {
        padding: 1px 6px;
        border-radius: var(--radius-xs);
        background: var(--surface);
        border: 1px solid var(--border-subtle);
        font-size: var(--text-2xs);
        font-weight: var(--weight-semibold);
        color: var(--text-muted);
        letter-spacing: 0.04em;
      }

      .viewer-spacer { flex: 1; }

      .viewer-search {
        display: flex;
        align-items: center;
        gap: var(--space-1);
        height: 26px;
        padding-inline: var(--space-2);
        border: 1px solid var(--border);
        border-radius: var(--radius-sm);
        background: var(--surface);
        color: var(--text-muted);
      }

      .viewer-search input {
        border: none;
        background: none;
        padding: 0;
        width: 110px;
        font-size: var(--text-xs);
      }

      .viewer-search input:focus-visible { outline: none; box-shadow: none; }

      .viewer-body {
        display: flex;
        max-height: var(--code-max-height, 560px);
        overflow: auto;
        font-family: var(--font-mono);
        font-size: var(--text-xs);
        line-height: 1.55;
      }

      .gutter {
        display: flex;
        flex-direction: column;
        padding: var(--space-3) var(--space-2);
        text-align: right;
        color: var(--code-gutter);
        background: color-mix(in srgb, var(--code-bg) 92%, #fff);
        border-right: 1px solid rgb(255 255 255 / 0.06);
        user-select: none;
        flex: 0 0 auto;
        position: sticky;
        left: 0;
        z-index: 1;
      }

      .gutter span { padding-inline: 2px; }
      .gutter .highlighted { color: var(--warning-fg); font-weight: var(--weight-semibold); }

      .code {
        margin: 0;
        padding: var(--space-3);
        color: var(--code-text);
        flex: 1;
        min-width: 0;
      }

      .viewer-body.wrap .code {
        white-space: pre-wrap;
        overflow-wrap: anywhere;
      }

      .viewer-footer {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        padding: var(--space-2) var(--space-3);
        background: var(--surface-sunken);
        border-top: 1px solid var(--border);
        font-size: var(--text-xs);
        color: var(--text-muted);
      }

      /* Token colours — set on the host so they resolve inside innerHTML. */
      .code ::ng-deep .t-key     { color: var(--code-key); }
      .code ::ng-deep .t-string  { color: var(--code-string); }
      .code ::ng-deep .t-number  { color: var(--code-number); }
      .code ::ng-deep .t-boolean { color: var(--code-boolean); }
      .code ::ng-deep .t-null    { color: var(--code-null); }
      .code ::ng-deep .t-tag     { color: var(--code-tag); }
      .code ::ng-deep .t-attr    { color: var(--code-attr); }
      .code ::ng-deep .t-comment { color: var(--code-comment); font-style: italic; }

      .code ::ng-deep mark {
        background: var(--warning-solid);
        color: #000;
        border-radius: 2px;
        padding: 0 1px;
      }
    `,
  ],
})
export class CodeViewerComponent {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toast = inject(ToastService);

  readonly content = input<string>('');
  readonly language = input<HighlightLanguage>('text');
  readonly filename = input<string | null>(null);
  readonly showToolbar = input(true);
  readonly showLineNumbers = input(true);
  readonly searchable = input(true);
  readonly allowPretty = input(true);
  readonly maxLines = input(1500);
  /** 1-based line numbers to flag in the gutter — validation errors point here. */
  readonly highlightedLines = input<readonly number[]>([]);

  protected readonly wrap = signal(false);
  protected readonly pretty = signal(true);
  protected readonly showAll = signal(false);
  protected readonly searchTerm = signal('');

  private readonly formatted = computed(() =>
    this.pretty() && this.allowPretty()
      ? prettyPrint(this.content(), this.language())
      : this.content(),
  );

  private readonly allLines = computed(() => this.formatted().split('\n'));

  protected readonly totalLines = computed(() => this.allLines().length);

  protected readonly truncated = computed(
    () => !this.showAll() && this.totalLines() > this.maxLines(),
  );

  private readonly visibleText = computed(() =>
    this.truncated() ? this.allLines().slice(0, this.maxLines()).join('\n') : this.formatted(),
  );

  protected readonly lineNumbers = computed(() => {
    const count = this.truncated() ? this.maxLines() : this.totalLines();
    return Array.from({ length: count }, (_, index) => index + 1);
  });

  /**
   * Safe because `highlight()` HTML-escapes the payload before inserting any
   * markup, and `markMatches()` only ever wraps text outside tags.
   */
  protected readonly rendered = computed<SafeHtml>(() => {
    const html = highlight(this.visibleText(), this.language());
    const withMarks = markMatches(html, this.searchTerm());
    return this.sanitizer.bypassSecurityTrustHtml(withMarks);
  });

  protected onSearch(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value);
  }

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.formatted());
      this.toast.success('Copied to clipboard');
    } catch {
      this.toast.error('Could not copy', 'The browser denied clipboard access.');
    }
  }
}
