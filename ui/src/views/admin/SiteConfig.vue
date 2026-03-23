<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../utils/ui-feedback'

const API_BASE = '/api'
const DEFAULT_DANDAN_BASE_URL = 'https://api.dandanplay.net'

const loading = ref(false)
const saving = ref(false)
const activeTab = ref('basic')
const showTestEmailDialog = ref(false)
const testEmail = ref('')
const sendingTestEmail = ref(false)
const roleOptions = ref([])

const form = ref({
  siteName: '',
  siteDescription: '',
  siteUrl: '',
  authRegisterEnabled: false,
  remoteAccessEnabled: false,
  remoteAccessTokenRequired: false,
  remoteAccessRequiredRole: 'user',
  smtpHost: '',
  smtpPort: 465,
  smtpUsername: '',
  smtpPassword: '',
  smtpFromEmail: '',
  smtpFromName: '',
  smtpSslEnabled: true,
  smtpStarttlsEnabled: false
})

// Dandan 字段
form.value.dandanAppId = ''
form.value.dandanAppSecret = ''
form.value.dandanBaseUrl = DEFAULT_DANDAN_BASE_URL
form.value.dandanAppSecretConfigured = false
form.value.smtpPasswordConfigured = false

const fetchRoleOptions = async () => {
  try {
    const res = await axios.get(`${API_BASE}/users/roles`)
    if (res.data?.code === 200 && Array.isArray(res.data.data)) {
      roleOptions.value = res.data.data
      return
    }
    roleOptions.value = []
  } catch (error) {
    console.error('获取角色列表失败:', error)
    roleOptions.value = []
  }
}

const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/site/config`)
    if (res.data?.data) {
      form.value = {
        siteName: res.data.data.siteName || '',
        siteDescription: res.data.data.siteDescription || '',
        siteUrl: res.data.data.siteUrl || '',
        dandanAppId: res.data.data.dandanAppId || '',
        dandanAppSecret: '',
        dandanBaseUrl: res.data.data.dandanBaseUrl || DEFAULT_DANDAN_BASE_URL,
        dandanAppSecretConfigured: !!res.data.data.dandanAppSecretConfigured,
        authRegisterEnabled: !!res.data.data.authRegisterEnabled,
        remoteAccessEnabled: !!res.data.data.remoteAccessEnabled,
        remoteAccessTokenRequired: !!res.data.data.remoteAccessTokenRequired,
        remoteAccessRequiredRole: res.data.data.remoteAccessRequiredRole || 'user',
        smtpHost: res.data.data.smtpHost || '',
        smtpPort: res.data.data.smtpPort || 465,
        smtpUsername: res.data.data.smtpUsername || '',
        smtpPassword: '',
        smtpFromEmail: res.data.data.smtpFromEmail || '',
        smtpFromName: res.data.data.smtpFromName || '',
        smtpSslEnabled: res.data.data.smtpSslEnabled !== false,
        smtpStarttlsEnabled: !!res.data.data.smtpStarttlsEnabled,
        smtpPasswordConfigured: !!res.data.data.smtpPasswordConfigured
      }
    }
  } catch (error) {
    console.error('获取站点配置失败:', error)
    showAppMessage('获取配置失败', 'error')
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  if (!form.value.siteName || !form.value.siteDescription || !form.value.siteUrl) {
    showAppMessage('请填写所有必填项', 'warning')
    return
  }

  saving.value = true

  try {
    const res = await axios.put(`${API_BASE}/site/config`, {
      siteName: form.value.siteName,
      siteDescription: form.value.siteDescription,
      siteUrl: form.value.siteUrl
        ,
        dandanAppId: form.value.dandanAppId,
        dandanAppSecret: form.value.dandanAppSecret || null,
        dandanBaseUrl: form.value.dandanBaseUrl,
        authRegisterEnabled: form.value.authRegisterEnabled,
        remoteAccessEnabled: form.value.remoteAccessEnabled,
        remoteAccessTokenRequired: form.value.remoteAccessTokenRequired,
        remoteAccessRequiredRole: form.value.remoteAccessRequiredRole,
        smtpHost: form.value.smtpHost,
        smtpPort: form.value.smtpPort,
        smtpUsername: form.value.smtpUsername,
        smtpPassword: form.value.smtpPassword || null,
        smtpFromEmail: form.value.smtpFromEmail,
        smtpFromName: form.value.smtpFromName,
        smtpSslEnabled: form.value.smtpSslEnabled,
        smtpStarttlsEnabled: form.value.smtpStarttlsEnabled
    })

    if (res.data?.code === 200) {
      showAppMessage('保存成功', 'success')
      const localConfig = JSON.parse(localStorage.getItem('siteConfig') || '{}')
      localStorage.setItem('siteConfig', JSON.stringify({
        ...localConfig,
        siteName: form.value.siteName,
        siteDescription: form.value.siteDescription,
        siteUrl: form.value.siteUrl,
        authRegisterEnabled: form.value.authRegisterEnabled,
        remoteAccessEnabled: form.value.remoteAccessEnabled,
        remoteAccessTokenRequired: form.value.remoteAccessTokenRequired,
        remoteAccessRequiredRole: form.value.remoteAccessRequiredRole
      }))
    } else {
      showAppMessage(res.data?.msg || '保存失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '保存失败，请稍后重试', 'error')
  } finally {
    saving.value = false
  }
}

const openTestEmailDialog = () => {
  testEmail.value = ''
  showTestEmailDialog.value = true
}

const sendTestEmail = async () => {
  if (!testEmail.value) {
    showAppMessage('请填写测试收件邮箱', 'warning')
    return
  }

  sendingTestEmail.value = true

  try {
    const res = await axios.post(`${API_BASE}/site/test-email`, {
      toEmail: testEmail.value
    })
    if (res.data?.code === 200) {
      showAppMessage('测试邮件已发送，请检查邮箱', 'success')
      showTestEmailDialog.value = false
    } else {
      showAppMessage(res.data?.msg || '测试邮件发送失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '测试邮件发送失败，请稍后重试', 'error')
  } finally {
    sendingTestEmail.value = false
  }
}

onMounted(() => {
  fetchRoleOptions()
  fetchConfig()
})
</script>

<template>
  <div>
    <v-card>
      <v-card-title>
        <v-icon start>mdi-web</v-icon>
        站点配置
      </v-card-title>
      <v-card-text>
        <v-tabs v-model="activeTab" color="primary" class="mb-4 site-config-tabs">
          <v-tab value="basic">
            <v-icon start>mdi-tune</v-icon>
            站点基础配置
          </v-tab>
          <v-tab value="user">
            <v-icon start>mdi-account-cog</v-icon>
            用户配置
          </v-tab>
          <v-tab value="integration">
            <v-icon start>mdi-link-variant</v-icon>
            关联配置
          </v-tab>
          <v-tab value="remote-access">
            <v-icon start>mdi-access-point-network</v-icon>
            远程访问
          </v-tab>
        </v-tabs>

        <v-form>
          <v-window v-model="activeTab">
            <v-window-item value="basic">
              <h3 class="text-h6 mb-4 text-primary font-weight-medium">
                <v-icon start color="primary">mdi-tune</v-icon>
                站点基础配置
              </h3>
              <v-text-field
                v-model="form.siteName"
                label="站点名称"
                prepend-inner-icon="mdi-label"
                variant="outlined"
                color="primary"
                required
                class="mb-4"
              />
              <v-textarea
                v-model="form.siteDescription"
                label="站点描述"
                prepend-inner-icon="mdi-text"
                variant="outlined"
                color="primary"
                rows="3"
                class="mb-4"
              />
              <v-text-field
                v-model="form.siteUrl"
                label="站点 URL"
                prepend-inner-icon="mdi-link"
                variant="outlined"
                color="primary"
                required
              />
            </v-window-item>

            <v-window-item value="user">
              <h3 class="text-h6 mb-4 text-primary font-weight-medium">
                <v-icon start color="primary">mdi-account-plus</v-icon>
                注册设置
              </h3>
              <v-switch
                v-model="form.authRegisterEnabled"
                label="允许新用户注册"
                color="primary"
                inset
                class="mb-4"
              />

              <v-divider class="my-4" />

              <h3 class="text-h6 mb-4 text-primary font-weight-medium">
                <v-icon start color="primary">mdi-email-fast</v-icon>
                SMTP 配置（注册验证码）
              </h3>
              <v-row dense>
                <v-col cols="12" md="8">
                  <v-text-field
                    v-model="form.smtpHost"
                    label="SMTP Host"
                    prepend-inner-icon="mdi-server"
                    variant="outlined"
                    color="primary"
                    class="mb-3"
                  />
                </v-col>
                <v-col cols="12" md="4">
                  <v-text-field
                    v-model.number="form.smtpPort"
                    label="SMTP Port"
                    type="number"
                    prepend-inner-icon="mdi-numeric"
                    variant="outlined"
                    color="primary"
                    class="mb-3"
                  />
                </v-col>

                <v-col cols="12" md="6">
                  <v-text-field
                    v-model="form.smtpUsername"
                    label="SMTP Username"
                    prepend-inner-icon="mdi-account"
                    variant="outlined"
                    color="primary"
                    class="mb-3"
                  />
                </v-col>
                <v-col cols="12" md="6">
                  <v-text-field
                    v-model="form.smtpPassword"
                    :hint="form.smtpPasswordConfigured ? '已配置密码，不填则保持不变' : '请输入 SMTP 密码'"
                    persistent-hint
                    label="SMTP Password"
                    type="password"
                    prepend-inner-icon="mdi-lock"
                    variant="outlined"
                    color="primary"
                    class="mb-3"
                  />
                </v-col>

                <v-col cols="12" md="6">
                  <v-text-field
                    v-model="form.smtpFromEmail"
                    label="发件邮箱"
                    prepend-inner-icon="mdi-email"
                    variant="outlined"
                    color="primary"
                    class="mb-3"
                  />
                </v-col>
                <v-col cols="12" md="6">
                  <v-text-field
                    v-model="form.smtpFromName"
                    label="发件人名称"
                    prepend-inner-icon="mdi-badge-account"
                    variant="outlined"
                    color="primary"
                    class="mb-3"
                  />
                </v-col>

                <v-col cols="12" md="6">
                  <v-switch
                    v-model="form.smtpSslEnabled"
                    label="启用 SMTP SSL"
                    color="primary"
                    inset
                    class="mt-0"
                  />
                </v-col>
                <v-col cols="12" md="6">
                  <v-switch
                    v-model="form.smtpStarttlsEnabled"
                    label="启用 STARTTLS"
                    color="primary"
                    inset
                    class="mt-0"
                  />
                </v-col>
              </v-row>
            </v-window-item>

            <v-window-item value="integration">
              <h3 class="text-h6 mb-4 text-primary font-weight-medium">
                <v-icon start color="primary">mdi-shield-key</v-icon>
                弹弹PLAY开放平台配置
              </h3>
              <v-text-field
                v-model="form.dandanAppId"
                label="Dandan App ID"
                prepend-inner-icon="mdi-account-key"
                variant="outlined"
                color="primary"
                class="mb-4"
              />
              <v-text-field
                v-model="form.dandanAppSecret"
                :hint="form.dandanAppSecretConfigured ? '已配置密钥，不填则保持不变' : '请输入 Dandan App Secret'"
                persistent-hint
                label="Dandan App Secret"
                type="password"
                prepend-inner-icon="mdi-lock"
                variant="outlined"
                color="primary"
                class="mb-4"
              />
              <v-text-field
                v-model="form.dandanBaseUrl"
                label="Dandan API 地址"
                prepend-inner-icon="mdi-web"
                variant="outlined"
                color="primary"
                hint="默认 https://api.dandanplay.net"
                persistent-hint
                class="mb-4"
              />
            </v-window-item>

            <v-window-item value="remote-access">
              <h3 class="text-h6 mb-4 text-primary font-weight-medium">
                <v-icon start color="primary">mdi-access-point-network</v-icon>
                API v1 远程访问配置
              </h3>

              <v-switch
                v-model="form.remoteAccessEnabled"
                label="开启远程访问（启用 API v1）"
                color="primary"
                inset
                class="mb-4"
              />

              <v-switch
                v-model="form.remoteAccessTokenRequired"
                label="远程访问需要授权"
                color="primary"
                inset
                class="mb-4"
              />

              <v-select
                v-model="form.remoteAccessRequiredRole"
                label="授权所需角色"
                prepend-inner-icon="mdi-shield-account"
                :items="roleOptions"
                item-title="roleName"
                item-value="roleCode"
                variant="outlined"
                color="primary"
                hint="具备该角色（或 super-admin）的用户密钥可访问 API v1"
                persistent-hint
                :disabled="!form.remoteAccessTokenRequired"
              />
            </v-window-item>
          </v-window>
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <template v-if="activeTab === 'user'">
          <v-btn
            color="amber-darken-2"
            variant="elevated"
            prepend-icon="mdi-email-check-outline"
            class="action-btn"
            @click="openTestEmailDialog"
          >
            发送测试邮件
          </v-btn>
          <v-btn
            color="teal-darken-1"
            variant="elevated"
            :loading="saving"
            :disabled="saving"
            class="action-btn"
            @click="saveConfig"
          >
            <v-icon start>mdi-content-save</v-icon>
            保存配置
          </v-btn>
        </template>
        <v-btn
          v-else
          color="teal-darken-1"
          variant="elevated"
          :loading="saving"
          :disabled="saving"
          class="action-btn"
          @click="saveConfig"
        >
          <v-icon start>mdi-content-save</v-icon>
          保存配置
        </v-btn>
      </v-card-actions>
    </v-card>

    <v-dialog v-model="showTestEmailDialog" max-width="460">
      <v-card>
        <v-card-title class="text-h6">
          <v-icon start color="primary">mdi-email-check-outline</v-icon>
          发送测试邮件
        </v-card-title>
        <v-card-text>
          <v-text-field
            v-model="testEmail"
            label="收件邮箱"
            type="email"
            prepend-inner-icon="mdi-email"
            variant="outlined"
            color="primary"
            autofocus
            placeholder="example@domain.com"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showTestEmailDialog = false">取消</v-btn>
          <v-btn
            color="primary"
            :loading="sendingTestEmail"
            :disabled="sendingTestEmail"
            @click="sendTestEmail"
          >
            发送
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.site-config-tabs :deep(.v-tab) {
  border-radius: 8px;
}

.action-btn {
  padding-inline: 18px;
  min-height: 40px;
}
</style>
