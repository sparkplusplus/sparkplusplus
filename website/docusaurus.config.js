const config = {
  title: 'SparkPlusPlus',
  tagline: 'Build structured Spark applications with less boilerplate.',
  favicon: 'img/favicon.svg',
  url: 'https://sparkplusplus.github.io',
  baseUrl: '/',
  organizationName: 'sparkplusplus',
  projectName: 'sparkplusplus',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'throw',
  trailingSlash: true,

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          path: 'docs',
          routeBasePath: 'docs',
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: 'https://github.com/sparkplusplus/sparkplusplus/tree/main/website/',
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      },
    ],
  ],

  plugins: [
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'usecases',
        path: 'use-cases-docs',
        routeBasePath: 'use-cases',
        sidebarPath: require.resolve('./usecaseSidebars.js'),
        editUrl: 'https://github.com/sparkplusplus/sparkplusplus/tree/main/website/',
      },
    ],
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'contributing',
        path: 'contributing-docs',
        routeBasePath: 'contributing-guide',
        sidebarPath: require.resolve('./contributingSidebars.js'),
        editUrl: 'https://github.com/sparkplusplus/sparkplusplus/tree/main/website/',
      },
    ],
  ],

  themeConfig: {
    image: 'img/social-card.svg',
    navbar: {
      title: 'SparkPlusPlus',
      logo: {
        alt: 'SparkPlusPlus',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docs',
          position: 'left',
          label: 'Docs',
        },
        {
          type: 'docSidebar',
          sidebarId: 'useCases',
          docsPluginId: 'usecases',
          label: 'Use Cases',
          position: 'left',
        },
        {
          type: 'docSidebar',
          sidebarId: 'contributingGuide',
          docsPluginId: 'contributing',
          label: 'Contributing Guide',
          position: 'left',
        },
        {
          href: 'https://github.com/sparkplusplus/sparkplusplus',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            { label: 'Getting Started', to: '/docs/getting-started' },
            { label: 'SparkApp Guide', to: '/docs/sparkapp' },
            { label: 'Contributing Guide', to: '/contributing-guide/' },
            { label: 'Use Cases', to: '/use-cases/' },
          ],
        },
        {
          title: 'Community',
          items: [
            { label: 'GitHub', href: 'https://github.com/sparkplusplus/sparkplusplus' },
            { label: 'Issues', href: 'https://github.com/sparkplusplus/sparkplusplus/issues' },
          ],
        },
      ],
      copyright: `Copyright ${new Date().getFullYear()} SparkPlusPlus`,
    },
    colorMode: {
      defaultMode: 'light',
      disableSwitch: false,
      respectPrefersColorScheme: true,
    },
    prism: {
      additionalLanguages: ['java', 'scala', 'bash', 'yaml'],
    },
  },
};

module.exports = config;
