import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { EmptyStateComponent } from '../../shared/components/states/states.component';

@Component({
  selector: 'ir-not-found',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, RouterLink],
  template: `
    <div class="page">
      <div class="card">
        <ir-empty-state
          icon="file-search"
          title="That page does not exist"
          body="The link may be out of date, or the module may have been renamed. Everything the platform offers is in the left navigation."
        >
          <a routerLink="/dashboard" class="btn btn-primary btn-sm">Back to the dashboard</a>
        </ir-empty-state>
      </div>
    </div>
  `,
})
export class NotFoundPage {}
