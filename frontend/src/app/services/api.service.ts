import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { PullRequestDetail, PullRequestSummary } from '../models/pull-request';
import { BuildResponse, CreateReleaseRequest, NotifyResponse, ReleaseNotes, ReleaseView } from '../models/release';

/**
 * The only class in the frontend allowed to make HTTP calls, and it only ever
 * calls Spring Boot. Angular never reaches PostgreSQL, GitHub, or the Python
 * AI service directly — Spring Boot is the single orchestrator.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = 'http://localhost:8080';

  constructor(private readonly http: HttpClient) {}

  health(): Observable<{ status: string }> {
    return this.http.get<{ status: string }>(`${this.baseUrl}/actuator/health`);
  }

  listPullRequests(): Observable<PullRequestSummary[]> {
    return this.http.get<PullRequestSummary[]>(`${this.baseUrl}/api/v1/pull-requests`);
  }

  getPullRequest(prId: string): Observable<PullRequestDetail> {
    return this.http.get<PullRequestDetail>(`${this.baseUrl}/api/v1/pull-requests/${prId}`);
  }

  listReleases(): Observable<ReleaseView[]> {
    return this.http.get<ReleaseView[]>(`${this.baseUrl}/api/v1/releases`);
  }

  createRelease(request: CreateReleaseRequest): Observable<ReleaseView> {
    return this.http.post<ReleaseView>(`${this.baseUrl}/api/v1/releases`, request);
  }

  markReleaseDeployed(releaseId: string): Observable<ReleaseView> {
    return this.http.post<ReleaseView>(`${this.baseUrl}/api/v1/releases/${releaseId}/deploy`, {});
  }

  getRelease(releaseId: string): Observable<ReleaseView> {
    return this.http.get<ReleaseView>(`${this.baseUrl}/api/v1/releases/${releaseId}`);
  }

  buildRelease(releaseId: string): Observable<BuildResponse> {
    return this.http.post<BuildResponse>(`${this.baseUrl}/api/v1/releases/${releaseId}/build`, {});
  }

  getReleaseNotes(releaseId: string): Observable<ReleaseNotes> {
    return this.http.get<ReleaseNotes>(`${this.baseUrl}/api/v1/releases/${releaseId}/notes`);
  }

  approveRelease(releaseId: string): Observable<ReleaseView> {
    return this.http.post<ReleaseView>(`${this.baseUrl}/api/v1/releases/${releaseId}/approve`, {});
  }

  sendReleaseNotifications(releaseId: string): Observable<NotifyResponse> {
    return this.http.post<NotifyResponse>(`${this.baseUrl}/api/v1/releases/${releaseId}/notify`, {});
  }
}
