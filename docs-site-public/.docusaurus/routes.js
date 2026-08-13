import React from 'react';
import ComponentCreator from '@docusaurus/ComponentCreator';

export default [
  {
    path: '/__docusaurus/debug',
    component: ComponentCreator('/__docusaurus/debug', '5ff'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/config',
    component: ComponentCreator('/__docusaurus/debug/config', '5ba'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/content',
    component: ComponentCreator('/__docusaurus/debug/content', 'a2b'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/globalData',
    component: ComponentCreator('/__docusaurus/debug/globalData', 'c3c'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/metadata',
    component: ComponentCreator('/__docusaurus/debug/metadata', '156'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/registry',
    component: ComponentCreator('/__docusaurus/debug/registry', '88c'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/routes',
    component: ComponentCreator('/__docusaurus/debug/routes', '000'),
    exact: true
  },
  {
    path: '/search',
    component: ComponentCreator('/search', '822'),
    exact: true
  },
  {
    path: '/docs',
    component: ComponentCreator('/docs', '72b'),
    routes: [
      {
        path: '/docs/next',
        component: ComponentCreator('/docs/next', '2fb'),
        routes: [
          {
            path: '/docs/next',
            component: ComponentCreator('/docs/next', '36d'),
            routes: [
              {
                path: '/docs/next/api/admin-maintenance',
                component: ComponentCreator('/docs/next/api/admin-maintenance', '7e1'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/admin-security',
                component: ComponentCreator('/docs/next/api/admin-security', 'cfe'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/agent-processes',
                component: ComponentCreator('/docs/next/api/agent-processes', 'f5e'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/ai-credentials',
                component: ComponentCreator('/docs/next/api/ai-credentials', '5bb'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/authentication',
                component: ComponentCreator('/docs/next/api/authentication', '2f6'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/code-tasks',
                component: ComponentCreator('/docs/next/api/code-tasks', '7d9'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/documents',
                component: ComponentCreator('/docs/next/api/documents', '87b'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/forms',
                component: ComponentCreator('/docs/next/api/forms', '85f'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/incidents',
                component: ComponentCreator('/docs/next/api/incidents', 'ec6'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/overview',
                component: ComponentCreator('/docs/next/api/overview', '9bd'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/processes',
                component: ComponentCreator('/docs/next/api/processes', '4a5'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/schemas',
                component: ComponentCreator('/docs/next/api/schemas', 'caa'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/swagger',
                component: ComponentCreator('/docs/next/api/swagger', 'eb2'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/api/tasks',
                component: ComponentCreator('/docs/next/api/tasks', '698'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/deployment/capacity-planning',
                component: ComponentCreator('/docs/next/deployment/capacity-planning', '00f'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/deployment/docker',
                component: ComponentCreator('/docs/next/deployment/docker', '50d'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/deployment/kubernetes',
                component: ComponentCreator('/docs/next/deployment/kubernetes', '388'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/deployment/observability',
                component: ComponentCreator('/docs/next/deployment/observability', '3be'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/getting-started/configuration',
                component: ComponentCreator('/docs/next/getting-started/configuration', 'cc4'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/getting-started/first-login',
                component: ComponentCreator('/docs/next/getting-started/first-login', '1a2'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/getting-started/install',
                component: ComponentCreator('/docs/next/getting-started/install', '5c7'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/getting-started/quick-start',
                component: ComponentCreator('/docs/next/getting-started/quick-start', 'e05'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/guides/api-tasks',
                component: ComponentCreator('/docs/next/guides/api-tasks', '72e'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/guides/call-activities',
                component: ComponentCreator('/docs/next/guides/call-activities', '506'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/guides/code-tasks',
                component: ComponentCreator('/docs/next/guides/code-tasks', '170'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/guides/create-process',
                component: ComponentCreator('/docs/next/guides/create-process', '03d'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/guides/documents',
                component: ComponentCreator('/docs/next/guides/documents', '233'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/guides/forms',
                component: ComponentCreator('/docs/next/guides/forms', '06f'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/guides/message-events',
                component: ComponentCreator('/docs/next/guides/message-events', '60f'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/guides/user-tasks',
                component: ComponentCreator('/docs/next/guides/user-tasks', '12c'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/intro',
                component: ComponentCreator('/docs/next/intro', '8ab'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/platform/admin',
                component: ComponentCreator('/docs/next/platform/admin', '4bd'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/platform/modeler',
                component: ComponentCreator('/docs/next/platform/modeler', '56e'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/platform/operations',
                component: ComponentCreator('/docs/next/platform/operations', '47b'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/platform/task-portal',
                component: ComponentCreator('/docs/next/platform/task-portal', 'fe3'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/reference/environment-variables',
                component: ComponentCreator('/docs/next/reference/environment-variables', '3de'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/reference/examples',
                component: ComponentCreator('/docs/next/reference/examples', '6c3'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/reference/permissions',
                component: ComponentCreator('/docs/next/reference/permissions', '85b'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/next/reference/process-json',
                component: ComponentCreator('/docs/next/reference/process-json', '8c4'),
                exact: true,
                sidebar: "docs"
              }
            ]
          }
        ]
      },
      {
        path: '/docs',
        component: ComponentCreator('/docs', '8a9'),
        routes: [
          {
            path: '/docs',
            component: ComponentCreator('/docs', 'bae'),
            routes: [
              {
                path: '/docs/api/admin-security',
                component: ComponentCreator('/docs/api/admin-security', '56b'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/agent-processes',
                component: ComponentCreator('/docs/api/agent-processes', '0de'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/ai-credentials',
                component: ComponentCreator('/docs/api/ai-credentials', 'e1b'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/authentication',
                component: ComponentCreator('/docs/api/authentication', '80a'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/code-tasks',
                component: ComponentCreator('/docs/api/code-tasks', '6b5'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/documents',
                component: ComponentCreator('/docs/api/documents', 'a79'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/forms',
                component: ComponentCreator('/docs/api/forms', 'bbb'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/overview',
                component: ComponentCreator('/docs/api/overview', '5c5'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/processes',
                component: ComponentCreator('/docs/api/processes', 'd59'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/schemas',
                component: ComponentCreator('/docs/api/schemas', 'f17'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/swagger',
                component: ComponentCreator('/docs/api/swagger', '3be'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/api/tasks',
                component: ComponentCreator('/docs/api/tasks', '7c7'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/deployment/docker',
                component: ComponentCreator('/docs/deployment/docker', '43c'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/deployment/kubernetes',
                component: ComponentCreator('/docs/deployment/kubernetes', '1f4'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/deployment/observability',
                component: ComponentCreator('/docs/deployment/observability', 'f3a'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/getting-started/configuration',
                component: ComponentCreator('/docs/getting-started/configuration', '70a'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/getting-started/first-login',
                component: ComponentCreator('/docs/getting-started/first-login', '503'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/getting-started/install',
                component: ComponentCreator('/docs/getting-started/install', 'ef3'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/getting-started/quick-start',
                component: ComponentCreator('/docs/getting-started/quick-start', '310'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/guides/api-tasks',
                component: ComponentCreator('/docs/guides/api-tasks', '707'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/guides/call-activities',
                component: ComponentCreator('/docs/guides/call-activities', 'db0'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/guides/code-tasks',
                component: ComponentCreator('/docs/guides/code-tasks', 'caf'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/guides/create-process',
                component: ComponentCreator('/docs/guides/create-process', '3d9'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/guides/documents',
                component: ComponentCreator('/docs/guides/documents', '638'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/guides/forms',
                component: ComponentCreator('/docs/guides/forms', '5ff'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/guides/message-events',
                component: ComponentCreator('/docs/guides/message-events', '1ed'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/guides/user-tasks',
                component: ComponentCreator('/docs/guides/user-tasks', '8d5'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/intro',
                component: ComponentCreator('/docs/intro', 'd9c'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/platform/admin',
                component: ComponentCreator('/docs/platform/admin', '64a'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/platform/modeler',
                component: ComponentCreator('/docs/platform/modeler', '354'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/platform/task-portal',
                component: ComponentCreator('/docs/platform/task-portal', '80b'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/reference/environment-variables',
                component: ComponentCreator('/docs/reference/environment-variables', '191'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/reference/examples',
                component: ComponentCreator('/docs/reference/examples', 'adb'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/reference/permissions',
                component: ComponentCreator('/docs/reference/permissions', '0f4'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/reference/process-json',
                component: ComponentCreator('/docs/reference/process-json', 'cdc'),
                exact: true,
                sidebar: "docs"
              }
            ]
          }
        ]
      }
    ]
  },
  {
    path: '/',
    component: ComponentCreator('/', 'e5f'),
    exact: true
  },
  {
    path: '*',
    component: ComponentCreator('*'),
  },
];
