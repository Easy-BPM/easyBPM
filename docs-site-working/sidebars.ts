import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docs: [
    'intro',
    'getting-started',
    {
      type: 'category',
      label: 'Architecture',
      items: [
        'architecture',
        'features-architecture',
        'implementation-status'
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: [
        'api-controllers',
      ],
    },
    {
      type: 'category',
      label: 'Observability',
      items: [
        'metrics-observability',
      ],
    },
    {
      type: 'category',
      label: 'Advanced',
      items: [
        'message-events',
        'integration-testing',
      ],
    },
    {
      type: 'category',
      label: 'Easy BPM Admin',
      items: [
        'easy-admin-overview',
        'easy-admin-getting-started',
        'easy-admin-architecture',
        'easy-admin-features',
        'easy-admin-api-integration',
      ],
    },
    {
      type: 'category',
      label: 'Easy BPMN Modeler',
      items: [
        'easy-modeler-overview',
        'easy-modeler-getting-started',
        'easy-modeler-deploy-integration',
      ],
    },
    {
      type: 'category',
      label: 'Easy BPM Task Portal',
      items: [
        'easy-task-portal-overview',
        'easy-task-portal-getting-started',
      ],
    },
    {
      type: 'category',
      label: 'Guides',
      items: [
        'legal',
        'qa-first-phase-test-plan',
        'developer-quick-reference',
        'examples',
      ],
    },
  ],
};

export default sidebars;
