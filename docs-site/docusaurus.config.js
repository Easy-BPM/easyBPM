// @ts-check
// Docusaurus configuration for BPM documentation site

module.exports = {
  title: 'Easy BPM Documentation',
  tagline: 'Documentation for the Easy BPM Engine',
  url: 'http://localhost',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.ico',
  organizationName: 'easybpm',
  projectName: 'bpm-docs-site',
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      },
    ],
  ],
  themeConfig: {
    navbar: {
      title: 'Easy BPM',
      logo: {
        alt: 'Easy BPM Logo',
        src: 'img/logo.svg',
      },
      items: [
        {to: '/docs/overview', label: 'Docs', position: 'left'},
        {to: '/docs/examples', label: 'Examples', position: 'left'},
        {href: 'https://github.com/your-org/your-repo', label: 'GitHub', position: 'right'},
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Overview', to: '/docs/overview'},
            {label: 'Getting Started', to: '/docs/getting-started'},
            {label: 'Implementation Status', to: '/docs/implementation-status'},
            {label: 'Features & Architecture', to: '/docs/features-architecture'},
            {label: 'API & Controllers', to: '/docs/api-controllers'},
            {label: 'Metrics & Observability', to: '/docs/metrics-observability'},
            {label: 'Message Events', to: '/docs/message-events'},
            {label: 'Integration Testing', to: '/docs/integration-testing'},
            {label: 'Examples', to: '/docs/examples'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Easy BPM.`,
    },
    // prism theme removed to fix ProgressPlugin error. Docusaurus will use default code highlighting.
  },
};
