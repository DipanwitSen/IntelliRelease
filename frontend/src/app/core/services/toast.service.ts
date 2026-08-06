import { Injectable, signal } from '@angular/core';

import { Tone } from '../models/common';

export interface Toast {
  readonly id: number;
  readonly tone: Tone;
  readonly title: string;
  readonly detail?: string;
  /** 0 keeps the toast up until it is dismissed — used for failures. */
  readonly durationMs: number;
}

/**
 * Transient confirmations and failures.
 *
 * Errors default to a duration of 0 (sticky): a build that failed is worth
 * more than four seconds of a user's attention, and auto-dismissing it is how
 * people end up not knowing why an action did nothing.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;

  readonly toasts = signal<readonly Toast[]>([]);

  success(title: string, detail?: string): void {
    this.push('success', title, detail, 4000);
  }

  info(title: string, detail?: string): void {
    this.push('info', title, detail, 4000);
  }

  warning(title: string, detail?: string): void {
    this.push('warning', title, detail, 7000);
  }

  error(title: string, detail?: string): void {
    this.push('danger', title, detail, 0);
  }

  dismiss(id: number): void {
    this.toasts.update((current) => current.filter((toast) => toast.id !== id));
  }

  private push(tone: Tone, title: string, detail: string | undefined, durationMs: number): void {
    const toast: Toast = { id: this.nextId++, tone, title, detail, durationMs };
    this.toasts.update((current) => [...current, toast]);

    if (durationMs > 0) {
      setTimeout(() => this.dismiss(toast.id), durationMs);
    }
  }
}
