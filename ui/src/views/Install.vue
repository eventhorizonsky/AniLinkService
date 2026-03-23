<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import InstallStep1 from '../components/InstallStep1.vue'
import InstallStep2 from '../components/InstallStep2.vue'
import InstallStep3 from '../components/InstallStep3.vue'

const API_BASE = '/api'
const DEFAULT_DANDAN_BASE_URL = 'https://api.dandanplay.net'
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
  adminPassword: '',
  dandanBaseUrl: DEFAULT_DANDAN_BASE_URL
})

// 增加 Dandan 字段
form.value.dandanAppId = ''
form.value.dandanAppSecret = ''

const nextStep = () => {
  errorMessage.value = ''

  if (currentStep.value === 1) {
    currentStep.value = 2
  } else if (currentStep.value === 2) {
    if (!validateStep2()) {
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

const validateStep2 = () => {
  if (!form.value.siteName || !form.value.siteDescription || !form.value.siteUrl ||
      !form.value.adminUsername || !form.value.adminPassword) {
    errorMessage.value = '请填写所有项'
    return false
  }

  if (form.value.adminPassword.length < 6) {
    errorMessage.value = '密码长度至少6位'
    return false
  }

  return true
}

const goToStep = (targetStep) => {
  if (targetStep === currentStep.value) {
    return
  }

  errorMessage.value = ''

  // 回退不需要校验
  if (targetStep < currentStep.value) {
    currentStep.value = targetStep
    return
  }

  // 前进到第3步前，校验第2步必填
  if (targetStep >= 3 && !validateStep2()) {
    return
  }

  currentStep.value = targetStep
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
        ,
        dandanAppId: form.value.dandanAppId,
        dandanAppSecret: form.value.dandanAppSecret,
        dandanBaseUrl: form.value.dandanBaseUrl
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

const goToHome = () => {
  router.push('/')
}

const steps = [
  { title: '系统信息', icon: 'mdi-information' },
  { title: '站点配置', icon: 'mdi-web' },
  { title: '媒体库配置', icon: 'mdi-folder-multiple' }
]
</script>

<template>
  <v-app>
    <v-main class="bg-grey-lighten-5 install-main">
      <v-container fluid class="fill-height d-flex justify-center install-container">

        <!-- 安装成功 -->
        <v-card v-if="success" class="elevation-2 install-card" width="100%" max-width="1400">
          <v-toolbar color="success" flat>
            <v-toolbar-title class="text-white">安装成功</v-toolbar-title>
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
              @click="goToHome"
              class="px-8"
            >
              <v-icon start>mdi-home</v-icon>
              返回首页
            </v-btn>
            <v-spacer />
          </v-card-actions>
        </v-card>

        <!-- 安装向导 -->
        <v-card v-else class="elevation-2 install-card" width="100%" max-width="1400">
          <v-toolbar color="primary" flat>
            <v-toolbar-title class="text-white">AniLinkService 安装向导</v-toolbar-title>
          </v-toolbar>

          <v-card-text class="pa-0">
            <div class="wizard-layout">
              <aside class="wizard-sidebar">
                <div class="wizard-sidebar-title">安装步骤</div>
                <v-list density="comfortable" class="bg-transparent pa-0">
                  <v-list-item
                    v-for="(step, index) in steps"
                    :key="step.title"
                    class="wizard-step-item"
                    :class="{
                      'is-active': currentStep === index + 1,
                      'is-done': currentStep > index + 1
                    }"
                    @click="goToStep(index + 1)"
                  >
                    <template #prepend>
                      <v-avatar size="30" :color="currentStep >= index + 1 ? 'primary' : 'grey-lighten-2'">
                        <v-icon size="16" :color="currentStep >= index + 1 ? 'white' : 'grey-darken-1'">
                          {{ step.icon }}
                        </v-icon>
                      </v-avatar>
                    </template>
                    <v-list-item-title class="font-weight-medium">{{ step.title }}</v-list-item-title>
                  </v-list-item>
                </v-list>
              </aside>

              <section class="wizard-content">
                <div class="wizard-mobile-steps">
                  <v-chip
                    v-for="(step, index) in steps"
                    :key="step.title"
                    :color="currentStep === index + 1 ? 'primary' : 'grey-lighten-2'"
                    :text-color="currentStep === index + 1 ? 'white' : 'grey-darken-2'"
                    size="small"
                    class="mr-2 mb-2"
                    @click="goToStep(index + 1)"
                  >
                    {{ index + 1 }}. {{ step.title }}
                  </v-chip>
                </div>

                <v-alert v-if="errorMessage" type="error" class="mb-4" closable>
                  {{ errorMessage }}
                </v-alert>

                <div class="wizard-panel">
                  <InstallStep1 v-if="currentStep === 1" />
                  <InstallStep2 v-else-if="currentStep === 2" :form="form" @update:form="form = $event" />
                  <InstallStep3 v-else />
                </div>

                <v-divider class="mt-6 mb-4" />

                <div class="d-flex justify-space-between flex-wrap gap-2">
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
              </section>
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
.install-main {
  overflow-y: auto;
}

.fill-height {
  min-height: 100vh;
}

.install-container {
  align-items: flex-start;
  padding: 24px 20px;
}

.install-card {
  overflow: hidden;
  width: min(96vw, 1400px);
}

.wizard-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  min-height: 620px;
}

.wizard-sidebar {
  border-right: 1px solid rgba(0, 0, 0, 0.08);
  padding: 24px 16px;
  background: linear-gradient(180deg, rgba(25, 118, 210, 0.05), rgba(25, 118, 210, 0.01));
}

.wizard-sidebar-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.75);
  margin-bottom: 14px;
}

.wizard-step-item {
  border-radius: 10px;
  margin-bottom: 6px;
  cursor: pointer;
}

.wizard-step-item.is-active {
  background: rgba(25, 118, 210, 0.1);
}

.wizard-content {
  padding: 28px;
}

.wizard-mobile-steps {
  display: none;
}

@media (min-width: 960px) {
  .install-container {
    align-items: center;
  }
}

@media (max-width: 959px) {
  .wizard-layout {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .wizard-sidebar {
    display: none;
  }

  .wizard-content {
    padding: 18px 14px;
  }

  .wizard-mobile-steps {
    display: block;
    margin-bottom: 8px;
  }
}
</style>
