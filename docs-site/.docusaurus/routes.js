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
    component: ComponentCreator('/blog', '98b'),
    exact: true
  },
  {
    path: '/docs',
    component: ComponentCreator('/docs', '5c0'),
    routes: [
      {
        path: '/docs',
        component: ComponentCreator('/docs', '729'),
        routes: [
          {
            path: '/docs',
            component: ComponentCreator('/docs', 'eb4'),
            routes: [
              {
                path: '/docs/api-controllers',
                component: ComponentCreator('/docs/api-controllers', '173'),
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
                component: ComponentCreator('/docs/overview', 'f1b'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/readme-main',
                component: ComponentCreator('/docs/readme-main', 'b7a'),
                exact: true
              }
            ]
          }
        ]
      }
    ]
  },
  {
    path: '*',
    component: ComponentCreator('*'),
  },
];
