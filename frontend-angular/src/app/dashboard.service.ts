import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { finalize } from 'rxjs';
import { GithubWebhookRequest, ReleaseNarrative, ReleaseSummary } from './models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly apiBaseUrl = 'http://localhost:8080/api';

  readonly releases = signal<ReleaseSummary[]>([]);
  readonly narrative = signal<ReleaseNarrative | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor(private readonly http: HttpClient) {}

  loadReleases(): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<ReleaseSummary[]>(`${this.apiBaseUrl}/releases`).pipe(
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (data) => {
        this.releases.set(data);
        const latest = data[0];
        if (latest) {
          this.loadNarrative(latest.releaseId);
        }
      },
      error: () => this.error.set('Unable to load releases from the backend.'),
    });
  }

  loadNarrative(releaseId: string): void {
    this.http.get<ReleaseNarrative>(`${this.apiBaseUrl}/releases/${releaseId}/narrative`).subscribe({
      next: (data) => this.narrative.set(data),
      error: () => this.narrative.set(null),
    });
  }

  seedDemoRelease(): void {
    const payload: GithubWebhookRequest = {
      repository: 'sap-commerce-storefront',
      branch: 'main',
      mergeSha: 'demo-sha-001',
      prNumber: '451',
      author: 'Rahul',
      title: 'Checkout validation and CMS tweaks',
      changedFiles: [
        { path: 'core/src/com/company/checkout/DefaultCheckoutFacade.java', changeType: 'MODIFIED' },
        { path: 'core/resources/items.xml', changeType: 'MODIFIED' },
        { path: 'resources/homepage.impex', changeType: 'MODIFIED' },
        { path: 'resources/solr.impex', changeType: 'MODIFIED' },
      ],
    };

    this.http.post<ReleaseSummary>(`${this.apiBaseUrl}/webhooks/github`, payload).subscribe({
      next: () => this.loadReleases(),
      error: () => this.error.set('Demo release could not be created.'),
    });
  }
}