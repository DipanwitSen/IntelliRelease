import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';

import { Tone } from '../../../core/models/common';
import { IconComponent } from '../icon/icon.component';

export interface DiagramStage {
  readonly id: string;
  readonly label: string;
  readonly kind: string;
  readonly detail?: string;
  readonly badges?: readonly string[];
  readonly tone?: Tone;
  readonly optional?: boolean;
  readonly issueCount?: number;
}

export interface DiagramBranch {
  readonly id: string;
  readonly label: string;
  readonly fromStageId: string;
  readonly toStageId: string;
  readonly kind: string;
}

/**
 * Renders a stage chain: Commerce → … → target system.
 *
 * Laid out in CSS rather than SVG. The chain is variable-length and its labels
 * are user data of unpredictable width, so text-driven layout is what keeps it
 * readable — an SVG would need measuring and re-flowing to do the same job, and
 * would still not wrap gracefully at 400px.
 *
 * The chain being data-driven is the whole point. A CPI-mediated order export
 * renders seven stages; a nightly CSV drop to an SFTP server renders three.
 * Neither is a special case, and neither has a middleware box invented for it.
 */
@Component({
  selector: 'ir-flow-diagram',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="diagram" [class.vertical]="orientation() === 'vertical'">
      <ol class="chain">
        @for (stage of stages(); track stage.id; let last = $last; let index = $index) {
          <li class="chain-item">
            <button
              type="button"
              class="stage"
              [class.selected]="stage.id === selectedStageId()"
              [class.optional]="stage.optional"
              [class]="stageClass(stage)"
              (click)="select(stage)"
              [attr.aria-pressed]="stage.id === selectedStageId()"
            >
              <span class="stage-top">
                <span class="stage-icon">
                  <ir-icon [name]="iconFor(stage.kind)" [size]="14" />
                </span>
                <span class="stage-index">{{ index + 1 }}</span>
                @if (stage.issueCount) {
                  <span class="stage-issues" [title]="stage.issueCount + ' known failure modes'">
                    <ir-icon name="alert-triangle" [size]="11" />
                    {{ stage.issueCount }}
                  </span>
                }
              </span>

              <span class="stage-label">{{ stage.label }}</span>

              @if (stage.detail) {
                <span class="stage-detail">{{ stage.detail }}</span>
              }

              @if (stage.badges?.length) {
                <span class="stage-badges">
                  @for (badge of stage.badges!.slice(0, 3); track badge) {
                    <span class="stage-badge">{{ badge }}</span>
                  }
                  @if (stage.badges!.length > 3) {
                    <span class="stage-badge muted">+{{ stage.badges!.length - 3 }}</span>
                  }
                </span>
              }

              @if (stage.optional) {
                <span class="stage-optional">optional</span>
              }
            </button>

            @if (!last) {
              <span class="connector" aria-hidden="true">
                <ir-icon [name]="orientation() === 'vertical' ? 'arrow-down' : 'arrow-right'" [size]="15" />
              </span>
            }
          </li>
        }
      </ol>

      @if (branches().length) {
        <div class="branches">
          <div class="section-title">Alternate paths</div>
          <ul class="branch-list">
            @for (branch of branches(); track branch.id) {
              <li class="branch">
                <ir-icon [name]="branchIcon(branch.kind)" [size]="13" [class]="'branch-icon ' + branchClass(branch.kind)" />
                <span class="branch-label">{{ branch.label }}</span>
                <span class="branch-route">
                  {{ labelFor(branch.fromStageId) }}
                  <ir-icon name="arrow-right" [size]="11" />
                  {{ labelFor(branch.toStageId) }}
                </span>
              </li>
            }
          </ul>
        </div>
      }
    </div>
  `,
  styles: [
    `
      :host { display: block; }

      .diagram { display: flex; flex-direction: column; gap: var(--space-5); }

      .chain {
        display: flex;
        flex-wrap: wrap;
        align-items: stretch;
        gap: var(--space-2);
      }

      .chain-item {
        display: flex;
        align-items: stretch;
        gap: var(--space-2);
        min-width: 0;
      }

      .stage {
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
        width: 168px;
        padding: var(--space-3);
        border: 1px solid var(--border);
        border-radius: var(--radius-md);
        background: var(--surface);
        text-align: left;
        transition: border-color var(--duration-fast) var(--ease-out),
                    box-shadow var(--duration-fast) var(--ease-out),
                    transform var(--duration-fast) var(--ease-out);
      }

      .stage:hover {
        border-color: var(--border-strong);
        box-shadow: var(--shadow-sm);
      }

      .stage.selected {
        border-color: var(--accent);
        box-shadow: 0 0 0 3px var(--accent-subtle-bg);
      }

      /* Optional stages are dashed — a landscape without middleware should
         *look* like one, not like a solid box that happens to say "optional". */
      .stage.optional {
        border-style: dashed;
        background: transparent;
      }

      .stage-top {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }

      .stage-icon {
        width: 22px;
        height: 22px;
        display: grid;
        place-items: center;
        border-radius: var(--radius-sm);
        background: var(--surface-sunken);
        color: var(--text-secondary);
        flex: 0 0 auto;
      }

      .tone-success .stage-icon { background: var(--success-bg); color: var(--success-fg); }
      .tone-warning .stage-icon { background: var(--warning-bg); color: var(--warning-fg); }
      .tone-danger .stage-icon  { background: var(--danger-bg);  color: var(--danger-fg); }
      .tone-info .stage-icon    { background: var(--info-bg);    color: var(--info-fg); }
      .tone-accent .stage-icon  { background: var(--accent-subtle-bg); color: var(--accent-subtle-fg); }
      .tone-ai .stage-icon      { background: var(--ai-bg);      color: var(--ai-fg); }

      .stage-index {
        font-size: var(--text-2xs);
        color: var(--text-muted);
        font-variant-numeric: tabular-nums;
      }

      .stage-issues {
        margin-left: auto;
        display: inline-flex;
        align-items: center;
        gap: 2px;
        font-size: var(--text-2xs);
        font-weight: var(--weight-semibold);
        color: var(--warning-fg);
      }

      .stage-label {
        font-size: var(--text-sm);
        font-weight: var(--weight-semibold);
        color: var(--text);
        line-height: 1.3;
      }

      .stage-detail {
        font-size: var(--text-2xs);
        color: var(--text-muted);
        line-height: 1.4;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .stage-badges {
        display: flex;
        flex-wrap: wrap;
        gap: 3px;
        margin-top: auto;
        padding-top: var(--space-1);
      }

      .stage-badge {
        padding: 0 5px;
        border-radius: var(--radius-xs);
        background: var(--surface-sunken);
        border: 1px solid var(--border-subtle);
        font-family: var(--font-mono);
        font-size: 9px;
        color: var(--text-secondary);
        line-height: 15px;
      }

      .stage-optional {
        font-size: 9px;
        text-transform: uppercase;
        letter-spacing: 0.06em;
        color: var(--text-muted);
      }

      .connector {
        display: grid;
        place-items: center;
        color: var(--border-strong);
        flex: 0 0 auto;
      }

      /* ---- branches ---- */
      .branch-list {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        margin-top: var(--space-2);
      }

      .branch {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        font-size: var(--text-sm);
        flex-wrap: wrap;
      }

      .branch-label { color: var(--text); font-weight: var(--weight-medium); }

      .branch-route {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        font-size: var(--text-xs);
        color: var(--text-muted);
      }

      .branch-error { color: var(--danger-fg); }
      .branch-retry { color: var(--warning-fg); }
      .branch-other { color: var(--info-fg); }

      /* ---- vertical ---- */
      .vertical .chain { flex-direction: column; }
      .vertical .chain-item { flex-direction: column; align-items: flex-start; }
      .vertical .stage { width: 100%; }
      .vertical .connector { padding-left: var(--space-5); }

      @media (max-width: 720px) {
        .chain { flex-direction: column; }
        .chain-item { flex-direction: column; align-items: stretch; }
        .stage { width: 100%; }
        .connector { padding-left: var(--space-5); justify-items: start; }
      }
    `,
  ],
})
export class FlowDiagramComponent {
  readonly stages = input<readonly DiagramStage[]>([]);
  readonly branches = input<readonly DiagramBranch[]>([]);
  readonly orientation = input<'horizontal' | 'vertical'>('horizontal');

  readonly selectedStageId = model<string | null>(null);
  readonly stageSelect = output<DiagramStage>();

  private readonly byId = computed(() => new Map(this.stages().map((stage) => [stage.id, stage])));

  protected select(stage: DiagramStage): void {
    // Clicking the selected stage clears it, so the detail panel can be closed
    // without hunting for an X.
    this.selectedStageId.set(this.selectedStageId() === stage.id ? null : stage.id);
    this.stageSelect.emit(stage);
  }

  protected labelFor(stageId: string): string {
    return this.byId().get(stageId)?.label ?? stageId;
  }

  protected stageClass(stage: DiagramStage): string {
    return stage.tone ? `stage tone-${stage.tone}` : 'stage';
  }

  /** Stage kind -> icon. Unknown kinds get a neutral box rather than nothing. */
  protected iconFor(kind: string): string {
    switch (kind) {
      case 'SOURCE': return 'box';
      case 'DOMAIN_MODEL': return 'database';
      case 'CONVERSION': return 'repeat';
      case 'DTO': return 'braces';
      case 'PAYLOAD_BUILD': return 'file-code';
      case 'TRANSPORT': return 'send';
      case 'MIDDLEWARE': return 'network';
      case 'TRANSFORMATION': return 'arrow-left-right';
      case 'VALIDATION': return 'check-circle';
      case 'ROUTING': return 'git-branch';
      case 'QUEUE': return 'layers';
      case 'PERSISTENCE': return 'database';
      case 'TARGET': return 'server';
      case 'ACKNOWLEDGEMENT': return 'check';
      default: return 'box';
    }
  }

  protected branchIcon(kind: string): string {
    switch (kind) {
      case 'RETRY': return 'repeat';
      case 'ERROR': return 'alert-triangle';
      case 'FALLBACK': return 'corner-down-right';
      default: return 'git-branch';
    }
  }

  protected branchClass(kind: string): string {
    switch (kind) {
      case 'ERROR': return 'branch-error';
      case 'RETRY': return 'branch-retry';
      default: return 'branch-other';
    }
  }
}
