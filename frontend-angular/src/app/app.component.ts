import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { DashboardService } from './dashboard.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  template: `
    <main class="shell">
      <section class="hero">
        <div>
          <p class="eyebrow">IntelliRelease Phase 1</p>
          <h1>AI-powered SAP Commerce release intelligence</h1>
          <p class="lede">
            One dashboard for QA, developers, release managers, and executives.
            GitHub merges become structured release intelligence, not spreadsheet noise.
          </p>
        </div>

        <div class="hero-actions">
          <button type="button" (click)="service.seedDemoRelease()">Seed demo release</button>
          <button type="button" class="secondary" (click)="service.loadReleases()">Refresh dashboard</button>
        </div>
      </section>

      <section class="stats-grid">
        <article class="stat-card">
          <span>Risk Score</span>
          <strong>{{ latest()?.riskScore ?? 0 }}/100</strong>
          <small>{{ latest()?.riskLevel ?? 'LOW' }}</small>
        </article>
        <article class="stat-card">
          <span>Readiness</span>
          <strong>{{ latest()?.readinessScore ?? 0 }}/100</strong>
          <small>{{ latest()?.readinessStatus ?? 'No release yet' }}</small>
        </article>
        <article class="stat-card">
          <span>Impacts</span>
          <strong>{{ latest()?.impactedCapabilities?.length ?? 0 }}</strong>
          <small>{{ latest()?.impactedCapabilities?.join(', ') || 'Waiting for webhook' }}</small>
        </article>
        <article class="stat-card">
          <span>Suggested Tests</span>
          <strong>{{ latest()?.recommendedTests?.length ?? 0 }}</strong>
          <small>{{ latest()?.recommendedTests?.slice(0, 2).join(', ') || 'No guidance yet' }}</small>
        </article>
      </section>

      <section class="content-grid">
        <article class="panel focus">
          <h2>Release Timeline</h2>
          <ng-container *ngIf="service.loading(); else releaseList">
            <p>Loading releases...</p>
          </ng-container>

          <ng-template #releaseList>
            <div *ngIf="service.error()" class="error">{{ service.error() }}</div>
            <div class="release-list">
              <article class="release-card" *ngFor="let release of service.releases(); trackBy: trackByReleaseId">
                <div class="release-header">
                  <div>
                    <h3>{{ release.releaseId }} · PR #{{ release.prNumber }}</h3>
                    <p>{{ release.title }}</p>
                  </div>
                  <span class="pill">{{ release.readinessStatus }}</span>
                </div>
                <dl>
                  <div>
                    <dt>Repository</dt>
                    <dd>{{ release.repository }}</dd>
                  </div>
                  <div>
                    <dt>Branch</dt>
                    <dd>{{ release.branch }}</dd>
                  </div>
                  <div>
                    <dt>Risk</dt>
                    <dd>{{ release.riskScore }}/100</dd>
                  </div>
                  <div>
                    <dt>Readiness</dt>
                    <dd>{{ release.readinessScore }}/100</dd>
                  </div>
                </dl>
                <p class="summary">{{ release.businessSummary }}</p>
              </article>
            </div>
          </ng-template>
        </article>

        <aside class="panel insight">
          <h2>Execution Guidance</h2>
          <p>
            This screen is designed to replace spreadsheet-based release notes with a live operational view.
            QA sees what to test, developers see what changed, and leadership sees readiness.
          </p>

          <section class="narrative" *ngIf="service.narrative() as narrative">
            <h3>AI Narrative</h3>
            <p><strong>Executive:</strong> {{ narrative.executiveSummary }}</p>
            <p><strong>Business:</strong> {{ narrative.businessSummary }}</p>
            <p><strong>QA:</strong> {{ narrative.qaGuidance }}</p>
          </section>

          <div class="tag-list">
            <span>GitHub Webhooks</span>
            <span>SAP Commerce Context</span>
            <span>Deterministic Risk</span>
            <span>AI Explanations</span>
            <span>Teams / Email</span>
            <span>Audit Trail</span>
          </div>
        </aside>
      </section>
    </main>
  `,
  styles: [`
    .shell {
      min-height: 100vh;
      padding: 32px;
      max-width: 1440px;
      margin: 0 auto;
    }

    .hero {
      display: flex;
      justify-content: space-between;
      gap: 24px;
      align-items: end;
      padding: 28px;
      border: 1px solid var(--border);
      border-radius: 28px;
      background: linear-gradient(135deg, rgba(15, 23, 42, 0.92), rgba(12, 18, 32, 0.72));
      box-shadow: 0 24px 80px rgba(2, 6, 23, 0.35);
      backdrop-filter: blur(20px);
    }

    .eyebrow {
      margin: 0 0 8px;
      text-transform: uppercase;
      letter-spacing: 0.22em;
      color: var(--accent);
      font-size: 0.75rem;
    }

    h1 {
      margin: 0;
      font-size: clamp(2.4rem, 5vw, 4.8rem);
      line-height: 0.95;
      max-width: 11ch;
    }

    .lede {
      max-width: 62ch;
      color: var(--muted);
      margin: 16px 0 0;
      font-size: 1.05rem;
    }

    .hero-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }

    button {
      border: 0;
      border-radius: 999px;
      padding: 14px 20px;
      font-weight: 700;
      background: linear-gradient(135deg, var(--accent), #34d399);
      color: #05201c;
      cursor: pointer;
    }

    button.secondary {
      background: var(--panel-strong);
      color: var(--text);
      border: 1px solid var(--border);
    }

    .stats-grid,
    .content-grid {
      display: grid;
      grid-template-columns: repeat(12, minmax(0, 1fr));
      gap: 18px;
      margin-top: 18px;
    }

    .stat-card,
    .panel {
      border: 1px solid var(--border);
      border-radius: 24px;
      background: var(--panel);
      backdrop-filter: blur(20px);
      box-shadow: 0 20px 50px rgba(2, 6, 23, 0.22);
    }

    .stat-card {
      grid-column: span 3;
      padding: 18px;
      display: grid;
      gap: 8px;
    }

    .stat-card span,
    dt {
      color: var(--muted);
      font-size: 0.86rem;
    }

    .stat-card strong {
      font-size: clamp(1.6rem, 3vw, 2.2rem);
    }

    .content-grid .focus {
      grid-column: span 8;
      padding: 22px;
    }

    .content-grid .insight {
      grid-column: span 4;
      padding: 22px;
      background: linear-gradient(180deg, rgba(17, 27, 49, 0.95), rgba(10, 17, 32, 0.95));
    }

    .release-list {
      display: grid;
      gap: 14px;
      margin-top: 16px;
    }

    .release-card {
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 18px;
      background: rgba(15, 23, 42, 0.72);
    }

    .release-header {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      align-items: start;
    }

    .release-header h3,
    .panel h2 {
      margin: 0;
    }

    .release-header p,
    .panel p,
    .summary,
    dd {
      color: var(--muted);
    }

    .pill {
      padding: 8px 12px;
      border-radius: 999px;
      background: rgba(94, 234, 212, 0.12);
      color: var(--accent);
      border: 1px solid rgba(94, 234, 212, 0.2);
      font-size: 0.82rem;
    }

    dl {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 12px;
      margin: 16px 0;
    }

    dd {
      margin: 4px 0 0;
      font-weight: 700;
      color: var(--text);
    }

    .tag-list {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 18px;
    }

    .narrative {
      margin-top: 20px;
      padding: 16px;
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid var(--border);
    }

    .narrative h3 {
      margin: 0 0 10px;
      font-size: 1rem;
    }

    .narrative p {
      margin: 0 0 10px;
    }

    .tag-list span {
      border-radius: 999px;
      padding: 10px 12px;
      background: rgba(248, 250, 252, 0.05);
      border: 1px solid var(--border);
      color: var(--text);
    }

    .error {
      padding: 12px 14px;
      border-radius: 14px;
      background: rgba(248, 113, 113, 0.12);
      border: 1px solid rgba(248, 113, 113, 0.24);
      margin-top: 14px;
    }

    @media (max-width: 1100px) {
      .hero,
      .content-grid {
        grid-template-columns: 1fr;
      }

      .stat-card,
      .content-grid .focus,
      .content-grid .insight {
        grid-column: auto;
      }

      dl {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }

    @media (max-width: 720px) {
      .shell {
        padding: 16px;
      }

      .hero {
        padding: 22px;
      }

      .stats-grid {
        grid-template-columns: 1fr 1fr;
      }

      .stat-card {
        grid-column: span 1;
      }

      dl {
        grid-template-columns: 1fr;
      }
    }
  `],
})
export class AppComponent implements OnInit {
  constructor(public readonly service: DashboardService) {}

  ngOnInit(): void {
    this.service.loadReleases();
  }

  latest() {
    return this.service.releases()[0] ?? null;
  }

  trackByReleaseId(_: number, item: { releaseId: string }) {
    return item.releaseId;
  }
}