<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import axios from 'axios'
import QRCode from 'qrcode'
import { showAppMessage } from '../utils/ui-feedback'

const API_BASE = '/api'

const loading = ref(false)
const regenerating = ref(false)
const copyingPayload = ref(false)
const canvasRef = ref(null)
const credential = ref({
  remoteAccessEnabled: false,
  tokenRequired: false,
  requiredRole: 'user',
  currentUser: '',
  remoteAccessToken: ''
})

const pageHost = computed(() => {
  if (typeof window === 'undefined') {
    return ''
  }
  return window.location.hostname || ''
})

const pagePort = computed(() => {
  if (typeof window === 'undefined') {
    return 0
  }
  const port = window.location.port
  if (port) {
    return Number(port)
  }
  return window.location.protocol === 'https:' ? 443 : 80
})

const qrPayload = computed(() => {
  const payload = {
    about: '请使用支持弹弹play远程访问功能的客户端扫描此二维码',
    ip: pageHost.value ? [pageHost.value] : [],
    port: pagePort.value,
    machineName: pageHost.value || 'Unknown',
    currentUser: credential.value.currentUser || '',
    tokenRequired: !!credential.value.tokenRequired
  }

  if (credential.value.tokenRequired && credential.value.remoteAccessToken) {
    payload.token = credential.value.remoteAccessToken
  }

  return JSON.stringify(payload)
})

const drawQrCode = async () => {
  if (!canvasRef.value) {
    return
  }

  try {
    await QRCode.toCanvas(canvasRef.value, qrPayload.value, {
      width: 260,
      margin: 1,
      color: {
        dark: '#1f2937',
        light: '#fdfbf9'
      }
    })
  } catch (error) {
    console.error('生成二维码失败:', error)
  }
}

const fetchCredential = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/remote-access/credential`)
    if (res.data?.code === 200 && res.data?.data) {
      credential.value = {
        ...credential.value,
        ...res.data.data
      }
      await drawQrCode()
      return
    }
    showAppMessage(res.data?.msg || '获取远程访问信息失败', 'error')
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '获取远程访问信息失败', 'error')
  } finally {
    loading.value = false
  }
}

const regenerateToken = async () => {
  regenerating.value = true
  try {
    const res = await axios.post(`${API_BASE}/remote-access/credential/regenerate`)
    if (res.data?.code === 200 && res.data?.data) {
      credential.value.remoteAccessToken = res.data.data
      await drawQrCode()
      showAppMessage('已重新生成远程访问密钥', 'success')
      return
    }
    showAppMessage(res.data?.msg || '重置密钥失败', 'error')
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '重置密钥失败，请稍后重试', 'error')
  } finally {
    regenerating.value = false
  }
}

const copyPayload = async () => {
  try {
    copyingPayload.value = true
    await navigator.clipboard.writeText(qrPayload.value)
    showAppMessage('二维码原始数据已复制', 'success')
  } catch (error) {
    showAppMessage('复制失败，请手动复制', 'error')
  } finally {
    copyingPayload.value = false
  }
}

const copyToken = async () => {
  try {
    await navigator.clipboard.writeText(credential.value.remoteAccessToken || '')
    showAppMessage('远程密钥已复制', 'success')
  } catch (e) {
    showAppMessage('复制失败', 'error')
  }
}

watch(qrPayload, () => {
  drawQrCode()
})

onMounted(() => {
  fetchCredential()
})
</script>

<template>
  <div class="remote-access-page unified-page-shell">
    <div class="page-head">
      <h2><i class="mdi mdi-qrcode-scan"></i> 远程访问</h2>
      <span class="sub">通过二维码或密钥在其他设备访问</span>
    </div>

    <div v-if="credential.remoteAccessEnabled === false" class="warn-banner">
      <i class="mdi mdi-alert-circle-outline"></i> 远程访问当前已关闭，API v1 不可用。
    </div>

    <div class="remote-card">
      <div v-if="loading" class="remote-card-loading">
        <i class="mdi mdi-loading mdi-spin"></i> 加载中...
      </div>
      <div v-show="!loading" class="remote-overview">
        <section class="remote-qr-panel">
          <div class="panel-caption">
            <i class="mdi mdi-qrcode"></i> 连接二维码
          </div>

          <div class="qr-canvas-wrap">
            <canvas ref="canvasRef" class="qr-canvas"></canvas>
          </div>

          <div class="qr-caption">扫码导入远程访问信息</div>
          <div class="qr-hint">建议在同一局域网下使用，连接更稳定。</div>
        </section>

        <div class="remote-side-panels">
          <section class="remote-info-panel">
            <div class="panel-caption">
              <i class="mdi mdi-lan-connect"></i> 连接信息
            </div>

            <div class="info-list">
              <div class="info-grid">
                <div class="info-item">
                  <div class="info-label">主机地址</div>
                  <div class="info-value">{{ pageHost || '-' }}</div>
                </div>
                <div class="info-item">
                  <div class="info-label">端口</div>
                  <div class="info-value">{{ pagePort }}</div>
                </div>
                <div class="info-item">
                  <div class="info-label">当前用户</div>
                  <div class="info-value">{{ credential.currentUser || '-' }}</div>
                </div>
                <div class="info-item">
                  <div class="info-label">授权状态</div>
                  <div class="info-value">{{ credential.tokenRequired ? '需要授权' : '无需授权' }}</div>
                </div>
                <div class="info-item info-item-wide">
                  <div class="info-label">访问角色要求</div>
                  <div class="info-value">{{ credential.requiredRole || 'user' }}</div>
                </div>
              </div>
            </div>

            <div v-if="credential.tokenRequired" class="token-section">
              <label class="token-label">当前用户远程密钥</label>
              <div class="token-row">
                <input :value="credential.remoteAccessToken" type="password" readonly />
                <button class="btn btn-ghost" @click="copyToken"><i class="mdi mdi-content-copy"></i> 复制</button>
              </div>
              <button class="btn btn-primary" :disabled="regenerating" @click="regenerateToken">
                <i class="mdi mdi-refresh"></i> {{ regenerating ? '生成中...' : '重新生成密钥' }}
              </button>
            </div>
          </section>

          <section class="payload-panel">
            <div class="payload-header">
              <div class="panel-caption">
                <i class="mdi mdi-code-json"></i> 二维码原始数据
              </div>
              <button class="btn btn-ghost" :disabled="copyingPayload" @click="copyPayload">
                <i class="mdi mdi-content-copy"></i> {{ copyingPayload ? '复制中...' : '复制' }}
              </button>
            </div>
            <pre class="payload-preview">{{ qrPayload }}</pre>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.remote-access-page {
  width: 100%;
  animation: ra-in 0.35s ease-out;
}
@keyframes ra-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

.remote-card {
  background: #fff;
  border: 1px solid #eceff3;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 6px 24px rgba(0, 0, 0, 0.05);
}

.remote-card-loading {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--anime-text-secondary);
  font-size: 13px;
}
.remote-card-loading i {
  font-size: 1.4rem;
  color: var(--anime-accent-red);
}

.warn-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #b45309;
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 13px;
  margin-bottom: 14px;
}

.remote-overview {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 22px;
  align-items: start;
}

.remote-side-panels {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.remote-qr-panel,
.remote-info-panel,
.payload-panel {
  background: transparent;
  border: none;
  border-radius: 0;
}

.remote-qr-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0;
}

.remote-info-panel {
  min-width: 0;
  border-left: 1px solid #f0f0f0;
  padding-left: 20px;
}

.payload-panel {
  border-top: 1px solid #f0f0f0;
  padding-top: 16px;
  border-left: 1px solid #f0f0f0;
  padding-left: 20px;
}

.panel-caption {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--anime-text-main);
}
.panel-caption i {
  color: var(--anime-accent-red);
  font-size: 1.05rem;
}

.qr-canvas-wrap {
  border: 1px solid #eceff3;
  border-radius: 10px;
  background: #ffffff;
  padding: 8px;
}

.qr-canvas {
  width: 260px;
  height: 260px;
}

.qr-caption {
  margin-top: 8px;
  color: #7b6d60;
  font-size: 13px;
}

.qr-hint {
  margin-top: 4px;
  color: #968679;
  font-size: 12px;
}

.info-list {
  min-width: 0;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.info-item {
  border: none;
  border-bottom: 1px dashed #f0f0f0;
  border-radius: 0;
  background: transparent;
  padding: 8px 0;
}

.info-item-wide {
  grid-column: 1 / -1;
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  color: #7a6c5f;
  font-size: 12px;
  margin-bottom: 4px;
}

.info-value {
  color: #2f2b28;
  font-weight: 600;
  line-height: 1.35;
}

.token-section {
  margin-top: 14px;
  display: grid;
  gap: 10px;
}
.token-label {
  font-size: 12px;
  color: var(--anime-text-secondary);
  font-weight: 600;
}
.token-row {
  display: flex;
  gap: 8px;
}
.token-row input {
  flex: 1;
  min-width: 0;
  border: 1.5px solid #e5e7eb;
  border-radius: 10px;
  padding: 9px 12px;
  font-size: 13px;
  color: var(--anime-text-main);
  background: #f9fafb;
  font-family: inherit;
  outline: none;
}

.payload-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.payload-preview {
  margin: 0;
  padding: 12px 0 0;
  border: none;
  border-top: 1px dashed #f0f0f0;
  border-radius: 10px;
  background: transparent;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .remote-overview {
    grid-template-columns: 1fr;
  }

  .remote-qr-panel {
    width: 100%;
    margin: 0;
  }

  .remote-info-panel {
    border-left: none;
    border-top: 1px solid #f0f0f0;
    padding-left: 0;
    padding-top: 14px;
  }

  .payload-panel {
    border-left: none;
    padding-left: 0;
  }
}

@media (max-width: 600px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-item-wide {
    grid-column: auto;
  }
}
</style>
