<script setup>
import { ref, onMounted } from 'vue'
import { showAppMessage } from '../../utils/ui-feedback'
import { copyText as copyTextUtil } from '../../utils/clipboard'
import { getMcpConfig, regenerateMcpConfig } from '../../api/system'

const loading = ref(false)
const regenerating = ref(false)
const config = ref(null)
const showKey = ref(false)
const confirmRegen = ref(false)

const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await getMcpConfig()
    if (res?.code === 200 && res?.data) {
      config.value = res.data
    } else {
      showAppMessage(res?.msg || '加载失败')
    }
  } catch (e) {
    console.error(e)
    showAppMessage(e.response?.data?.msg || '加载 MCP 配置失败')
  } finally {
    loading.value = false
  }
}

const copyText = (text, label) => copyTextUtil(text, `${label} 已复制`)

const doRegenerate = async () => {
  confirmRegen.value = false
  regenerating.value = true
  try {
    const res = await regenerateMcpConfig()
    if (res?.code === 200 && res?.data) {
      config.value = res.data
      showAppMessage(res?.msg || '已重置 API Key')
    } else {
      showAppMessage(res?.msg || '重置失败')
    }
  } catch (e) {
    showAppMessage(e.response?.data?.msg || '重置失败')
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
