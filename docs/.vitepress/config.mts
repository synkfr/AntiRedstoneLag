import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'en-US',
  title: 'AntiRedstoneLag',
  description: 'Redstone lag prevention, live diagnostics, and entity cleanup for Paper, Purpur, and Folia.',
  base: '/AntiRedstoneLag/',
  cleanUrls: true,
  appearance: false,
  head: [
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
    ['link', {
      rel: 'stylesheet',
      href: 'https://fonts.googleapis.com/css2?family=Familjen+Grotesk:wght@500;600&family=IBM+Plex+Sans:wght@400;500&family=JetBrains+Mono:wght@400;500&display=swap'
    }]
  ],
  themeConfig: {
    siteTitle: 'AntiRedstoneLag',
    search: {
      provider: 'local'
    },
    nav: [
      { text: 'Install', link: '/guide/installation' },
      { text: 'Commands', link: '/guide/commands' },
      { text: 'Config', link: '/guide/configuration' },
      { text: 'ClearLagg', link: '/guide/clearlagg' },
      { text: 'Changelog', link: '/guide/changelog' }
    ],
    sidebar: {
      '/guide/': [
        {
          text: 'Documentation',
          items: [
            { text: 'Installation', link: '/guide/installation' },
            { text: 'Commands and permissions', link: '/guide/commands' },
            { text: 'Configuration', link: '/guide/configuration' },
            { text: 'ClearLagg rules', link: '/guide/clearlagg' },
            { text: 'Changelog', link: '/guide/changelog' }
          ]
        }
      ]
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/synkfr/AntiRedstoneLag' },
      { icon: 'discord', link: 'https://discord.gg/UAgsmk6KEr' }
    ]
  }
})
