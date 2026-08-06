import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { PullRequestDetail, PullRequestSummary } from '../../models/pull-request';
import { CreateReleaseRequest, NotifyResponse, ReleaseNotes, ReleaseView } from '../../models/release';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  pullRequests: PullRequestSummary[] = [];
  selected: PullRequestDetail | null = null;
  loading = true;
  detailLoading = false;
  error: string | null = null;

  releases: ReleaseView[] = [];
  releasesLoading = true;
  releaseError: string | null = null;
  showReleaseForm = false;
  newRelease: CreateReleaseRequest = { repoName: '', version: '', fromRef: '', toRef: 'main' };
  creatingRelease = false;
  deployingReleaseId: string | null = null;

  selectedRelease: ReleaseView | null = null;
  releaseDetailLoading = false;
  building = false;
  buildNote: string | null = null;
  notes: ReleaseNotes | null = null;
  loadingNotes = false;
  approving = false;
  sendingNotifications = false;
  notifyResult: NotifyResponse | null = null;

  constructor(
    private readonly api: ApiService,
    readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.refresh();
    this.refreshReleases();
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

  refreshReleases(): void {
    this.releasesLoading = true;
    this.releaseError = null;
    this.api.listReleases().subscribe({
      next: (releases) => {
        this.releases = releases;
        this.releasesLoading = false;
      },
      error: () => {
        this.releasesLoading = false;
        this.releaseError = 'Could not load releases.';
      },
    });
  }

  createRelease(): void {
    this.creatingRelease = true;
    this.releaseError = null;
    this.api.createRelease(this.newRelease).subscribe({
      next: () => {
        this.creatingRelease = false;
        this.showReleaseForm = false;
        this.newRelease = { repoName: '', version: '', fromRef: '', toRef: 'main' };
        this.refreshReleases();
      },
      error: (err) => {
        this.creatingRelease = false;
        this.releaseError = err?.error?.message ?? 'Could not create release.';
      },
    });
  }

  deployRelease(release: ReleaseView): void {
    this.deployingReleaseId = release.releaseId;
    this.api.markReleaseDeployed(release.releaseId).subscribe({
      next: (updated) => {
        this.deployingReleaseId = null;
        this.refreshReleases();
        if (this.selectedRelease?.releaseId === updated.releaseId) {
          this.selectedRelease = updated;
        }
      },
      error: () => {
        this.deployingReleaseId = null;
        this.releaseError = 'Could not confirm deployment.';
      },
    });
  }

  openRelease(release: ReleaseView): void {
    this.releaseDetailLoading = true;
    this.selectedRelease = null;
    this.buildNote = null;
    this.notes = null;
    this.notifyResult = null;
    this.api.getRelease(release.releaseId).subscribe({
      next: (detail) => {
        this.selectedRelease = detail;
        this.releaseDetailLoading = false;
      },
      error: () => {
        this.releaseDetailLoading = false;
        this.releaseError = 'Could not load release detail.';
      },
    });
  }

  closeReleaseDetail(): void {
    this.selectedRelease = null;
    this.notes = null;
    this.buildNote = null;
    this.notifyResult = null;
  }

  buildRelease(): void {
    if (!this.selectedRelease) {
      return;
    }
    this.building = true;
    this.buildNote = null;
    this.api.buildRelease(this.selectedRelease.releaseId).subscribe({
      next: (result) => {
        this.building = false;
        this.selectedRelease = result.release;
        this.buildNote = `${result.commitsExamined} commit(s) examined from Git`
          + (result.unmatchedPrNumbers.length
            ? `. PR(s) ${result.unmatchedPrNumbers.join(', ')} resolved from Git but not yet captured via webhook.`
            : '.');
        this.refreshReleases();
      },
      error: () => {
        this.building = false;
        this.buildNote = 'Could not build release from Git.';
      },
    });
  }

  loadNotes(): void {
    if (!this.selectedRelease) {
      return;
    }
    this.loadingNotes = true;
    this.api.getReleaseNotes(this.selectedRelease.releaseId).subscribe({
      next: (notes) => {
        this.notes = notes;
        this.loadingNotes = false;
      },
      error: () => {
        this.loadingNotes = false;
        this.releaseError = 'Could not generate release notes.';
      },
    });
  }

  approveRelease(): void {
    if (!this.selectedRelease) {
      return;
    }
    this.approving = true;
    this.api.approveRelease(this.selectedRelease.releaseId).subscribe({
      next: (updated) => {
        this.approving = false;
        this.selectedRelease = { ...updated, pullRequests: this.selectedRelease?.pullRequests ?? null,
          excludedPrs: this.selectedRelease?.excludedPrs ?? null };
        this.refreshReleases();
      },
      error: () => {
        this.approving = false;
        this.releaseError = 'Could not approve release.';
      },
    });
  }

  sendNotifications(): void {
    if (!this.selectedRelease) {
      return;
    }
    this.sendingNotifications = true;
    this.notifyResult = null;
    this.api.sendReleaseNotifications(this.selectedRelease.releaseId).subscribe({
      next: (result) => {
        this.sendingNotifications = false;
        this.notifyResult = result;
      },
      error: (err) => {
        this.sendingNotifications = false;
        this.releaseError = err?.error?.code === 'APPROVAL_REQUIRED'
          ? 'This release must be approved before notifications can be sent.'
          : 'Could not send release notifications.';
      },
    });
  }

  logout(): void {
    this.auth.logout();
  }
}
