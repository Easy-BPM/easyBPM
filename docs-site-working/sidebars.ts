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
        'easy-admin-canvas-rendering',
      ],
    },
    {
      type: 'category',
      label: 'Easy BPMN Modeler',
      items: [
        'easy-modeler-overview',
        'easy-modeler-getting-started',
        'easy-modeler-ai-agent',
        'easy-modeler-deploy-integration',
      ],
    },
    {
      type: 'category',
      label: 'Call Activity & Subprocesses',
      items: [
        'easy-modeler-call-activity',
        'call-activity-variable-mapping',
        'call-activity-error-handling',
        'call-activity-examples',
        'phase-7-qa-test-plan',
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
      label: 'Code Task (Phase 8)',
      items: [
        'code-task-quick-start',
        'code-task-test-jar',
        'phase-8-1-9-rest-controller',
        'phase-8-2-modeler-ui',
        'phase-8-3-admin-ui',
        'phase-8-3-sprint-plan',
        'phase-8-3-qa-test-scenarios',
        'phase-8-1-9-8-2-delivery-summary',
        'phase-8-progress-report',
        'phase-8-documentation-index',
      ],
    },
    {
      type: 'category',
      label: 'EPICs & Roadmap',
      items: [
        'epics/overview',
        'epics/epic-call-activity-subprocess-support',
        'epics/epic-code-task-support',
      ],
    },
    {
      type: 'category',
      label: 'Guides',
      items: [
        'LEGAL',
        'qa-first-phase-test-plan',
        'developer-quick-reference',
        'examples',
      ],
    },
  ],
};

export default sidebars;
