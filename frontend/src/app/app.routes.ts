import { Routes } from '@angular/router';

/**
 * Route table.
 *
 * Every feature is lazily loaded via `loadComponent`, so opening the Dashboard
 * does not pay for the Payload Explorer's parsers or the Flow Visualiser's
 * diagram engine. `data.breadcrumb` is what BreadcrumbService reads; detail
 * pages append their own trailing crumb once their subject has loaded.
 *
 * `title` is set per route so the browser tab, history entries and bookmarks
 * are meaningful — an easy thing to skip and an obvious omission once noticed.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

  {
    path: 'dashboard',
    title: 'Dashboard · IntelliRelease',
    data: { breadcrumb: 'Dashboard' },
    loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },

  /* ------------------------------------------------------------- delivery */
  {
    path: 'repositories',
    data: { breadcrumb: 'Repositories' },
    children: [
      {
        path: '',
        title: 'Repositories · IntelliRelease',
        loadComponent: () =>
          import('./features/repositories/repositories.page').then((m) => m.RepositoriesPage),
      },
      {
        path: ':name',
        title: 'Repository · IntelliRelease',
        loadComponent: () =>
          import('./features/repositories/repository-detail.page').then((m) => m.RepositoryDetailPage),
      },
    ],
  },
  {
    path: 'pull-requests',
    data: { breadcrumb: 'Pull Requests' },
    children: [
      {
        path: '',
        title: 'Pull Requests · IntelliRelease',
        loadComponent: () =>
          import('./features/pull-requests/pull-requests.page').then((m) => m.PullRequestsPage),
      },
      {
        path: ':id',
        title: 'Pull Request · IntelliRelease',
        loadComponent: () =>
          import('./features/pull-requests/pull-request-detail.page').then((m) => m.PullRequestDetailPage),
      },
    ],
  },
  {
    path: 'release-notes',
    title: 'Release Notes · IntelliRelease',
    data: { breadcrumb: 'Release Notes' },
    loadComponent: () => import('./features/release-notes/release-notes.page').then((m) => m.ReleaseNotesPage),
  },
  {
    path: 'risk-analysis',
    title: 'Risk Analysis · IntelliRelease',
    data: { breadcrumb: 'Risk Analysis' },
    loadComponent: () => import('./features/risk/risk-analysis.page').then((m) => m.RiskAnalysisPage),
  },
  {
    path: 'ai-summary',
    title: 'AI Summary · IntelliRelease',
    data: { breadcrumb: 'AI Summary' },
    loadComponent: () => import('./features/ai-summary/ai-summary.page').then((m) => m.AiSummaryPage),
  },

  /* ---------------------------------------------------------- integration */
  {
    path: 'integration',
    data: { breadcrumb: 'Integration Center' },
    children: [
      {
        path: '',
        title: 'Integration Center · IntelliRelease',
        loadComponent: () =>
          import('./features/integration/integration-center.page').then((m) => m.IntegrationCenterPage),
      },
      {
        path: 'interfaces/:id',
        title: 'Interface · IntelliRelease',
        loadComponent: () =>
          import('./features/integration/interface-detail.page').then((m) => m.InterfaceDetailPage),
      },
    ],
  },
  {
    path: 'payloads',
    title: 'Payload Explorer · IntelliRelease',
    data: { breadcrumb: 'Payload Explorer' },
    loadComponent: () => import('./features/payloads/payload-explorer.page').then((m) => m.PayloadExplorerPage),
  },
  {
    path: 'mappings',
    title: 'Mapping Explorer · IntelliRelease',
    data: { breadcrumb: 'Mapping Explorer' },
    loadComponent: () => import('./features/mappings/mapping-explorer.page').then((m) => m.MappingExplorerPage),
  },
  {
    path: 'flows',
    data: { breadcrumb: 'Flow Visualizer' },
    children: [
      {
        path: '',
        title: 'Flow Visualizer · IntelliRelease',
        loadComponent: () => import('./features/flows/flow-visualizer.page').then((m) => m.FlowVisualizerPage),
      },
      {
        path: ':id',
        title: 'Flow · IntelliRelease',
        loadComponent: () => import('./features/flows/flow-visualizer.page').then((m) => m.FlowVisualizerPage),
      },
    ],
  },
  {
    path: 'apis',
    title: 'API Explorer · IntelliRelease',
    data: { breadcrumb: 'API Explorer' },
    loadComponent: () => import('./features/apis/api-explorer.page').then((m) => m.ApiExplorerPage),
  },
  {
    path: 'errors',
    title: 'Error Intelligence · IntelliRelease',
    data: { breadcrumb: 'Error Intelligence' },
    loadComponent: () => import('./features/errors/error-intelligence.page').then((m) => m.ErrorIntelligencePage),
  },

  /* ----------------------------------------------------------- operations */
  {
    path: 'deployments',
    title: 'Deployment Timeline · IntelliRelease',
    data: { breadcrumb: 'Deployment Timeline' },
    loadComponent: () => import('./features/deployments/deployments.page').then((m) => m.DeploymentsPage),
  },
  {
    path: 'releases',
    data: { breadcrumb: 'Release History' },
    children: [
      {
        path: '',
        title: 'Release History · IntelliRelease',
        loadComponent: () => import('./features/releases/releases.page').then((m) => m.ReleasesPage),
      },
      {
        path: ':id',
        title: 'Release · IntelliRelease',
        loadComponent: () => import('./features/releases/release-detail.page').then((m) => m.ReleaseDetailPage),
      },
    ],
  },
  {
    path: 'knowledge',
    title: 'Knowledge Base · IntelliRelease',
    data: { breadcrumb: 'Knowledge Base' },
    loadComponent: () => import('./features/knowledge/knowledge-base.page').then((m) => m.KnowledgeBasePage),
  },
  {
    path: 'audit',
    title: 'Audit Logs · IntelliRelease',
    data: { breadcrumb: 'Audit Logs' },
    loadComponent: () => import('./features/audit/audit.page').then((m) => m.AuditPage),
  },
  {
    path: 'settings',
    title: 'Settings · IntelliRelease',
    data: { breadcrumb: 'Settings' },
    loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage),
  },

  {
    path: '**',
    title: 'Not found · IntelliRelease',
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
