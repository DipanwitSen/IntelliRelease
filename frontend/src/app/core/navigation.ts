/**
 * The single source of truth for the left navigation.
 *
 * The router config, the sidebar, the breadcrumb trail and the command
 * palette all read this array, so a module cannot appear in one and be
 * missing from another — the class of inconsistency that makes an enterprise
 * app feel unfinished.
 */
export interface NavItem {
  readonly id: string;
  readonly label: string;
  /** Key into ICONS in the icon component. */
  readonly icon: string;
  readonly route: string;
  /** One line, shown in the command palette and the collapsed-sidebar tooltip. */
  readonly description: string;
  /** Extra words the command palette should match on. */
  readonly keywords?: readonly string[];
}

export interface NavGroup {
  readonly id: string;
  readonly label: string;
  readonly items: readonly NavItem[];
}

/**
 * Grouped rather than flat: seventeen undifferentiated links is a wall, and
 * the groups match how the three audiences actually work — people shipping
 * releases, people debugging integrations, and people running the platform.
 */
export const NAV_GROUPS: readonly NavGroup[] = [
  {
    id: 'overview',
    label: 'Overview',
    items: [
      {
        id: 'dashboard',
        label: 'Dashboard',
        icon: 'grid',
        route: '/dashboard',
        description: 'Delivery, risk and integration health at a glance',
        keywords: ['home', 'overview', 'kpi', 'summary'],
      },
    ],
  },
  {
    id: 'delivery',
    label: 'Delivery',
    items: [
      {
        id: 'repositories',
        label: 'Repositories',
        icon: 'repo',
        route: '/repositories',
        description: 'Connected repositories and their health',
        keywords: ['repo', 'git', 'github', 'source'],
      },
      {
        id: 'pull-requests',
        label: 'Pull Requests',
        icon: 'git-pull-request',
        route: '/pull-requests',
        description: 'Captured changes with deterministic SAP Commerce context',
        keywords: ['pr', 'merge', 'change', 'commit', 'diff'],
      },
      {
        id: 'release-notes',
        label: 'Release Notes',
        icon: 'file-text',
        route: '/release-notes',
        description: 'Audience-specific notes, previewed before anything is sent',
        keywords: ['changelog', 'notes', 'communication', 'email'],
      },
      {
        id: 'risk-analysis',
        label: 'Risk Analysis',
        icon: 'shield',
        route: '/risk-analysis',
        description: 'Deterministic risk scores and the rules behind them',
        keywords: ['risk', 'score', 'policy', 'severity', 'readiness'],
      },
      {
        id: 'ai-summary',
        label: 'AI Summary',
        icon: 'sparkles',
        route: '/ai-summary',
        description: 'What the model was told, and what it concluded',
        keywords: ['ai', 'llm', 'summary', 'assistant', 'chat', 'explain'],
      },
    ],
  },
  {
    id: 'integration',
    label: 'Integration',
    items: [
      {
        id: 'integration-center',
        label: 'Integration Center',
        icon: 'network',
        route: '/integration',
        description: 'Every interface, inbound and outbound, sync and async',
        keywords: ['interface', 'inbound', 'outbound', 'cpi', 'idoc', 'soap', 'rest', 'odata', 'csv'],
      },
      {
        id: 'payload-explorer',
        label: 'Payload Explorer',
        icon: 'braces',
        route: '/payloads',
        description: 'View, validate, diff and compare payloads in any format',
        keywords: ['json', 'xml', 'csv', 'edmx', 'wsdl', 'xsd', 'idoc', 'excel', 'diff', 'compare'],
      },
      {
        id: 'mapping-explorer',
        label: 'Mapping Explorer',
        icon: 'arrow-left-right',
        route: '/mappings',
        description: 'Field journeys end to end, with missing mappings flagged',
        keywords: ['mapping', 'field', 'transformation', 'dto', 'missing', 'renamed'],
      },
      {
        id: 'flow-visualizer',
        label: 'Flow Visualizer',
        icon: 'workflow',
        route: '/flows',
        description: 'Interactive end-to-end diagrams for any business flow',
        keywords: ['flow', 'diagram', 'order', 'invoice', 'shipment', 'sync', 'visualise'],
      },
      {
        id: 'api-explorer',
        label: 'API Explorer',
        icon: 'plug',
        route: '/apis',
        description: 'REST, SOAP, OData and OCC catalogues with contracts',
        keywords: ['api', 'occ', 'swagger', 'openapi', 'wsdl', 'endpoint', 'operation'],
      },
      {
        id: 'error-intelligence',
        label: 'Error Intelligence',
        icon: 'alert-triangle',
        route: '/errors',
        description: 'Raw exceptions translated into cause, fix and test',
        keywords: ['error', 'exception', 'fault', 'stack trace', 'rca', 'root cause', 'fix'],
      },
    ],
  },
  {
    id: 'operations',
    label: 'Operations',
    items: [
      {
        id: 'deployment-timeline',
        label: 'Deployment Timeline',
        icon: 'activity',
        route: '/deployments',
        description: 'What went out, when, and what happened next',
        keywords: ['deploy', 'timeline', 'environment', 'rollback'],
      },
      {
        id: 'release-history',
        label: 'Release History',
        icon: 'history',
        route: '/releases',
        description: 'Every release, its contents and its approvals',
        keywords: ['release', 'history', 'version', 'approve'],
      },
      {
        id: 'knowledge-base',
        label: 'Knowledge Base',
        icon: 'book',
        route: '/knowledge',
        description: 'Patterns, best practices, known issues and glossary',
        keywords: ['kb', 'docs', 'glossary', 'sap note', 'faq', 'best practice'],
      },
      {
        id: 'audit-logs',
        label: 'Audit Logs',
        icon: 'clipboard-list',
        route: '/audit',
        description: 'Immutable record of every governed action',
        keywords: ['audit', 'log', 'compliance', 'who', 'trail'],
      },
      {
        id: 'settings',
        label: 'Settings',
        icon: 'settings',
        route: '/settings',
        description: 'Landscape topology, AI, notifications and governance',
        keywords: ['settings', 'config', 'preferences', 'topology', 'connection'],
      },
    ],
  },
];

/** Flattened, for the command palette and breadcrumb lookups. */
export const NAV_ITEMS: readonly NavItem[] = NAV_GROUPS.flatMap((group) => group.items);

/**
 * Finds the nav item that owns a URL, matching the longest route first so
 * `/releases/abc` resolves to Release History rather than to whichever
 * shorter route happens to be a prefix.
 */
export function navItemForUrl(url: string): NavItem | undefined {
  const path = url.split('?')[0].split('#')[0];
  return [...NAV_ITEMS]
    .sort((a, b) => b.route.length - a.route.length)
    .find((item) => path === item.route || path.startsWith(item.route + '/'));
}
