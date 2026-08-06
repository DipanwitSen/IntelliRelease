import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DashboardSnapshot } from '../models/dashboard';
import {
  AuditEntry, BuildResult, CreateReleaseRequest, DeploymentEvent, NotifyResult,
  PlatformSettings, PullRequestDetail, PullRequestSummary, ReleaseDetail,
  ReleaseNotes, ReleaseSummary, RepositoryDetail, RepositorySummary,
} from '../models/delivery';
import {
  ApiDefinition, FlowDefinition, IntegrationHealthSnapshot, IntegrationInterface,
  IntegrationOverview, MappingComparison, MappingLink, MappingSet,
  PayloadComparison, PayloadContent, PayloadDocument,
} from '../models/integration';
import {
  AiServiceStatus, AssistantMessage, AssistantRequest, AssistantSuggestion,
  ContextPackage, ErrorExplanation, ErrorOccurrence, ErrorPattern,
  GlossaryTerm, KnowledgeArticle, ParsedInput,
} from '../models/intelligence';
import { Page } from '../models/common';
import { API_BASE_URL } from '../api-base-url';

/**
 * The only class in the frontend permitted to make HTTP calls, and it only
 * ever calls Spring Boot.
 *
 * This is architecture rules 1–5 expressed as code: Angular never reaches
 * PostgreSQL, never calls GitHub for business processing, and never calls the
 * Python AI service. Every method below points at `/api/v1/**` on the single
 * orchestrator. If a future feature needs data from somewhere else, the
 * correct move is a new Spring Boot endpoint, not a second HTTP client here.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /* ---------------------------------------------------------------- health */

  health(): Observable<{ status: string }> {
    return this.http.get<{ status: string }>(`${this.baseUrl}/actuator/health`);
  }

  aiStatus(): Observable<AiServiceStatus> {
    return this.http.get<AiServiceStatus>(`${this.api}/ai/status`);
  }

  /* ------------------------------------------------------------- dashboard */

  dashboard(windowDays = 7): Observable<DashboardSnapshot> {
    return this.http.get<DashboardSnapshot>(`${this.api}/dashboard`, {
      params: new HttpParams().set('windowDays', windowDays),
    });
  }

  /* ---------------------------------------------------------- repositories */

  listRepositories(): Observable<RepositorySummary[]> {
    return this.http.get<RepositorySummary[]>(`${this.api}/repositories`);
  }

  getRepository(name: string): Observable<RepositoryDetail> {
    return this.http.get<RepositoryDetail>(`${this.api}/repositories/${encodeURIComponent(name)}`);
  }

  /* --------------------------------------------------------- pull requests */

  listPullRequests(options: PullRequestQuery = {}): Observable<Page<PullRequestSummary>> {
    return this.http.get<Page<PullRequestSummary>>(`${this.api}/pull-requests`, {
      params: this.params(options),
    });
  }

  getPullRequest(prId: string): Observable<PullRequestDetail> {
    return this.http.get<PullRequestDetail>(`${this.api}/pull-requests/${prId}`);
  }

  reanalyzePullRequest(prId: string): Observable<PullRequestDetail> {
    return this.http.post<PullRequestDetail>(`${this.api}/pull-requests/${prId}/reanalyze`, {});
  }

  /** The exact deterministic package that was handed to the model. */
  getPullRequestContextPackage(prId: string): Observable<ContextPackage> {
    return this.http.get<ContextPackage>(`${this.api}/pull-requests/${prId}/context-package`);
  }

  /* -------------------------------------------------------------- releases */

  listReleases(options: ReleaseQuery = {}): Observable<Page<ReleaseSummary>> {
    return this.http.get<Page<ReleaseSummary>>(`${this.api}/releases`, { params: this.params(options) });
  }

  getRelease(releaseId: string): Observable<ReleaseDetail> {
    return this.http.get<ReleaseDetail>(`${this.api}/releases/${releaseId}`);
  }

  createRelease(request: CreateReleaseRequest): Observable<ReleaseDetail> {
    return this.http.post<ReleaseDetail>(`${this.api}/releases`, request);
  }

  buildRelease(releaseId: string): Observable<BuildResult> {
    return this.http.post<BuildResult>(`${this.api}/releases/${releaseId}/build`, {});
  }

  getReleaseNotes(releaseId: string): Observable<ReleaseNotes> {
    return this.http.get<ReleaseNotes>(`${this.api}/releases/${releaseId}/notes`);
  }

  approveRelease(releaseId: string): Observable<ReleaseDetail> {
    return this.http.post<ReleaseDetail>(`${this.api}/releases/${releaseId}/approve`, {});
  }

  markReleaseDeployed(releaseId: string): Observable<ReleaseDetail> {
    return this.http.post<ReleaseDetail>(`${this.api}/releases/${releaseId}/deploy`, {});
  }

  sendReleaseNotifications(releaseId: string): Observable<NotifyResult> {
    return this.http.post<NotifyResult>(`${this.api}/releases/${releaseId}/notify`, {});
  }

  /* ----------------------------------------------------------- deployments */

  listDeployments(options: DeploymentQuery = {}): Observable<Page<DeploymentEvent>> {
    return this.http.get<Page<DeploymentEvent>>(`${this.api}/deployments`, { params: this.params(options) });
  }

  /* ---------------------------------------------------- integration center */

  integrationOverview(): Observable<IntegrationOverview> {
    return this.http.get<IntegrationOverview>(`${this.api}/integration/overview`);
  }

  listInterfaces(options: InterfaceQuery = {}): Observable<Page<IntegrationInterface>> {
    return this.http.get<Page<IntegrationInterface>>(`${this.api}/integration/interfaces`, {
      params: this.params(options),
    });
  }

  getInterface(id: string): Observable<IntegrationInterface> {
    return this.http.get<IntegrationInterface>(`${this.api}/integration/interfaces/${encodeURIComponent(id)}`);
  }

  integrationHealth(): Observable<IntegrationHealthSnapshot> {
    return this.http.get<IntegrationHealthSnapshot>(`${this.api}/integration/health`);
  }

  /* ------------------------------------------------------------ flows */

  listFlows(query?: string): Observable<FlowDefinition[]> {
    return this.http.get<FlowDefinition[]>(`${this.api}/flows`, { params: this.params({ q: query }) });
  }

  getFlow(id: string): Observable<FlowDefinition> {
    return this.http.get<FlowDefinition>(`${this.api}/flows/${encodeURIComponent(id)}`);
  }

  /* ---------------------------------------------------------- payloads */

  listPayloads(options: PayloadQuery = {}): Observable<Page<PayloadDocument>> {
    return this.http.get<Page<PayloadDocument>>(`${this.api}/payloads`, { params: this.params(options) });
  }

  getPayload(id: string): Observable<PayloadContent> {
    return this.http.get<PayloadContent>(`${this.api}/payloads/${encodeURIComponent(id)}`);
  }

  comparePayloads(leftId: string, rightId: string): Observable<PayloadComparison> {
    return this.http.get<PayloadComparison>(`${this.api}/payloads/compare`, {
      params: this.params({ left: leftId, right: rightId }),
    });
  }

  /**
   * Parses arbitrary pasted or uploaded content. The backend sniffs the format
   * rather than trusting a file extension, so a `.txt` holding an IDoc is
   * still recognised as an IDoc.
   */
  parsePayload(request: ParsePayloadRequest): Observable<PayloadContent> {
    return this.http.post<PayloadContent>(`${this.api}/payloads/parse`, request);
  }

  compareRawPayloads(request: CompareRawRequest): Observable<PayloadComparison> {
    return this.http.post<PayloadComparison>(`${this.api}/payloads/compare`, request);
  }

  /* ---------------------------------------------------------- mappings */

  listMappingSets(options: MappingQuery = {}): Observable<Page<MappingSet>> {
    return this.http.get<Page<MappingSet>>(`${this.api}/mappings`, { params: this.params(options) });
  }

  getMappingLinks(setId: string, options: MappingLinkQuery = {}): Observable<Page<MappingLink>> {
    return this.http.get<Page<MappingLink>>(`${this.api}/mappings/${encodeURIComponent(setId)}/links`, {
      params: this.params(options),
    });
  }

  compareMappingSets(setId: string, baseVersion: string, targetVersion: string): Observable<MappingComparison> {
    return this.http.get<MappingComparison>(`${this.api}/mappings/${encodeURIComponent(setId)}/compare`, {
      params: this.params({ base: baseVersion, target: targetVersion }),
    });
  }

  /* ------------------------------------------------------------- APIs */

  listApis(options: ApiCatalogQuery = {}): Observable<Page<ApiDefinition>> {
    return this.http.get<Page<ApiDefinition>>(`${this.api}/api-catalog`, { params: this.params(options) });
  }

  getApi(id: string): Observable<ApiDefinition> {
    return this.http.get<ApiDefinition>(`${this.api}/api-catalog/${encodeURIComponent(id)}`);
  }

  /* ------------------------------------------------- error intelligence */

  listErrorPatterns(options: ErrorQuery = {}): Observable<Page<ErrorPattern>> {
    return this.http.get<Page<ErrorPattern>>(`${this.api}/errors/patterns`, { params: this.params(options) });
  }

  getErrorPattern(id: string): Observable<ErrorPattern> {
    return this.http.get<ErrorPattern>(`${this.api}/errors/patterns/${encodeURIComponent(id)}`);
  }

  /** Classifies raw text — a stack trace, a SOAP fault, a CPI message log. */
  explainError(request: ExplainErrorRequest): Observable<ErrorExplanation> {
    return this.http.post<ErrorExplanation>(`${this.api}/errors/explain`, request);
  }

  listErrorOccurrences(options: ErrorQuery = {}): Observable<Page<ErrorOccurrence>> {
    return this.http.get<Page<ErrorOccurrence>>(`${this.api}/errors/occurrences`, { params: this.params(options) });
  }

  /* --------------------------------------------------------- knowledge */

  listKnowledgeArticles(options: KnowledgeQuery = {}): Observable<Page<KnowledgeArticle>> {
    return this.http.get<Page<KnowledgeArticle>>(`${this.api}/knowledge/articles`, { params: this.params(options) });
  }

  getKnowledgeArticle(id: string): Observable<KnowledgeArticle> {
    return this.http.get<KnowledgeArticle>(`${this.api}/knowledge/articles/${encodeURIComponent(id)}`);
  }

  listGlossary(query?: string): Observable<GlossaryTerm[]> {
    return this.http.get<GlossaryTerm[]>(`${this.api}/knowledge/glossary`, { params: this.params({ q: query }) });
  }

  /* --------------------------------------------------------- assistant */

  askAssistant(request: AssistantRequest): Observable<AssistantMessage> {
    return this.http.post<AssistantMessage>(`${this.api}/assistant/ask`, request);
  }

  assistantSuggestions(context?: string): Observable<AssistantSuggestion[]> {
    return this.http.get<AssistantSuggestion[]>(`${this.api}/assistant/suggestions`, {
      params: this.params({ context }),
    });
  }

  /** Format sniffing for anything a user drops into the app. */
  inspectInput(content: string, filename?: string): Observable<ParsedInput> {
    return this.http.post<ParsedInput>(`${this.api}/inputs/inspect`, { content, filename });
  }

  /* ------------------------------------------------------------- audit */

  listAudit(options: AuditQuery = {}): Observable<Page<AuditEntry>> {
    return this.http.get<Page<AuditEntry>>(`${this.api}/audit`, { params: this.params(options) });
  }

  /* ---------------------------------------------------------- settings */

  getSettings(): Observable<PlatformSettings> {
    return this.http.get<PlatformSettings>(`${this.api}/settings`);
  }

  updateSettings(settings: Partial<PlatformSettings>): Observable<PlatformSettings> {
    return this.http.put<PlatformSettings>(`${this.api}/settings`, settings);
  }

  /* ----------------------------------------------------------- internals */

  private get api(): string {
    return `${this.baseUrl}/api/v1`;
  }

  /**
   * Builds query params, dropping anything undefined, null or blank so the
   * URL only ever carries filters the user actually set. Arrays repeat the
   * key, which is what Spring's `List<String>` binding expects.
   */
  private params(source: object): HttpParams {
    let params = new HttpParams();
    // `object` rather than `Record<string, unknown>`: a plain interface has no
    // index signature, so the stricter type would reject every query shape
    // below and force a cast at each of the twenty call sites.
    for (const [key, value] of Object.entries(source)) {
      if (value === undefined || value === null || value === '') {
        continue;
      }
      if (Array.isArray(value)) {
        for (const entry of value) {
          if (entry !== undefined && entry !== null && entry !== '') {
            params = params.append(key, String(entry));
          }
        }
        continue;
      }
      params = params.set(key, String(value));
    }
    return params;
  }
}

/* =========================================================================
   Query shapes. Every list endpoint takes the same paging vocabulary so the
   data-table component can drive any of them without special-casing.
   ========================================================================= */

/**
 * `sortDirection`, not `direction` — several resources already use `direction`
 * for something domain-meaningful (an interface is INBOUND or OUTBOUND), and a
 * base interface that quietly narrows that field to `'asc' | 'desc'` makes
 * every one of those queries fail to extend it.
 */
export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
  sortDirection?: 'asc' | 'desc';
}

export interface PullRequestQuery extends PageQuery {
  q?: string;
  repo?: string;
  author?: string;
  riskLevel?: string;
  analyzed?: boolean;
  integrationOnly?: boolean;
  since?: string;
}

export interface ReleaseQuery extends PageQuery {
  q?: string;
  repo?: string;
  status?: string;
  deployed?: boolean;
}

export interface DeploymentQuery extends PageQuery {
  environment?: string;
  status?: string;
  repo?: string;
  since?: string;
}

export interface InterfaceQuery extends PageQuery {
  q?: string;
  direction?: string;
  style?: string;
  topology?: string;
  protocol?: string;
  format?: string;
  domain?: string;
  health?: string;
}

export interface PayloadQuery extends PageQuery {
  q?: string;
  format?: string;
  interfaceId?: string;
  direction?: string;
}

export interface MappingQuery extends PageQuery {
  q?: string;
  interfaceId?: string;
  direction?: string;
}

export interface MappingLinkQuery extends PageQuery {
  q?: string;
  status?: string;
  issuesOnly?: boolean;
}

export interface ApiCatalogQuery extends PageQuery {
  q?: string;
  kind?: string;
  system?: string;
  changedOnly?: boolean;
}

export interface ErrorQuery extends PageQuery {
  q?: string;
  category?: string;
  severity?: string;
  layer?: string;
  resolved?: boolean;
}

export interface KnowledgeQuery extends PageQuery {
  q?: string;
  category?: string;
  tag?: string;
}

export interface AuditQuery extends PageQuery {
  q?: string;
  actor?: string;
  action?: string;
  entityType?: string;
  outcome?: string;
  since?: string;
}

export interface ParsePayloadRequest {
  content: string;
  filename?: string;
  /** Optional hint; the backend still sniffs and may disagree. */
  format?: string;
  schema?: string;
}

export interface CompareRawRequest {
  left: string;
  right: string;
  leftLabel?: string;
  rightLabel?: string;
  format?: string;
}

export interface ExplainErrorRequest {
  content: string;
  /** Narrows the catalogue when the user knows where it came from. */
  layer?: string;
  interfaceId?: string;
  /** When false the backend returns the deterministic explanation only. */
  includeNarrative?: boolean;
}
