<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const API_BASE = '/api'

const loading = ref(false)
const regenerating = ref(false)
const snackbar = ref(false)
const snackText = ref('')
const config = ref(null)
const showKey = ref(false)
const confirmRegen = ref(false)

const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/mcp/config`)
    if (res.data?.code === 200 && res.data?.data) {
      config.value = res.data.data
    } else {
      snackText.value = res.data?.message || '加载失败'
      snackbar.value = true
    }
  } catch (e) {
    console.error(e)
    snackText.value = e.response?.data?.message || '加载 MCP 配置失败'
    snackbar.value = true
  } finally {
    loading.value = false
  }
}

const copyText = async (text, label) => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    snackText.value = `${label} 已复制`
    snackbar.value = true
  } catch {
    snackText.value = '复制失败，请手动选择文本复制'
    snackbar.value = true
  }
}

const doRegenerate = async () => {
  confirmRegen.value = false
  regenerating.value = true
  try {
    const res = await axios.post(`${API_BASE}/mcp/config/regenerate`)
    if (res.data?.code === 200 && res.data?.data) {
      config.value = res.data.data
      snackText.value = res.data?.message || '已重置 API Key'
      snackbar.value = true
    } else {
      snackText.value = res.data?.message || '重置失败'
      snackbar.value = true
    }
  } catch (e) {
    snackText.value = e.response?.data?.message || '重置失败'
    snackbar.value = true
  } finally {
    regenerating.value = false
  }
}

onMounted(() => {
  fetchConfig()
})
</script>

<template>
  <div>
    <v-snackbar v-model="snackbar" location="top" :timeout="2800">{{ snackText }}</v-snackbar>

    <v-card v-if="loading" class="text-center pa-8">
      <v-progress-circular indeterminate color="primary" size="48" />
      <p class="mt-4 text-body-1">加载中...</p>
    </v-card>

    <v-card v-else-if="config">
      <v-card-title class="d-flex align-center flex-wrap gap-2">
        <v-icon start>mdi-api</v-icon>
        MCP 接入
        <v-spacer />
        <v-btn
          color="warning"
          variant="tonal"
          :loading="regenerating"
          @click="confirmRegen = true"
        >
          重置 API Key
        </v-btn>
        <v-btn color="primary" variant="tonal" @click="fetchConfig">刷新</v-btn>
      </v-card-title>
      <v-card-text>
        <p class="text-body-2 text-medium-emphasis mb-4">
          以下 API Key 与当前超级管理员账号一一对应。在 Cursor 等客户端中使用
          <code>streamable_http</code> 传输，并在请求头携带
          <code>X-API-KEY</code>。建议在「服务配置」中填写正确的站点 URL，以便生成可直连的 MCP 地址。
        </p>

        <v-text-field
          :model-value="config.apiKey"
          label="MCP API Key"
          readonly
          variant="outlined"
          class="mb-3"
          :type="showKey ? 'text' : 'password'"
          :append-inner-icon="showKey ? 'mdi-eye-off' : 'mdi-eye'"
          @click:append-inner="showKey = !showKey"
        />

        <v-text-field
          :model-value="config.mcpUrl"
          label="MCP 端点 URL"
          readonly
          variant="outlined"
          class="mb-3"
        />

        <div class="d-flex gap-2 mb-4 flex-wrap">
          <v-btn size="small" variant="tonal" @click="copyText(config.apiKey, 'API Key')">复制 API Key</v-btn>
          <v-btn size="small" variant="tonal" @click="copyText(config.mcpUrl, 'MCP URL')">复制 MCP URL</v-btn>
        </div>

        <div class="d-flex align-center mb-2">
          <span class="text-subtitle-1 font-weight-medium">一键配置 JSON</span>
          <v-spacer />
          <v-btn
            color="primary"
            variant="flat"
            prepend-icon="mdi-content-copy"
            @click="copyText(config.clientConfigJson, '配置 JSON')"
          >
            复制整段
          </v-btn>
        </div>
        <v-textarea
          :model-value="config.clientConfigJson"
          readonly
          variant="outlined"
          rows="12"
          class="text-mono"
          auto-grow
        />
      </v-card-text>
    </v-card>

    <v-card v-else class="pa-6">
      <p class="text-body-1">暂无数据</p>
      <v-btn class="mt-2" color="primary" @click="fetchConfig">重试</v-btn>
    </v-card>

    <v-dialog v-model="confirmRegen" max-width="480">
      <v-card>
        <v-card-title>确认重置？</v-card-title>
        <v-card-text>旧 API Key 将立即失效，已配置的 MCP 客户端需更新密钥。</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="confirmRegen = false">取消</v-btn>
          <v-btn color="warning" variant="flat" @click="doRegenerate">重置</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.text-mono :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.85rem;
}
</style>
