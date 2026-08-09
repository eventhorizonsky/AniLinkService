<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../../utils/ui-feedback'

const API_BASE = '/api'

const loading = ref(false)
const saving = ref(false)
const testingConnection = ref(false)
const connectionResult = ref(null)

const form = ref({
  resourceNodeBaseUrl: '',
  resourceDownloadTempDir: './data/media-data/download-temp',
  resourceDownloadMaxConcurrency: 2,
  resourceDownloadLimitKbps: 0,
  resourceUploadLimitKbps: 0,
  resourceSeedTimeSeconds: 0,
  resourceCustomTrackers: '',
  resourceNodeProxyHost: '',
  resourceNodeProxyPort: 0,
  rssProxyHost: '',
  rssProxyPort: 0
})

const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/site/config`)
    const data = res.data?.data || {}
    form.value = {
      resourceNodeBaseUrl: data.resourceNodeBaseUrl || '',
      resourceDownloadTempDir: data.resourceDownloadTempDir || './data/media-data/download-temp',
      resourceDownloadMaxConcurrency: data.resourceDownloadMaxConcurrency || 2,
      resourceDownloadLimitKbps: data.resourceDownloadLimitKbps || 0,
      resourceUploadLimitKbps: data.resourceUploadLimitKbps || 0,
      resourceSeedTimeSeconds: data.resourceSeedTimeSeconds || 0,
      resourceCustomTrackers: data.resourceCustomTrackers || '',
      resourceNodeProxyHost: data.resourceNodeProxyHost || '',
      resourceNodeProxyPort: data.resourceNodeProxyPort || 0,
      rssProxyHost: data.rssProxyHost || '',
      rssProxyPort: data.rssProxyPort || 0
    }
  } catch (error) {
    console.error('获取下载器配置失败:', error)
    showAppMessage('获取下载器配置失败', 'error')
  } finally {
    loading.value = false
  }
}

const rules = {
  positiveInt: (value) => !value || Number(value) >= 1 || '必须大于等于 1',
  nonNegativeInt: (value) => !value || Number(value) >= 0 || '必须大于等于 0',
  proxyPair: (value) => {
    if (!form.value.resourceNodeProxyHost && !form.value.resourceNodeProxyPort) return true
    if (form.value.resourceNodeProxyHost && Number(form.value.resourceNodeProxyPort) > 0) return true
    return '代理主机和端口需同时填写'
  },
  rssProxyPair: (value) => {
    if (!form.value.rssProxyHost && !form.value.rssProxyPort) return true
    if (form.value.rssProxyHost && Number(form.value.rssProxyPort) > 0) return true
    return '代理主机和端口需同时填写'
  }
}

const saveConfig = async () => {
  saving.value = true
  try {
    const payload = {
      resourceNodeBaseUrl: form.value.resourceNodeBaseUrl,
      resourceDownloadTempDir: form.value.resourceDownloadTempDir,
      resourceDownloadMaxConcurrency: Math.max(1, Number(form.value.resourceDownloadMaxConcurrency || 1)),
      resourceDownloadLimitKbps: Math.max(0, Number(form.value.resourceDownloadLimitKbps || 0)),
      resourceUploadLimitKbps: Math.max(0, Number(form.value.resourceUploadLimitKbps || 0)),
      resourceSeedTimeSeconds: Math.max(0, Number(form.value.resourceSeedTimeSeconds || 0)),
      resourceCustomTrackers: form.value.resourceCustomTrackers || '',
      resourceNodeProxyHost: form.value.resourceNodeProxyHost || '',
      resourceNodeProxyPort: Math.max(0, Number(form.value.resourceNodeProxyPort || 0)),
      rssProxyHost: form.value.rssProxyHost || '',
      rssProxyPort: Math.max(0, Number(form.value.rssProxyPort || 0))
    }
    const res = await axios.put(`${API_BASE}/site/config`, payload)
    if (res.data?.code === 200) {
      showAppMessage('下载器配置保存成功', 'success')
    } else {
      showAppMessage(res.data?.msg || '保存失败', 'error')
    }
  } catch (error) {
    console.error('保存下载器配置失败:', error)
    showAppMessage(error.response?.data?.msg || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

const testConnection = async () => {
  if (!form.value.resourceNodeBaseUrl) {
    showAppMessage('请先填写资源搜索节点地址', 'warning')
    return
  }
  testingConnection.value = true
  connectionResult.value = null
  try {
    const res = await axios.post(`${API_BASE}/resource-search/test-connection`)
    connectionResult.value = res.data?.data || null
    if (res.data?.code === 200 && res.data?.data?.ok) {
      showAppMessage(`连接成功，延迟 ${res.data.data.latencyMs}ms`, 'success')
    } else {
      showAppMessage(res.data?.data?.message || '连接失败', 'error')
    }
  } catch (error) {
    console.error('测试连接失败:', error)
    connectionResult.value = { ok: false, message: error.response?.data?.msg || '连接失败' }
    showAppMessage(error.response?.data?.msg || '测试连接失败', 'error')
  } finally {
    testingConnection.value = false
  }
}

onMounted(() => {
  fetchConfig()
})
</script>

<template>
  <div>
    <v-card>
      <v-card-title>
        <v-icon start>mdi-download-box</v-icon>
        资源接口与下载器配置
      </v-card-title>
      <v-card-text>
        <v-form>
          <v-alert type="info" variant="tonal" class="mb-4">
            这里统一管理资源节点接口、下载临时目录、并发与速率限制、做种时长和附加 Tracker。
          </v-alert>

          <h3 class="text-h6 mb-4 text-primary font-weight-medium">
            <v-icon start color="primary">mdi-download-network</v-icon>
            节点与存储
          </h3>

          <v-text-field
            v-model="form.resourceNodeBaseUrl"
            label="资源搜索节点地址"
            prepend-inner-icon="mdi-web"
            variant="outlined"
            color="primary"
            hint="例如: http://127.0.0.1:9000"
            persistent-hint
            class="mb-4"
            :loading="loading"
          />

          <v-text-field
            v-model="form.resourceDownloadTempDir"
            label="下载暂存目录"
            prepend-inner-icon="mdi-folder-download"
            variant="outlined"
            color="primary"
            hint="建议使用统一挂载目录下的子目录"
            persistent-hint
            class="mb-4"
            :loading="loading"
          />

          <v-divider class="my-4" />

          <h3 class="text-h6 mb-4 text-primary font-weight-medium">
            <v-icon start color="primary">mdi-speedometer</v-icon>
            限速与做种
          </h3>

          <v-row dense>
            <v-col cols="12" md="4">
              <v-text-field
                v-model.number="form.resourceDownloadMaxConcurrency"
                label="下载并发上限"
                prepend-inner-icon="mdi-lan-connect"
                type="number"
                min="1"
                max="10"
                variant="outlined"
                color="primary"
                :rules="[rules.positiveInt]"
                class="mb-4"
                :loading="loading"
              />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field
                v-model.number="form.resourceDownloadLimitKbps"
                label="全局下载限速 (KB/s)"
                prepend-inner-icon="mdi-speedometer"
                type="number"
                min="0"
                variant="outlined"
                color="primary"
                hint="0 为不限速；按所有活跃下载任务动态分摊"
                persistent-hint
                :rules="[rules.nonNegativeInt]"
                class="mb-4"
                :loading="loading"
              />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field
                v-model.number="form.resourceUploadLimitKbps"
                label="全局上传限速 (KB/s)"
                prepend-inner-icon="mdi-upload-network"
                type="number"
                min="0"
                variant="outlined"
                color="primary"
                hint="0 为不限速；按所有活跃下载任务动态分摊"
                persistent-hint
                :rules="[rules.nonNegativeInt]"
                class="mb-4"
                :loading="loading"
              />
            </v-col>
          </v-row>

          <v-text-field
            v-model.number="form.resourceSeedTimeSeconds"
            label="下载完成后做种时长 (秒)"
            prepend-inner-icon="mdi-timer-sand"
            type="number"
            min="0"
            variant="outlined"
            color="primary"
            hint=">0 时会先入库并触发扫描，再在暂存目录继续做种，结束后自动清理"
            persistent-hint
            :rules="[rules.nonNegativeInt]"
            class="mb-4"
            :loading="loading"
          />

          <v-textarea
            v-model="form.resourceCustomTrackers"
            label="附加自定义 Tracker"
            prepend-inner-icon="mdi-source-branch"
            variant="outlined"
            color="primary"
            auto-grow
            rows="5"
            hint="每行一个 Tracker，也支持逗号分隔；新下载会自动附加"
            persistent-hint
            :loading="loading"
          />

          <v-divider class="my-4" />

          <h3 class="text-h6 mb-4 text-primary font-weight-medium">
            <v-icon start color="primary">mdi-lan-connect</v-icon>
            代理配置
          </h3>

          <v-row dense>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="form.resourceNodeProxyHost"
                label="资源节点请求代理主机"
                prepend-inner-icon="mdi-web"
                variant="outlined"
                color="primary"
                hint="留空表示不启用"
                persistent-hint
                :rules="[rules.proxyPair]"
                class="mb-4"
                :loading="loading"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field
                v-model.number="form.resourceNodeProxyPort"
                label="资源节点请求代理端口"
                prepend-inner-icon="mdi-numeric"
                type="number"
                min="0"
                variant="outlined"
                color="primary"
                hint="0 表示不启用"
                persistent-hint
                class="mb-4"
                :loading="loading"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="form.rssProxyHost"
                label="RSS 请求代理主机"
                prepend-inner-icon="mdi-rss"
                variant="outlined"
                color="primary"
                hint="留空表示不启用"
                persistent-hint
                :rules="[rules.rssProxyPair]"
                class="mb-4"
                :loading="loading"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field
                v-model.number="form.rssProxyPort"
                label="RSS 请求代理端口"
                prepend-inner-icon="mdi-numeric"
                type="number"
                min="0"
                variant="outlined"
                color="primary"
                hint="0 表示不启用"
                persistent-hint
                class="mb-4"
                :loading="loading"
              />
            </v-col>
          </v-row>
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-chip
          v-if="connectionResult"
          :color="connectionResult.ok ? 'success' : 'error'"
          size="small"
          variant="tonal"
          class="mr-2"
        >
          {{ connectionResult.ok ? `节点连接正常 (${connectionResult.latencyMs}ms)` : `节点连接失败: ${connectionResult.message}` }}
        </v-chip>
        <v-btn variant="outlined" :loading="testingConnection" @click="testConnection">
          <v-icon start>mdi-flash-outline</v-icon>
          测试连接
        </v-btn>
        <v-btn color="primary" :loading="saving" @click="saveConfig">保存配置</v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>
