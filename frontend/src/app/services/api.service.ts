import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { PullRequestDetail, PullRequestSummary } from '../models/pull-request';

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
}
