<script setup>
import { ref, onMounted, computed } from 'vue'
import { askAppConfirm, showAppMessage } from '../../utils/ui-feedback'
import { getBangumiAccountStatus, bindBangumiAccount, unbindBangumiAccount } from '../../api/bangumi'

const loading = ref(false)
const binding = ref(false)
const unbinding = ref(false)
const tokenInput = ref('')
const status = ref({
  bound: false,
  tokenValid: false,
  tokenExpired: false,
  bangumiUserId: null,
  bangumiUsername: '',
  bangumiNickname: '',
  profile: null,
  statusMessage: '未绑定 Bangumi 账号'
})

const avatar = computed(() => {
  const p = status.value.profile
  return p?.avatar?.large || p?.avatar?.medium || p?.avatar?.small || ''
})

const stateTone = computed(() => {
  if (!status.value.bound) return { label: '未绑定', color: '#6b7280' }
  if (status.value.tokenValid) return { label: '已绑定 · Token 有效', color: '#16a34a' }
  if (status.value.tokenExpired) return { label: '已绑定 · Token 过期', color: '#d97706' }
  return { label: '已绑定', color: '#6b7280' }
})

const fetchStatus = async () => {
  loading.value = true
  try {
    const res = await getBangumiAccountStatus()
    if (res?.code === 200 && res?.data) {
      status.value = res.data
    } else showAppMessage(res?.msg || '获取绑定状态失败', 'error')
  } catch (e) { showAppMessage(e.response?.data?.msg || '获取绑定状态失败', 'error') }
  finally { loading.value = false }
}

const bindToken = async () => {
  const token = tokenInput.value.trim()
  if (!token) { showAppMessage('请输入 Bangumi Access Token', 'warning'); return }
  binding.value = true
  try {
    const res = await bindBangumiAccount({ accessToken: token })
    if (res?.code === 200 && res?.data) {
      status.value = res.data
      tokenInput.value = ''
      showAppMessage('Bangumi 账号绑定成功', 'success')
    } else showAppMessage(res?.msg || '绑定失败', 'error')
  } catch (e) { showAppMessage(e.response?.data?.msg || '绑定失败', 'error') }
  finally { binding.value = false }
}

const unbind = async () => {
  const confirmed = await askAppConfirm({
    title: '解除 Bangumi 绑定',
    message: '确定要解除 Bangumi 账号绑定吗？'
  })
  if (!confirmed) return
  unbinding.value = true
  try {
    const res = await unbindBangumiAccount()
    if (res?.code === 200) {
      status.value = {
        bound: false, tokenValid: false, tokenExpired: false,
        bangumiUserId: null, bangumiUsername: '', bangumiNickname: '',
        profile: null, statusMessage: '未绑定 Bangumi 账号'
      }
      showAppMessage('已解除 Bangumi 绑定', 'success')
    } else showAppMessage(res?.msg || '解除绑定失败', 'error')
  } catch (e) { showAppMessage('解除绑定失败', 'error') }
  finally { unbinding.value = false }
}

onMounted(fetchStatus)
</script>

<template>
  <div class="binding-page">
    <div class="page-head">
      <h2><i class="mdi mdi-link-variant"></i> 账号绑定</h2>
    </div>

    <div v-if="loading" class="bind-skeleton"></div>

    <!-- Bangumi 绑定 -->
    <div v-else class="bind-card">
      <div class="bind-card-head">
        <div class="provider">
          <img class="provider-logo" src="/bgmlogo.png" alt="Bangumi" />
          <div>
            <h3>Bangumi</h3>
            <span class="provider-desc">同步收藏 / 评分 / 短评</span>
          </div>
        </div>
        <span class="state-chip" :style="{ background: stateTone.color + '1a', color: stateTone.color }">
          <span class="state-dot" :style="{ background: stateTone.color }"></span>{{ stateTone.label }}
        </span>
      </div>

      <div class="bind-card-body">
        <!-- 已绑定 -->
        <div v-if="status.bound" class="bound-info">
          <img v-if="avatar" :src="avatar" class="bgm-avatar" alt="" />
          <div class="bgm-info">
            <div class="bgm-nickname">{{ status.bangumiNickname || status.bangumiUsername }}</div>
            <div class="bgm-username">
              @{{ status.bangumiUsername }} · UID {{ status.bangumiUserId }}
              <a v-if="status.profile?.url" :href="status.profile.url" target="_blank" rel="noopener">
                <i class="mdi mdi-open-in-new"></i> 查看主页
              </a>
            </div>
            <p v-if="status.statusMessage" class="bgm-status">{{ status.statusMessage }}</p>
          </div>
          <button class="btn btn-danger" :disabled="unbinding" @click="unbind">
            <i class="mdi mdi-link-off"></i> {{ unbinding ? '解除中...' : '解除绑定' }}
          </button>
        </div>

        <!-- 未绑定 -->
        <div v-else class="unbound-info">
          <p class="unbound-tip">
            绑定 Bangumi 账号后，可同步你的追番收藏，并在这部动画详情页同步评分与短评。
          </p>
          <div class="token-row">
            <input
              v-model="tokenInput"
              type="password"
              placeholder="输入 Bangumi Access Token"
              @keyup.enter="bindToken"
            />
            <button class="btn btn-primary" :disabled="binding" @click="bindToken">
              <i class="mdi mdi-link-variant"></i> {{ binding ? '绑定中...' : '绑定' }}
            </button>
          </div>
          <p class="token-hint">
            在 Bangumi 设置页获取 Access Token（需开启开发者权限）。
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.binding-page { animation: in 0.35s ease-out; }
@keyframes in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

.bind-skeleton {
  height: 240px; border-radius: 16px;
  background: linear-gradient(135deg, var(--anime-bg-beige) 25%, var(--al-bg-beige-7) 50%, var(--anime-bg-beige) 75%);
  background-size: 200% 100%;
  animation: br-shim 1.4s ease-in-out infinite;
}

.bind-card {
  background: var(--al-bg); border: 1px solid var(--al-border-panel); border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 6px 24px rgba(0, 0, 0, 0.05);
  overflow: hidden; max-width: 640px;
}
.bind-card-head {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 18px 22px; border-bottom: 1px solid var(--al-border-neutral); flex-wrap: wrap;
}
.provider { display: flex; align-items: center; gap: 12px; }
.provider-logo { width: 40px; height: 40px; border-radius: 10px; background: var(--al-border-neutral); object-fit: contain; padding: 4px; }
.provider h3 { margin: 0; font-size: 16px; color: var(--anime-text-main); }
.provider-desc { font-size: 12px; color: var(--anime-text-secondary); }

.state-chip {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12px; font-weight: 600; padding: 5px 12px; border-radius: 999px;
}
.state-dot { width: 7px; height: 7px; border-radius: 50%; }

.bind-card-body { padding: 22px; }
.bound-info { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.bgm-avatar { width: 72px; height: 72px; border-radius: 16px; object-fit: cover; background: var(--al-border-neutral); border: 1px solid var(--al-border-panel); }
.bgm-info { flex: 1; min-width: 200px; }
.bgm-nickname { font-size: 1.1rem; font-weight: 700; color: var(--anime-text-main); }
.bgm-username { font-size: 13px; color: var(--anime-text-secondary); margin-top: 2px; }
.bgm-username a { color: var(--anime-accent-red); text-decoration: none; margin-left: 6px; }
.bgm-username a:hover { text-decoration: underline; }
.bgm-status { margin: 6px 0 0; font-size: 12px; color: var(--anime-text-secondary); }

.unbound-tip { margin: 0 0 16px; font-size: 13px; color: var(--anime-text-secondary); line-height: 1.6; }
.token-row { display: flex; gap: 10px; }
.token-row input {
  flex: 1; border: 1.5px solid var(--al-border-input); border-radius: 10px;
  padding: 10px 14px; font-size: 13px; outline: none; font-family: inherit;
}
.token-row input:focus { border-color: var(--anime-accent-red); box-shadow: 0 0 0 4px rgba(196, 93, 43, 0.12); }
.token-hint { margin: 12px 0 0; font-size: 12px; color: var(--anime-text-secondary); opacity: 0.8; }
</style>
