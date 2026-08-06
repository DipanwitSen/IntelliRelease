import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, model, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';

import { NAV_ITEMS, NavItem } from '../../core/navigation';
import { ThemeService } from '../../core/services/theme.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

interface Command {
  readonly id: string;
  readonly label: string;
  readonly hint: string;
  readonly icon: string;
  readonly group: string;
  readonly keywords: readonly string[];
  readonly run: () => void;
}

/**
 * Ctrl/Cmd-K launcher.
 *
 * Scoring is intentionally simple and predictable: an exact label match beats
 * a label prefix, which beats a label substring, which beats a keyword hit.
 * Fuzzy subsequence matching was tried and rejected — with only ~20 targets it
 * mostly produced surprising top hits, and surprise is the one thing a
 * keyboard launcher cannot afford.
 */
@Component({
  selector: 'ir-command-palette',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (open()) {
      <div class="scrim" (click)="close()" role="presentation"></div>

      <div class="palette" role="dialog" aria-modal="true" aria-label="Command palette">
        <div class="palette-input">
          <ir-icon name="search" [size]="17" class="muted" />
          <input
            #queryInput
            type="text"
            placeholder="Jump to a module, or search commands…"
            [value]="query()"
            (input)="onQuery($event)"
            (keydown)="onKeydown($event)"
            aria-label="Search commands"
            autocomplete="off"
            spellcheck="false"
          />
          <kbd class="kbd">Esc</kbd>
        </div>

        <div class="palette-results" role="listbox">
          @if (!results().length) {
            <div class="palette-empty">
              <ir-icon name="search" [size]="20" />
              <span>No match for “{{ query() }}”</span>
            </div>
          }

          @for (group of grouped(); track group.name) {
            <div class="palette-group">{{ group.name }}</div>
            @for (command of group.commands; track command.id) {
              <button
                type="button"
                class="palette-item"
                role="option"
                [class.selected]="command.id === selectedId()"
                [attr.aria-selected]="command.id === selectedId()"
                (mouseenter)="selectById(command.id)"
                (click)="run(command)"
              >
                <ir-icon [name]="command.icon" [size]="16" />
                <span class="palette-label">{{ command.label }}</span>
                <span class="palette-hint">{{ command.hint }}</span>
              </button>
            }
          }
        </div>

        <div class="palette-footer">
          <span><kbd class="kbd">↑</kbd><kbd class="kbd">↓</kbd> navigate</span>
          <span><kbd class="kbd">↵</kbd> open</span>
          <span class="spacer"></span>
          <span>{{ results().length }} result{{ results().length === 1 ? '' : 's' }}</span>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .scrim {
        position: fixed;
        inset: 0;
        background: var(--overlay-scrim);
        z-index: var(--z-modal);
        animation: fade-in var(--duration-fast) var(--ease-out);
      }

      .palette {
        position: fixed;
        top: 12vh;
        left: 50%;
        transform: translateX(-50%);
        width: min(620px, calc(100vw - 2rem));
        max-height: 70vh;
        display: flex;
        flex-direction: column;
        background: var(--surface-raised);
        border: 1px solid var(--border);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-xl);
        z-index: var(--z-modal);
        overflow: hidden;
        animation: palette-in var(--duration-normal) var(--ease-out);
      }

      @keyframes palette-in {
        from { opacity: 0; transform: translateX(-50%) translateY(-8px) scale(0.985); }
        to   { opacity: 1; transform: translateX(-50%) translateY(0) scale(1); }
      }

      .palette-input {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        padding: var(--space-3) var(--space-4);
        border-bottom: 1px solid var(--border-subtle);
        flex: 0 0 auto;
      }

      .palette-input input {
        flex: 1;
        border: none;
        background: none;
        padding: 0;
        font-size: var(--text-base);
      }

      .palette-input input:focus-visible {
        outline: none;
        box-shadow: none;
      }

      .palette-results {
        overflow-y: auto;
        padding: var(--space-2);
        flex: 1 1 auto;
      }

      .palette-group {
        padding: var(--space-2) var(--space-3) var(--space-1);
        font-size: var(--text-2xs);
        font-weight: var(--weight-semibold);
        text-transform: uppercase;
        letter-spacing: 0.06em;
        color: var(--text-muted);
      }

      .palette-item {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        width: 100%;
        padding: var(--space-2) var(--space-3);
        border-radius: var(--radius-sm);
        text-align: left;
        color: var(--text-secondary);
        font-size: var(--text-md);
      }

      .palette-item.selected {
        background: var(--accent-subtle-bg);
        color: var(--accent-subtle-fg);
      }

      .palette-label {
        font-weight: var(--weight-medium);
        white-space: nowrap;
      }

      .palette-hint {
        margin-left: auto;
        font-size: var(--text-xs);
        color: var(--text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 55%;
      }

      .palette-empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-2);
        padding: var(--space-10);
        color: var(--text-muted);
        font-size: var(--text-md);
      }

      .palette-footer {
        display: flex;
        align-items: center;
        gap: var(--space-4);
        padding: var(--space-2) var(--space-4);
        border-top: 1px solid var(--border-subtle);
        background: var(--surface-sunken);
        font-size: var(--text-2xs);
        color: var(--text-muted);
        flex: 0 0 auto;
      }

      .palette-footer span {
        display: inline-flex;
        align-items: center;
        gap: 4px;
      }

      .spacer { flex: 1; }

      .kbd {
        display: inline-grid;
        place-items: center;
        min-width: 18px;
        height: 18px;
        padding-inline: 4px;
        border: 1px solid var(--border);
        border-bottom-width: 2px;
        border-radius: var(--radius-xs);
        background: var(--surface);
        font-family: var(--font-sans);
        font-size: var(--text-2xs);
        color: var(--text-muted);
      }
    `,
  ],
})
export class CommandPaletteComponent {
  private readonly router = inject(Router);
  private readonly theme = inject(ThemeService);

  readonly open = model(false);

  private readonly queryInput = viewChild<ElementRef<HTMLInputElement>>('queryInput');

  protected readonly query = signal('');
  protected readonly selectedId = signal<string | null>(null);

  private readonly commands = computed<readonly Command[]>(() => [
    ...NAV_ITEMS.map((item) => this.navCommand(item)),
    {
      id: 'action:theme',
      label: 'Toggle theme',
      hint: 'Light, dark, or follow system',
      icon: 'sun',
      group: 'Actions',
      keywords: ['theme', 'dark', 'light', 'appearance', 'contrast'],
      run: () => this.theme.cycle(),
    },
    {
      id: 'action:new-release',
      label: 'Create a release',
      hint: 'Resolve contents from a Git ref range',
      icon: 'rocket',
      group: 'Actions',
      keywords: ['release', 'new', 'create', 'cut', 'version'],
      run: () => void this.router.navigate(['/releases'], { queryParams: { create: 1 } }),
    },
    {
      id: 'action:explain-error',
      label: 'Explain an error',
      hint: 'Paste a stack trace, SOAP fault or message log',
      icon: 'alert-triangle',
      group: 'Actions',
      keywords: ['error', 'explain', 'stack', 'trace', 'fault', 'debug', 'rca'],
      run: () => void this.router.navigate(['/errors'], { queryParams: { tab: 'explain' } }),
    },
    {
      id: 'action:compare-payloads',
      label: 'Compare two payloads',
      hint: 'Structural diff with breaking-change detection',
      icon: 'arrow-left-right',
      group: 'Actions',
      keywords: ['compare', 'diff', 'payload', 'schema', 'version'],
      run: () => void this.router.navigate(['/payloads'], { queryParams: { tab: 'compare' } }),
    },
  ]);

  protected readonly results = computed<readonly Command[]>(() => {
    const term = this.query().trim().toLowerCase();
    const all = this.commands();
    if (!term) {
      return all;
    }
    return all
      .map((command) => ({ command, score: this.score(command, term) }))
      .filter((entry) => entry.score > 0)
      .sort((a, b) => b.score - a.score)
      .map((entry) => entry.command);
  });

  protected readonly grouped = computed(() => {
    const groups = new Map<string, Command[]>();
    for (const command of this.results()) {
      const list = groups.get(command.group) ?? [];
      list.push(command);
      groups.set(command.group, list);
    }
    return [...groups.entries()].map(([name, commands]) => ({ name, commands }));
  });

  constructor() {
    // Reset and focus whenever the palette opens. The timeout waits for the
    // @if block to render the input before we reach for it.
    effect(() => {
      if (this.open()) {
        this.query.set('');
        setTimeout(() => this.queryInput()?.nativeElement.focus(), 0);
      }
    });

    // Keep a valid selection as the result list narrows under typing.
    effect(() => {
      const results = this.results();
      const current = this.selectedId();
      if (!results.some((command) => command.id === current)) {
        this.selectedId.set(results[0]?.id ?? null);
      }
    });
  }

  close(): void {
    this.open.set(false);
  }

  protected onQuery(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  protected selectById(id: string): void {
    this.selectedId.set(id);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const results = this.results();
    if (!results.length && event.key !== 'Escape') {
      return;
    }
    const index = results.findIndex((command) => command.id === this.selectedId());

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.selectedId.set(results[(index + 1) % results.length].id);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.selectedId.set(results[(index - 1 + results.length) % results.length].id);
        break;
      case 'Enter': {
        event.preventDefault();
        const chosen = results[index] ?? results[0];
        if (chosen) {
          this.run(chosen);
        }
        break;
      }
      case 'Escape':
        event.preventDefault();
        this.close();
        break;
      default:
        break;
    }
  }

  protected run(command: Command): void {
    this.close();
    command.run();
  }

  private navCommand(item: NavItem): Command {
    return {
      id: `nav:${item.id}`,
      label: item.label,
      hint: item.description,
      icon: item.icon,
      group: 'Go to',
      keywords: item.keywords ?? [],
      run: () => void this.router.navigateByUrl(item.route),
    };
  }

  private score(command: Command, term: string): number {
    const label = command.label.toLowerCase();
    if (label === term) return 100;
    if (label.startsWith(term)) return 80;
    if (label.includes(term)) return 60;
    if (command.keywords.some((keyword) => keyword.startsWith(term))) return 40;
    if (command.keywords.some((keyword) => keyword.includes(term))) return 25;
    if (command.hint.toLowerCase().includes(term)) return 10;
    return 0;
  }
}
