import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docs: [
    'intro',
    {
      type: 'category',
      label: 'Getting Started',
      items: [
        'getting-started/quick-start',
        'getting-started/install',
        'getting-started/configuration',
        'getting-started/first-login',
      ],
    },
    {
      type: 'category',
      label: 'Build Processes',
      items: [
        'guides/create-process',
        'guides/forms',
        'guides/user-tasks',
        'guides/api-tasks',
        'guides/code-tasks',
        'guides/message-events',
        'guides/call-activities',
        'guides/documents',
      ],
    },
    {
      type: 'category',
      label: 'Platform Apps',
      items: [
        'platform/modeler',
        'platform/task-portal',
        'platform/admin',
        'platform/operations',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: [
        'api/overview',
        'api/swagger',
        'api/authentication',
        'api/processes',
        'api/tasks',
        'api/forms',
        'api/documents',
        'api/code-tasks',
        'api/incidents',
        'api/admin-security',
        'api/admin-maintenance',
        'api/ai-credentials',
        'api/schemas',
      ],
    },
    {
      type: 'category',
      label: 'Deployment',
      items: [
        'deployment/docker',
        'deployment/kubernetes',
        'deployment/capacity-planning',
        'deployment/observability',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      items: [
        'reference/process-json',
        'reference/permissions',
        'reference/environment-variables',
        'reference/examples',
      ],
    },
  ],
};

export default sidebars;
