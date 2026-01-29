import { createApp } from 'vue'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import 'vuetify/styles'
import '@mdi/font/css/materialdesignicons.css'
import router from './router'
import './style.css'
import App from './App.vue'
import { md3 } from 'vuetify/blueprints'

const vuetify = createVuetify({
  components,
  directives,
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

createApp(App).use(vuetify).use(router).mount('#app')
