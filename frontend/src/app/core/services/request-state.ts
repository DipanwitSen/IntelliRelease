import { Signal, WritableSignal, computed, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';

/**
 * The three states every remote read can be in, held as signals.
 *
 * Every page in the product uses this instead of hand-rolling
 * `loading`/`error`/`data` triples, which is what makes loading skeletons and
 * error panels look and behave identically everywhere. It also cancels the
 * previous request when a new one starts, so rapid filter changes cannot land
 * out of order and paint stale results.
 */
export class RequestState<T> {
  private readonly _data: WritableSignal<T | null> = signal<T | null>(null);
  private readonly _error = signal<string | null>(null);
  private readonly _loading = signal(false);
  /** True only for the very first load, so refreshes don't flash a skeleton. */
  private readonly _initialised = signal(false);

  private subscription?: Subscription;

  readonly data: Signal<T | null> = this._data.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();

  /** Show a skeleton: loading, and nothing has ever been rendered. */
  readonly showSkeleton = computed(() => this._loading() && !this._initialised());

  /** Show a subtle inline spinner: reloading over content that is already up. */
  readonly refreshing = computed(() => this._loading() && this._initialised());

  readonly hasData = computed(() => this._data() !== null);

  constructor(private readonly errorMapper: (error: unknown) => string = defaultErrorMessage) {}

  /** Runs `source`, cancelling any request already in flight. */
  load(source: Observable<T>): void {
    this.subscription?.unsubscribe();
    this._loading.set(true);
    this._error.set(null);

    this.subscription = source.subscribe({
      next: (value) => {
        this._data.set(value);
        this._loading.set(false);
        this._initialised.set(true);
      },
      error: (error: unknown) => {
        this._error.set(this.errorMapper(error));
        this._loading.set(false);
        this._initialised.set(true);
      },
    });
  }

  /** Replaces the held value without a request — used after a local mutation. */
  set(value: T): void {
    this._data.set(value);
    this._error.set(null);
    this._initialised.set(true);
  }

  reset(): void {
    this.subscription?.unsubscribe();
    this._data.set(null);
    this._error.set(null);
    this._loading.set(false);
    this._initialised.set(false);
  }

  destroy(): void {
    this.subscription?.unsubscribe();
  }
}

/**
 * Turns an HttpErrorResponse into something worth showing a user.
 *
 * Prefers the backend's own `message` from `ApiExceptionHandler`, because that
 * text was written for humans; falls back to status-specific wording rather
 * than the browser's default, which tends to say "Http failure response" and
 * leave the reader none the wiser.
 */
export function defaultErrorMessage(error: unknown): string {
  const response = error as { status?: number; error?: { message?: string; code?: string }; message?: string };

  if (response?.error?.message) {
    return response.error.message;
  }
  switch (response?.status) {
    case 0:
      return 'Cannot reach the IntelliRelease backend. Check that it is running and that CORS allows this origin.';
    case 401:
      return 'Your session is not authenticated. Sign in again to continue.';
    case 403:
      return 'You do not have permission to view this.';
    case 404:
      return 'Not found. It may have been removed, or never captured.';
    case 409:
      return response.error?.code === 'APPROVAL_REQUIRED'
        ? 'This release has not been approved yet. Approval is enforced by the backend, not the UI.'
        : 'That conflicts with the current state. Refresh and try again.';
    case 503:
      return 'A dependency is unavailable. The platform falls back to deterministic output where it can.';
    default:
      return response?.message ?? 'Something went wrong loading this data.';
  }
}
