import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Tone } from '../../../core/models/common';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';
import { IconComponent } from '../icon/icon.component';

export interface TimelineItem {
  readonly id: string;
  readonly title: string;
  readonly detail?: string;
  readonly timestamp: string;
  readonly tone: Tone;
  readonly icon?: string;
  readonly meta?: string;
  /** Mutable array: Angular's `routerLink` input does not accept `readonly`. */
  readonly routerLink?: string[];
}

/**
 * A vertical event rail — deployments, activity, release history, audit.
 *
 * The connecting line is drawn with a `::before` on each item rather than as a
 * separate absolutely-positioned element, so it can never fall out of sync
 * with the markers when items are added, filtered or virtualised away.
 */
@Component({
  selector: 'ir-timeline',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RouterLink, RelativeTimePipe],
  template: `
    <ol class="timeline">
      @for (item of items(); track item.id; let last = $last) {
        <li class="timeline-item" [class.is-last]="last">
          <span class="marker" [class]="'marker tone-' + item.tone">
            <ir-icon [name]="item.icon || 'clock'" [size]="12" />
          </span>

          <div class="content">
            <div class="headline">
              @if (item.routerLink) {
                <a [routerLink]="item.routerLink" class="title">{{ item.title }}</a>
              } @else {
                <span class="title">{{ item.title }}</span>
              }
              <time class="stamp" [attr.datetime]="item.timestamp" [title]="item.timestamp">
                {{ item.timestamp | relativeTime }}
              </time>
            </div>

            @if (item.detail) {
              <p class="detail">{{ item.detail }}</p>
            }
            @if (item.meta) {
              <p class="meta">{{ item.meta }}</p>
            }
          </div>
        </li>
      }
    </ol>
  `,
  styles: [
    `
      :host { display: block; }

      .timeline { display: flex; flex-direction: column; }

      .timeline-item {
        position: relative;
        display: flex;
        gap: var(--space-3);
        padding-bottom: var(--space-5);
      }

      .timeline-item.is-last { padding-bottom: 0; }

      /* The rail: starts below the marker, stops at the next item. */
      .timeline-item:not(.is-last)::before {
        content: '';
        position: absolute;
        left: 11px;
        top: 24px;
        bottom: 0;
        width: 1px;
        background: var(--border);
      }

      .marker {
        position: relative;
        z-index: 1;
        width: 23px;
        height: 23px;
        flex: 0 0 auto;
        display: grid;
        place-items: center;
        border-radius: 50%;
        border: 2px solid var(--surface);
      }

      .content { flex: 1; min-width: 0; padding-top: 1px; }

      .headline {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: var(--space-3);
      }

      .title {
        font-size: var(--text-md);
        font-weight: var(--weight-medium);
        color: var(--text);
        min-width: 0;
      }

      a.title:hover { color: var(--text-link); }

      .stamp {
        flex: 0 0 auto;
        font-size: var(--text-xs);
        color: var(--text-muted);
        white-space: nowrap;
      }

      .detail {
        margin-top: 2px;
        font-size: var(--text-sm);
        color: var(--text-secondary);
        overflow-wrap: anywhere;
      }

      .meta {
        margin-top: var(--space-1);
        font-size: var(--text-xs);
        color: var(--text-muted);
      }
    `,
  ],
})
export class TimelineComponent {
  readonly items = input<readonly TimelineItem[]>([]);
  readonly itemClick = output<TimelineItem>();
}
