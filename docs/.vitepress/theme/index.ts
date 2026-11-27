import DefaultTheme from 'vitepress/theme'
import './custom.css'
import HomePage from './HomePage.vue'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('HomePage', HomePage)
  }
}
