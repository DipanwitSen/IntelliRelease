import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ProvenanceClass, Tone } from '../../../core/models/common';
import { humanise, provenanceTone } from '../../tone';
import { IconComponent } from '../icon/icon.component';

/**
 * The one badge.
 *
 * Callers pass a tone rather than a domain value, and get the tone from
 * `shared/tone.ts`. That indirection is what stops "HIGH risk" and "HIGH
 * severity" drifting into different colours in different modules.
 */
@Component({
  selector: 'ir-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span
      [class]="'badge tone-' + tone() + (square() ? ' badge-square' : '') + (outlined() ? ' outlined' : '')"
      [title]="title() || ''"
    >
      @if (icon()) {
        <ir-icon [name]="icon()!" [size]="11" />
      }
      {{ display() }}
    </span>
  `,
  styles: [
    `
      :host { display: inline-flex; min-width: 0; }
      .outlined { box-shadow: inset 0 0 0 1px currentColor; }
    `,
  ],
})
export class BadgeComponent {
  readonly label = input<string | null | undefined>('');
  readonly tone = input<Tone>('neutral');
  readonly icon = input<string | null>(null);
  /** Rectangular, sentence-case variant for labels that are not statuses. */
  readonly square = input(false);
  /** Adds a ring — used for CRITICAL so it outranks HIGH at a glance. */
  readonly outlined = input(false);
  readonly title = input<string | null>(null);
  /** Set false when the label is already display-ready (a name, a version). */
  readonly humanize = input(true);

  protected readonly display = computed(() => {
    const label = this.label();
    if (label === null || label === undefined || label === '') {
      return '—';
    }
    return this.humanize() ? humanise(label) : label;
  });
}

/**
 * Architecture rule 10 rendered.
 *
 * Deliberately its own component rather than a `<ir-badge>` with a tone passed
 * in: provenance appears on hundreds of values, and giving it one component
 * means the explanatory tooltip is written once and is always right.
 */
@Component({
  selector: 'ir-provenance',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="provenance" [class]="'provenance provenance-' + cssKey()" [title]="explanation()">
      @if (showIcon()) {
        <ir-icon [name]="value() === 'AI_INFERENCE' ? 'sparkles' : 'shield'" [size]="10" />
      }
      {{ short() }}
    </span>
  `,
  styles: [`:host { display: inline-flex; }`],
})
export class ProvenanceBadgeComponent {
  readonly value = input<ProvenanceClass | string | null | undefined>('UNKNOWN');
  readonly showIcon = input(true);

  protected readonly cssKey = computed(() => (this.value() ?? 'unknown').toString().toLowerCase());

  protected readonly short = computed(() => {
    switch (this.value()) {
      case 'FACT': return 'Fact';
      case 'DERIVED_FACT': return 'Derived';
      case 'RULE_OUTPUT': return 'Rule';
      case 'AI_INFERENCE': return 'AI';
      default: return 'Unknown';
    }
  });

  protected readonly explanation = computed(() => {
    switch (this.value()) {
      case 'FACT':
        return 'FACT — captured directly from a source system. Not computed, not inferred.';
      case 'DERIVED_FACT':
        return 'DERIVED_FACT — computed deterministically from captured facts. Reproducible from the same inputs.';
      case 'RULE_OUTPUT':
        return 'RULE_OUTPUT — produced by a versioned rule engine. The rule that fired is recorded alongside it.';
      case 'AI_INFERENCE':
        return 'AI_INFERENCE — written by a language model to explain deterministic findings. It did not decide anything; verify before acting on it.';
      default:
        return 'UNKNOWN — no rule matched this input. Downstream engines treat it conservatively rather than guessing.';
    }
  });

  /** Exposed so tone-driven callers can reuse the same mapping. */
  protected readonly tone = computed(() => provenanceTone(this.value()));
}
