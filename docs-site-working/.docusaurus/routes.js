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
    path: '/blog',
    component: ComponentCreator('/blog', 'e21'),
    exact: true
  },
  {
    path: '/blog/archive',
    component: ComponentCreator('/blog/archive', '182'),
    exact: true
  },
  {
    path: '/blog/authors',
    component: ComponentCreator('/blog/authors', '0b7'),
    exact: true
  },
  {
    path: '/blog/authors/all-sebastien-lorber-articles',
    component: ComponentCreator('/blog/authors/all-sebastien-lorber-articles', 'ec3'),
    exact: true
  },
  {
    path: '/blog/authors/yangshun',
    component: ComponentCreator('/blog/authors/yangshun', 'b14'),
    exact: true
  },
  {
    path: '/blog/first-blog-post',
    component: ComponentCreator('/blog/first-blog-post', '5c7'),
    exact: true
  },
  {
    path: '/blog/long-blog-post',
    component: ComponentCreator('/blog/long-blog-post', '4f6'),
    exact: true
  },
  {
    path: '/blog/mdx-blog-post',
    component: ComponentCreator('/blog/mdx-blog-post', 'e9f'),
    exact: true
  },
  {
    path: '/blog/tags',
    component: ComponentCreator('/blog/tags', '287'),
    exact: true
  },
  {
    path: '/blog/tags/docusaurus',
    component: ComponentCreator('/blog/tags/docusaurus', '096'),
    exact: true
  },
  {
    path: '/blog/tags/facebook',
    component: ComponentCreator('/blog/tags/facebook', '394'),
    exact: true
  },
  {
    path: '/blog/tags/hello',
    component: ComponentCreator('/blog/tags/hello', '731'),
    exact: true
  },
  {
    path: '/blog/tags/hola',
    component: ComponentCreator('/blog/tags/hola', '4fa'),
    exact: true
  },
  {
    path: '/blog/welcome',
    component: ComponentCreator('/blog/welcome', 'dfe'),
    exact: true
  },
  {
    path: '/markdown-page',
    component: ComponentCreator('/markdown-page', '53a'),
    exact: true
  },
  {
    path: '/docs',
    component: ComponentCreator('/docs', 'ba8'),
    routes: [
      {
        path: '/docs',
        component: ComponentCreator('/docs', '3d8'),
        routes: [
          {
            path: '/docs',
            component: ComponentCreator('/docs', '51d'),
            routes: [
              {
                path: '/docs/api-controllers',
                component: ComponentCreator('/docs/api-controllers', '173'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/architecture',
                component: ComponentCreator('/docs/architecture', '38d'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/call-activity-error-handling',
                component: ComponentCreator('/docs/call-activity-error-handling', '2a9'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/call-activity-examples',
                component: ComponentCreator('/docs/call-activity-examples', '75c'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/call-activity-variable-mapping',
                component: ComponentCreator('/docs/call-activity-variable-mapping', '1b5'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/code-task-quick-start',
                component: ComponentCreator('/docs/code-task-quick-start', '183'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/code-task-test-jar',
                component: ComponentCreator('/docs/code-task-test-jar', '824'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/developer-quick-reference',
                component: ComponentCreator('/docs/developer-quick-reference', '3e8'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/document-handling',
                component: ComponentCreator('/docs/document-handling', '3b2'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-admin-api-integration',
                component: ComponentCreator('/docs/easy-admin-api-integration', '867'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-admin-architecture',
                component: ComponentCreator('/docs/easy-admin-architecture', '497'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-admin-canvas-rendering',
                component: ComponentCreator('/docs/easy-admin-canvas-rendering', '8cd'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-admin-features',
                component: ComponentCreator('/docs/easy-admin-features', '250'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-admin-getting-started',
                component: ComponentCreator('/docs/easy-admin-getting-started', '4af'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-admin-overview',
                component: ComponentCreator('/docs/easy-admin-overview', '292'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-modeler-call-activity',
                component: ComponentCreator('/docs/easy-modeler-call-activity', '1c4'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-modeler-deploy-integration',
                component: ComponentCreator('/docs/easy-modeler-deploy-integration', '9ed'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-modeler-getting-started',
                component: ComponentCreator('/docs/easy-modeler-getting-started', '10a'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-modeler-overview',
                component: ComponentCreator('/docs/easy-modeler-overview', 'e4d'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-task-portal-getting-started',
                component: ComponentCreator('/docs/easy-task-portal-getting-started', '259'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/easy-task-portal-overview',
                component: ComponentCreator('/docs/easy-task-portal-overview', 'c7a'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/epics/epic-call-activity-subprocess-support',
                component: ComponentCreator('/docs/epics/epic-call-activity-subprocess-support', '105'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/epics/epic-code-task-support',
                component: ComponentCreator('/docs/epics/epic-code-task-support', '8cc'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/epics/overview',
                component: ComponentCreator('/docs/epics/overview', 'e57'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/examples',
                component: ComponentCreator('/docs/examples', '6fe'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/examples-readme',
                component: ComponentCreator('/docs/examples-readme', '8ac'),
                exact: true
              },
              {
                path: '/docs/features-architecture',
                component: ComponentCreator('/docs/features-architecture', '1da'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/getting-started',
                component: ComponentCreator('/docs/getting-started', '565'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/implementation-status',
                component: ComponentCreator('/docs/implementation-status', '403'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/integration-testing',
                component: ComponentCreator('/docs/integration-testing', '365'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/intro',
                component: ComponentCreator('/docs/intro', '536'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/LEGAL',
                component: ComponentCreator('/docs/LEGAL', 'f31'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/message-events',
                component: ComponentCreator('/docs/message-events', 'c18'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/metrics-observability',
                component: ComponentCreator('/docs/metrics-observability', '3a9'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/overview',
                component: ComponentCreator('/docs/overview', '3eb'),
                exact: true
              },
              {
                path: '/docs/phase-7-qa-test-plan',
                component: ComponentCreator('/docs/phase-7-qa-test-plan', '4bd'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/phase-8-1-9-8-2-delivery-summary',
                component: ComponentCreator('/docs/phase-8-1-9-8-2-delivery-summary', 'fa4'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/phase-8-1-9-rest-controller',
                component: ComponentCreator('/docs/phase-8-1-9-rest-controller', 'e67'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/phase-8-2-modeler-ui',
                component: ComponentCreator('/docs/phase-8-2-modeler-ui', 'd48'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/phase-8-3-admin-ui',
                component: ComponentCreator('/docs/phase-8-3-admin-ui', '8dc'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/phase-8-3-qa-test-scenarios',
                component: ComponentCreator('/docs/phase-8-3-qa-test-scenarios', '28d'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/phase-8-3-sprint-plan',
                component: ComponentCreator('/docs/phase-8-3-sprint-plan', 'a76'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/phase-8-documentation-index',
                component: ComponentCreator('/docs/phase-8-documentation-index', '1a0'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/phase-8-progress-report',
                component: ComponentCreator('/docs/phase-8-progress-report', '4b0'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/qa-first-phase-test-plan',
                component: ComponentCreator('/docs/qa-first-phase-test-plan', '816'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/readme-main',
                component: ComponentCreator('/docs/readme-main', 'b7a'),
                exact: true
              },
              {
                path: '/docs/tutorial-basics/congratulations',
                component: ComponentCreator('/docs/tutorial-basics/congratulations', 'fe2'),
                exact: true
              },
              {
                path: '/docs/tutorial-basics/create-a-blog-post',
                component: ComponentCreator('/docs/tutorial-basics/create-a-blog-post', 'eef'),
                exact: true
              },
              {
                path: '/docs/tutorial-basics/create-a-document',
                component: ComponentCreator('/docs/tutorial-basics/create-a-document', 'e00'),
                exact: true
              },
              {
                path: '/docs/tutorial-basics/create-a-page',
                component: ComponentCreator('/docs/tutorial-basics/create-a-page', '660'),
                exact: true
              },
              {
                path: '/docs/tutorial-basics/deploy-your-site',
                component: ComponentCreator('/docs/tutorial-basics/deploy-your-site', '19f'),
                exact: true
              },
              {
                path: '/docs/tutorial-basics/markdown-features',
                component: ComponentCreator('/docs/tutorial-basics/markdown-features', '272'),
                exact: true
              },
              {
                path: '/docs/tutorial-extras/manage-docs-versions',
                component: ComponentCreator('/docs/tutorial-extras/manage-docs-versions', '764'),
                exact: true
              },
              {
                path: '/docs/tutorial-extras/translate-your-site',
                component: ComponentCreator('/docs/tutorial-extras/translate-your-site', '898'),
                exact: true
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
