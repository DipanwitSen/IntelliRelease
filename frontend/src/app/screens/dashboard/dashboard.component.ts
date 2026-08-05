import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';

import { PullRequestDetail, PullRequestSummary } from '../../models/pull-request';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  pullRequests: PullRequestSummary[] = [];
  selected: PullRequestDetail | null = null;
  loading = true;
  detailLoading = false;
  error: string | null = null;

  constructor(
    private readonly api: ApiService,
    readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.error = null;
    this.api.listPullRequests().subscribe({
      next: (prs) => {
        this.pullRequests = prs;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.error = 'Could not load pull requests.';
      },
    });
  }

  select(pr: PullRequestSummary): void {
    this.detailLoading = true;
    this.selected = null;
    this.api.getPullRequest(pr.prId).subscribe({
      next: (detail) => {
        this.selected = detail;
        this.detailLoading = false;
      },
      error: () => {
        this.detailLoading = false;
        this.error = 'Could not load pull request detail.';
      },
    });
  }

  closeDetail(): void {
    this.selected = null;
  }

  logout(): void {
    this.auth.logout();
  }
}
