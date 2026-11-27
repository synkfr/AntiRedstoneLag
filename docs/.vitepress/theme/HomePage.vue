<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { withBase } from 'vitepress'

type ModeKey = 'freeze' | 'notify' | 'off' | 'remove'

interface ModeSpec {
  key: ModeKey
  label: string
  command: string
  summary: string
  detail: string
  configSnippet: string
  outputSnippet: string
}

const modes: ModeSpec[] = [
  {
    key: 'freeze',
    label: 'Freeze',
    command: '/arl redstone on',
    summary: 'Pauses fast clocks safely',
    detail: 'Freezes fast clocks for 15 seconds without breaking blocks.',
    configSnippet: `redstone-enabled: true\nremoval-action: FREEZE\nfreeze:\n  duration-seconds: 15`,
    outputSnippet: `[ARL] Clock paused 15s.\nBlocks stay intact.`
  },
  {
    key: 'notify',
    label: 'Notify only',
    command: '/arl redstone notify',
    summary: 'Keeps farms running and alerts staff',
    detail: 'Skips blocking and sends coordinates, counts, and culprit names.',
    configSnippet: `redstone-enabled: true\nremoval-action: NOTIFY\nalerts:\n  notify-on-tps-lag: true`,
    outputSnippet: `[!] 142, 64, -88 (Chunk: 612)\nCulprit: Klyde (Nearby)`
  },
  {
    key: 'off',
    label: 'Protection off',
    command: '/arl redstone off',
    summary: 'Turns limiting off immediately',
    detail: 'Stops throttling and clears active freezes and chunk locks.',
    configSnippet: `redstone-enabled: false`,
    outputSnippet: `[!] Protection disabled.\nRedstone runs unrestricted.`
  },
  {
    key: 'remove',
    label: 'Drop or remove',
    command: 'removal-action: DROP',
    summary: 'Breaks repeating lag machines',
    detail: 'Drops or clears offending blocks on strict servers.',
    configSnippet: `redstone-enabled: true\nremoval-action: DROP`,
    outputSnippet: `[!] OBSERVER broken\nat 142, 64, -88`
  }
]

const activeKey = ref<ModeKey>('notify')
const copied = ref(false)
const canvasRef = ref<HTMLCanvasElement | null>(null)

const currentMode = () => modes.find((m) => m.key === activeKey.value) ?? modes[0]

async function copyCommand() {
  try {
    await navigator.clipboard.writeText('/arl redstone notify')
    copied.value = true
    window.setTimeout(() => {
      copied.value = false
    }, 1800)
  } catch {
    copied.value = false
  }
}

let rafId = 0
let stopObserver: (() => void) | null = null

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const styles = getComputedStyle(document.documentElement)
  const glyphs = ['.', ':', '+', '*', '#', '0', '1']
  const colors = [
    styles.getPropertyValue('--color-glyph-2').trim(),
    styles.getPropertyValue('--color-glyph-3').trim(),
    styles.getPropertyValue('--color-glyph-4').trim(),
    styles.getPropertyValue('--color-accent-deep').trim()
  ]

  let width = 0
  let height = 0
  let visible = true
  let lastDraw = -100

  const resize = () => {
    const rect = canvas.getBoundingClientRect()
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    width = Math.max(1, Math.floor(rect.width))
    height = Math.max(1, Math.floor(rect.height))
    canvas.width = Math.floor(width * dpr)
    canvas.height = Math.floor(height * dpr)
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    ctx.font = '12px "JetBrains Mono", monospace'
    ctx.textBaseline = 'top'
  }

  const drawFrame = (time: number) => {
    if (!visible) return
    if (!reduceMotion && time - lastDraw < 33) {
      rafId = window.requestAnimationFrame(drawFrame)
      return
    }
    lastDraw = time
    ctx.clearRect(0, 0, width, height)

    const cellW = 10
    const cellH = 16
    const cols = Math.ceil(width / cellW)
    const rows = Math.ceil(height / cellH)
    const startCol = width < 720 ? Math.floor(cols * 0.62) : Math.floor(cols * 0.44)
    const t = reduceMotion ? 0 : time * 0.0012

    for (let c = startCol; c < cols; c++) {
      const colBias = (c - startCol) / Math.max(1, cols - startCol)
      for (let r = 0; r < rows; r++) {
        const wave = Math.sin(c * 0.16 - t * 2.0 + Math.cos(r * 0.24)) + Math.cos(r * 0.2 + t)
        const density = ((wave + 2) * 0.28) * (0.35 + 0.65 * colBias)
        if (density < 0.2) continue
        const idx = Math.min(glyphs.length - 1, Math.floor(density * glyphs.length))
        const colorIdx = Math.min(colors.length - 1, Math.floor(density * colors.length))
        ctx.fillStyle = colors[colorIdx] || colors[0]
        ctx.fillText(glyphs[idx], c * cellW, r * cellH)
      }
    }

    if (!reduceMotion) {
      rafId = window.requestAnimationFrame(drawFrame)
    }
  }

  resize()
  drawFrame(performance.now())
  window.addEventListener('resize', resize)
  const io = new IntersectionObserver(([entry]) => {
    visible = entry.isIntersecting
    if (visible && !reduceMotion && !rafId) {
      rafId = window.requestAnimationFrame(drawFrame)
    }
  })
  io.observe(canvas)
  stopObserver = () => {
    io.disconnect()
    window.removeEventListener('resize', resize)
  }
})

onBeforeUnmount(() => {
  if (rafId) window.cancelAnimationFrame(rafId)
  if (stopObserver) stopObserver()
})
</script>

<template>
  <div class="arl-page">
    <section class="arl-hero">
      <canvas ref="canvasRef" class="arl-hero__canvas" aria-hidden="true"></canvas>
      <div class="container">
        <div class="arl-hero__inner">
          <h1 class="arl-hero__title">Stop redstone lag without breaking farms</h1>
          <p class="arl-hero__lead">
            Paper and Folia plugin that throttles clocks, tracks culprits, and cleans entities.
          </p>
          <div class="arl-actions">
            <a class="key key--primary" :href="withBase('/guide/installation')">Read the guide</a>
            <a class="key" href="https://modrinth.com/plugin/antiredstonelag" target="_blank" rel="noreferrer">Download 26.5</a>
          </div>
          <div class="arl-cmd">
            <code class="arl-cmd__code">/arl redstone notify</code>
            <button class="arl-cmd__copy" type="button" @click="copyCommand" aria-live="polite">
              {{ copied ? 'Copied' : 'Copy' }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="arl-section">
      <div class="container">
        <div class="arl-head">
          <h2 class="arl-head__title">Four redstone modes</h2>
          <p class="arl-head__lead">
            Switch freezing, notify mode, or removal live.
          </p>
        </div>

        <div class="arl-tabs" role="tablist" aria-label="Redstone protection modes">
          <button
            v-for="mode in modes"
            :key="mode.key"
            role="tab"
            type="button"
            class="arl-tab"
            :id="`tab-${mode.key}`"
            :aria-selected="activeKey === mode.key ? 'true' : 'false'"
            :aria-controls="`panel-${mode.key}`"
            @click="activeKey = mode.key"
          >
            {{ mode.label }}
          </button>
        </div>

        <div class="arl-panel-grid" role="tabpanel" :id="`panel-${currentMode().key}`" :aria-labelledby="`tab-${currentMode().key}`">
          <div class="arl-cut arl-mode-copy">
            <div>
              <h3 class="arl-mode-copy__title">{{ currentMode().summary }}</h3>
              <p class="arl-mode-copy__text">{{ currentMode().detail }}</p>
            </div>
            <div class="arl-mode-copy__meta">
              Command: <code>{{ currentMode().command }}</code>
            </div>
          </div>

          <div class="arl-graphite">
            <div class="arl-pane">
              <div class="arl-pane__head">config.yml</div>
              <pre>{{ currentMode().configSnippet }}</pre>
            </div>
            <div class="arl-pane">
              <div class="arl-pane__head">Server output</div>
              <pre class="arl-pane__accent">{{ currentMode().outputSnippet }}</pre>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="arl-section arl-section--well">
      <div class="container">
        <div class="arl-head">
          <h2 class="arl-head__title">Commands and permissions</h2>
          <p class="arl-head__lead">
            Run diagnostics and toggles under /arl.
          </p>
        </div>

        <div class="arl-table-wrap">
          <table class="arl-table">
            <thead>
              <tr>
                <th>Command</th>
                <th>Purpose</th>
                <th>Permission</th>
              </tr>
            </thead>
            <tbody>
              <tr class="is-featured">
                <td class="arl-table__cmd">/arl redstone</td>
                <td>Toggle protection or notify mode</td>
                <td class="arl-table__perm">antiredstonelag.redstone</td>
              </tr>
              <tr>
                <td class="arl-table__cmd">/arl alerts</td>
                <td>Mute or enable staff alerts</td>
                <td class="arl-table__perm">antiredstonelag.alerts</td>
              </tr>
              <tr>
                <td class="arl-table__cmd">/arl inspect</td>
                <td>Live BossBar chunk UPS meter</td>
                <td class="arl-table__perm">antiredstonelag.inspect</td>
              </tr>
              <tr>
                <td class="arl-table__cmd">/arl snapshot</td>
                <td>Inspect or jump to lag sites</td>
                <td class="arl-table__perm">antiredstonelag.snapshot</td>
              </tr>
              <tr>
                <td class="arl-table__cmd">/arl clear</td>
                <td>Clear items and lag entities</td>
                <td class="arl-table__perm">antiredstonelag.clear</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <section class="arl-section">
      <div class="container">
        <div class="arl-split">
          <div class="arl-split__text">
            <h2 class="arl-split__title">TPS lag culprit lookup</h2>
            <p class="arl-split__body">
              When TPS drops or MSPT spikes on Paper, AntiRedstoneLag inspects the busiest chunk and names the player responsible.
            </p>
          </div>
          <div class="arl-split__code">
<pre>alerts:
  enabled: true
  log-to-console: true
  notify-on-tps-lag: true
  only-when-lagging: false

adaptive:
  enabled: true
  target-mspt: 40.0
  min-tps: 18.0
  healthy-headroom-multiplier: 1.5</pre>
          </div>
        </div>
      </div>
    </section>

    <footer class="arl-footer">
      <div class="container">
        <div class="arl-footer__inner">
          <div>
            <div class="arl-footer__brand">AntiRedstoneLag</div>
            <p class="arl-footer__meta">Version 26.5, MIT License, Paper and Folia 1.21.4 to 26.3</p>
          </div>
          <nav class="arl-footer__links" aria-label="Footer links">
            <a :href="withBase('/guide/installation')">Install</a>
            <a :href="withBase('/guide/commands')">Commands</a>
            <a :href="withBase('/guide/configuration')">Configuration</a>
            <a :href="withBase('/guide/clearlagg')">ClearLagg</a>
            <a :href="withBase('/guide/changelog')">Changelog</a>
            <a href="https://github.com/synkfr/AntiRedstoneLag" target="_blank" rel="noreferrer">GitHub</a>
          </nav>
        </div>
      </div>
    </footer>
  </div>
</template>
