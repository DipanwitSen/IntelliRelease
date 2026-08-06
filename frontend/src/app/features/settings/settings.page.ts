import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';

import { PlatformSettings } from '../../core/models/delivery';
import { ApiService } from '../../core/services/api.service';
import { RequestState } from '../../core/services/request-state';
import { ThemeService } from '../../core/services/theme.service';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { ErrorPanelComponent, SkeletonComponent } from '../../shared/components/states/states.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { healthTone, humanise } from '../../shared/tone';

/**
 * Landscape, AI, notifications and governance.
 *
 * The Integration section is the one that matters most: it is where a customer
 * declares that they have no middleware, or that they exchange everything as
 * nightly CSV. Every other module reads those switches, so a landscape
 * declared here stops the rest of the product describing a system the customer
 * does not run.
 */
@Component({
  selector: 'ir-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent, SectionCardComponent, BadgeComponent, IconComponent,
    SkeletonComponent, ErrorPanelComponent, RelativeTimePipe,
  ],
  template: `
    <div class="page">
      <ir-page-header
        title="Settings"
        subtitle="How this tenant's landscape is shaped, and what the platform is allowed to do on its behalf."
        icon="settings"
      />

      <ir-section-card title="Appearance" icon="sun">
        <div class="row-2 row-wrap">
          <span class="text-md secondary">Theme</span>
          <div class="btn-group" role="group" aria-label="Theme">
            @for (option of themeOptions; track option.value) {
              <button
                type="button"
                class="btn btn-sm"
                [attr.aria-pressed]="theme.preference() === option.value"
                (click)="theme.set(option.value)"
              >
                <ir-icon [name]="option.icon" [size]="14" />
                {{ option.label }}
              </button>
            }
          </div>
          <span class="text-xs muted">Currently rendering in {{ theme.resolved() }} mode.</span>
        </div>
      </ir-section-card>

      @if (state.showSkeleton()) {
        <div class="card"><ir-skeleton [rows]="8" /></div>
      } @else if (state.error()) {
        <ir-error-panel [message]="state.error()!" (retry)="reload()" />
      }

      <!-- A standalone block rather than another else-branch: the "as" alias is
           only supported on a primary if-block, never on an else-if. -->
      @if (loaded(); as settings) {
        <ir-section-card
          title="Integration landscape"
          subtitle="What this customer actually runs. Declaring it here keeps every other module honest."
          icon="network"
        >
          <div class="def-grid">
            <div>
              <div class="def-label">Middleware</div>
              <div class="def-value">
                @if (settings.integration.middlewareEnabled) {
                  {{ settings.integration.middlewareName ?? 'Enabled' }}
                } @else {
                  <span class="muted">None — commerce talks to the target system directly</span>
                }
              </div>
            </div>
            <div>
              <div class="def-label">Target system</div>
              <div class="def-value">{{ settings.integration.targetSystemName }}</div>
            </div>
            <div>
              <div class="def-label">Topologies in use</div>
              <div class="chip-row">
                @for (topology of settings.integration.enabledTopologies; track topology) {
                  <span class="chip">{{ humanise(topology) }}</span>
                }
              </div>
            </div>
            <div>
              <div class="def-label">Protocols</div>
              <div class="chip-row">
                @for (protocol of settings.integration.enabledProtocols; track protocol) {
                  <span class="chip chip-mono">{{ protocol }}</span>
                }
              </div>
            </div>
            <div>
              <div class="def-label">Payload formats</div>
              <div class="chip-row">
                @for (format of settings.integration.enabledFormats; track format) {
                  <span class="chip chip-mono">{{ format }}</span>
                }
              </div>
            </div>
          </div>
        </ir-section-card>

        <div class="grid grid-2">
          <ir-section-card title="AI" icon="sparkles">
            <div class="def-grid">
              <div>
                <div class="def-label">Enabled</div>
                <div class="def-value">
                  <ir-badge [label]="settings.ai.enabled ? 'Enabled' : 'Disabled'" [tone]="settings.ai.enabled ? 'ai' : 'neutral'" [humanize]="false" />
                </div>
              </div>
              <div>
                <div class="def-label">Provider / model</div>
                <div class="def-value def-value-mono">{{ settings.ai.provider ?? '—' }} / {{ settings.ai.model ?? '—' }}</div>
              </div>
              <div>
                <div class="def-label">Deterministic fallback</div>
                <div class="def-value">{{ settings.ai.deterministicFallback ? 'On' : 'Off' }}</div>
              </div>
              <div>
                <div class="def-label">Secret redaction</div>
                <div class="def-value">{{ settings.ai.redactSecrets ? 'On' : 'Off' }}</div>
              </div>
            </div>
            <p class="text-xs muted" style="margin-top: var(--space-3)">
              AI never scores a change and never approves a release. Turning it off removes the prose, not the analysis.
            </p>
          </ir-section-card>

          <ir-section-card title="Governance" icon="shield">
            <div class="def-grid">
              <div>
                <div class="def-label">Approval required</div>
                <div class="def-value">{{ settings.governance.approvalRequired ? 'Yes' : 'No' }}</div>
              </div>
              <div>
                <div class="def-label">Risk policy</div>
                <div class="def-value def-value-mono">{{ settings.governance.riskPolicyVersion ?? '—' }}</div>
              </div>
              <div>
                <div class="def-label">Block on critical risk</div>
                <div class="def-value">{{ settings.governance.blockOnCriticalRisk ? 'Yes' : 'No' }}</div>
              </div>
              <div>
                <div class="def-label">Require tests for high risk</div>
                <div class="def-value">{{ settings.governance.requireTestsForHighRisk ? 'Yes' : 'No' }}</div>
              </div>
            </div>
          </ir-section-card>
        </div>

        <ir-section-card title="Connections" icon="plug" [count]="settings.connections.length">
          <div class="stack-2">
            @for (connection of settings.connections; track connection.key) {
              <div class="connection-row">
                <span class="dot" [class]="'dot fg-' + healthTone(connection.status)"></span>
                <span class="weight-medium">{{ connection.label }}</span>
                <ir-badge [label]="connection.status" [tone]="healthTone(connection.status)" />
                @if (connection.detail) {
                  <span class="text-xs muted truncate">{{ connection.detail }}</span>
                }
                <span class="spacer"></span>
                <span class="text-xs muted">{{ connection.lastCheckedAt | relativeTime }}</span>
              </div>
            }
          </div>
        </ir-section-card>

        <ir-section-card title="Notifications" icon="send">
          <div class="stack-2">
            @for (audience of settings.notifications.audiences; track audience.audience) {
              <div class="connection-row">
                <ir-badge [label]="audience.audience" tone="accent" />
                <span class="text-sm secondary truncate">{{ audience.recipients.join(', ') || 'No recipients configured' }}</span>
                <span class="spacer"></span>
                <ir-badge [label]="audience.enabled ? 'On' : 'Off'" [tone]="audience.enabled ? 'success' : 'neutral'" [humanize]="false" />
              </div>
            }
          </div>
        </ir-section-card>
      }
    </div>
  `,
  styles: [
    `
      .connection-row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        flex-wrap: wrap;
        padding: var(--space-2) var(--space-3);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-sm);
      }
    `,
  ],
})
export class SettingsPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  protected readonly theme = inject(ThemeService);

  protected readonly state = new RequestState<PlatformSettings>();

  /** Settings once they are actually here — null while loading or on failure. */
  protected readonly loaded = this.state.data;

  protected readonly healthTone = healthTone;
  protected readonly humanise = humanise;

  protected readonly themeOptions = [
    { value: 'light' as const, label: 'Light', icon: 'sun' },
    { value: 'dark' as const, label: 'Dark', icon: 'moon' },
    { value: 'system' as const, label: 'System', icon: 'monitor' },
  ];

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.state.destroy();
  }

  protected reload(): void {
    this.state.load(this.api.getSettings());
  }
}
