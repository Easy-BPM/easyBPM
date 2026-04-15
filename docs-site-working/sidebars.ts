import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docs: [
    'intro',
    'getting-started',
    {
      label: '🏗️ Architecture',
      items: [
        'architecture',
        'features-architecture',
        'implementation-status'
      ],
    },
    {
      label: '📡 API Reference',
      items: [
        'api-controllers',
      ],
    },
    {
      label: '📊 Observability',
      items: [
        'metrics-observability',
      ],
    },
    {
      label: '🔄 Advanced',
      items: [
        'message-events',
        'integration-testing',
      ],
    },
    {
      label: '�‍💼 Easy BPM Admin',
      items: [
        'easy-admin-overview',
        'easy-admin-getting-started',
        'easy-admin-architecture',
        'easy-admin-features',
        'easy-admin-api-integration',
      ],
    },
    {
      label: '�📚 Guides',
      items: [
        'developer-quick-reference',
        'examples',
      ],
    },
  ],
};

export default sidebars;
