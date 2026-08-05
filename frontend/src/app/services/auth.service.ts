import { Injectable, computed, signal } from '@angular/core';

interface Credentials {
  username: string;
  password: string;
}

/**
 * Holds the operator's Basic Auth credentials in memory only (not persisted
 * across reloads) and turns them into the Authorization header the
 * interceptor attaches to every backend request. The backend — not this
 * service — is what actually decides whether the credentials are valid.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly credentials = signal<Credentials | null>(null);

  readonly isAuthenticated = computed(() => this.credentials() !== null);
  readonly username = computed(() => this.credentials()?.username ?? null);

  login(username: string, password: string): void {
    this.credentials.set({ username, password });
  }

  logout(): void {
    this.credentials.set(null);
  }

  authHeader(): string | null {
    const current = this.credentials();
    return current ? 'Basic ' + btoa(`${current.username}:${current.password}`) : null;
  }
}
