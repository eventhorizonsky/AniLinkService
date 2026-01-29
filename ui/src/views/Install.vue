<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import InstallStep1 from '../components/InstallStep1.vue'
import InstallStep2 from '../components/InstallStep2.vue'
import InstallStep3 from '../components/InstallStep3.vue'

const API_BASE = '/api'
const router = useRouter()

const currentStep = ref(1)
const loading = ref(false)
const errorMessage = ref('')
const success = ref(false)

const form = ref({
  siteName: '',
  siteDescription: '',
  siteUrl: window.location.origin,
  adminUsername: '',
  adminPassword: ''
})

const nextStep = () => {
  errorMessage.value = ''

  if (currentStep.value === 1) {
    currentStep.value = 2
  } else if (currentStep.value === 2) {
    if (!form.value.siteName || !form.value.siteDescription || !form.value.siteUrl ||
        !form.value.adminUsername || !form.value.adminPassword) {
      errorMessage.value = '请填写所有必填项'
      return
    }

    if (form.value.adminPassword.length < 6) {
      errorMessage.value = '密码长度至少6位'
      return
    }

    currentStep.value = 3
  } else if (currentStep.value === 3) {
    submitInstallation()
  }
}

const prevStep = () => {
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

const submitInstallation = async () => {
  errorMessage.value = ''
  loading.value = true

  try {
    const res = await axios.post(`${API_BASE}/init/site-config`, {
      siteName: form.value.siteName,
      siteDescription: form.value.siteDescription,
      siteUrl: form.value.siteUrl,
      adminUsername: form.value.adminUsername,
      adminPassword: form.value.adminPassword
    })

    if (res.data?.code === 200) {
      success.value = true
      localStorage.setItem('installed', 'true')
      localStorage.setItem('siteConfig', JSON.stringify({
        siteName: form.value.siteName,
        siteUrl: form.value.siteUrl
      }))
    } else {
      errorMessage.value = res.data?.msg || '安装失败'
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.msg || '安装失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const goToAdmin = () => {
  router.push('/admin')
}

const steps = [
  { title: '系统信息', icon: 'mdi-information' },
  { title: '站点配置', icon: 'mdi-web' },
  { title: '媒体库配置', icon: 'mdi-folder-multiple' }
]
</script>

<template>
  <v-app>
    <v-main class="bg-grey-lighten-5">
      <v-container class="fill-height d-flex align-center justify-center">

        <!-- 安装成功 -->
        <v-card v-if="success" class="elevation-2" width="100%" max-width="450">
          <v-toolbar color="success" flat>
            <v-toolbar-title class="text-white">安装成功</v-toolbar-title>
            <v-spacer />
            <v-icon color="white">mdi-check-circle</v-icon>
          </v-toolbar>
          <v-card-text class="pa-8 text-center">
            <v-icon color="success" size="80">mdi-check-circle-outline</v-icon>
            <h2 class="text-h4 mt-4 mb-2">恭喜！安装完成</h2>
            <p class="text-body-1 text-grey mb-6">AniLinkService 已成功安装</p>
            <v-divider class="my-4" />
            <div class="text-left bg-grey-lighten-4 rounded-lg pa-4">
              <p class="mb-2"><strong>站点名称：</strong>{{ form.siteName }}</p>
              <p><strong>管理员账号：</strong>{{ form.adminUsername }}</p>
            </div>
          </v-card-text>
          <v-card-actions class="pa-6">
            <v-spacer />
            <v-btn
              color="success"
              size="large"
              variant="elevated"
              @click="goToAdmin"
              block
            >
              登录管理后台
              <v-icon end>mdi-arrow-right</v-icon>
            </v-btn>
            <v-spacer />
          </v-card-actions>
        </v-card>

        <!-- 安装向导 -->
        <v-card v-else class="elevation-2" width="100%" max-width="900">
          <v-toolbar color="primary" flat>
            <v-toolbar-title class="text-white">AniLinkService 安装向导</v-toolbar-title>
            <v-spacer />
            <v-icon color="white">mdi-cog</v-icon>
          </v-toolbar>

          <v-card-text class="pa-6">
            <v-stepper
              v-model="currentStep"
              :items="['系统信息', '站点配置', '媒体库配置']"
              hide-actions
            >
              <template v-slot:item.1>
                <v-alert v-if="errorMessage" type="error" class="mb-4" closable>
                  {{ errorMessage }}
                </v-alert>
                <InstallStep1 />
              </template>

              <template v-slot:item.2>
                <v-alert v-if="errorMessage" type="error" class="mb-4" closable>
                  {{ errorMessage }}
                </v-alert>
                <InstallStep2 :form="form" @update:form="form = $event" />
              </template>

              <template v-slot:item.3>
                <v-alert v-if="errorMessage" type="error" class="mb-4" closable>
                  {{ errorMessage }}
                </v-alert>
                <InstallStep3 />
              </template>
            </v-stepper>

            <v-divider class="mt-6 mb-4" />

            <div class="d-flex justify-space-between">
              <v-btn
                v-if="currentStep > 1"
                variant="text"
                @click="prevStep"
              >
                上一步
              </v-btn>
              <v-spacer v-if="currentStep === 1" />
              <v-btn
                v-if="currentStep < 3"
                color="primary"
                variant="elevated"
                @click="nextStep"
              >
                下一步
              </v-btn>
              <v-btn
                v-else
                color="success"
                variant="elevated"
                :loading="loading"
                :disabled="loading"
                @click="submitInstallation"
              >
                <v-icon start>mdi-check</v-icon>
                完成安装
              </v-btn>
            </div>
          </v-card-text>

          <v-divider />

          <v-card-text class="pa-4 pt-0">
            <p class="text-center text-caption text-grey-darken-1">
              AniLinkService v1.0.0 · 基于弹弹play开放平台
            </p>
          </v-card-text>
        </v-card>

      </v-container>
    </v-main>
  </v-app>
</template>

<style scoped>
.fill-height {
  min-height: 100vh;
}
</style>
