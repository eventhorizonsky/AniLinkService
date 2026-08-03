import { createApp } from 'vue'
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
    defaultTheme: 'light',
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

setupHttpInterceptors()

createApp(App).use(vuetify).use(router).mount('#app')
