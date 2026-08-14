import { createApp, watch } from 'vue'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import { zhHans } from 'vuetify/locale'
import 'vuetify/styles'
import '@mdi/font/css/materialdesignicons.css'
import router from './router'
import './style.css'
import './styles/anime.css'
import './styles/browse.css'
import App from './App.vue'
import { md3 } from 'vuetify/blueprints'
import { setupHttpInterceptors } from './utils/http'
import { theme, isDark } from './composables/useTheme'

const vuetify = createVuetify({
  components,
  directives,
  locale: {
    locale: 'zh-Hans',
    messages: {
      'zh-Hans': zhHans,
    },
  },
  theme: {
    defaultTheme: isDark.value ? 'dark' : 'light',
    themes: {
      light: {
        colors: {
          primary: '#6750a4',
          background: '#fafafa',
          surface: '#ffffff',
        },
      },
      dark: {
        colors: {
          primary: '#b9774a',
          info: '#7fa8c9',
          background: '#1b1612',
          surface: '#201a15',
        },
      },
    },
  },
  blueprint: md3,
  icons: {
    defaultSet: 'mdi',
  },
  defaults: {
    VTextField: {
      variant: 'outlined',
      color: 'primary',
    },
    VTextarea: {
      variant: 'outlined',
      color: 'primary',
    },
    VBtn: {
      variant: 'elevated',
    },
    VCard: {
      elevation: 2,
    },
  },
})

// 前台主题切换时同步 vuetify（后台与全局 v-app 壳、snackbar/对话框）
watch(theme, (value) => {
  vuetify.theme.global.name.value = value === 'dark' ? 'dark' : 'light'
})

setupHttpInterceptors()

createApp(App).use(vuetify).use(router).mount('#app')
