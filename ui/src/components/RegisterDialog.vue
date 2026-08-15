<template>
  <div v-if="modelValue" class="login-modal-overlay" @click.self="close">
    <div class="login-modal" @click.stop>
      <div class="login-header">
        <h2>用户注册</h2>
        <button class="close-btn" @click="close">
          <i class="mdi mdi-close"></i>
        </button>
      </div>

      <div class="login-body">
        <input v-model="registerForm.username" type="text" placeholder="用户名" class="login-input" />
        <input v-model="registerForm.email" type="email" placeholder="邮箱" class="login-input" />
        <input v-model="registerForm.password" type="password" placeholder="密码" class="login-input" />
        <input
          v-model="registerForm.confirmPassword"
          type="password"
          placeholder="确认密码"
          class="login-input"
        />
        <template v-if="captchaEnabled">
          <div class="captcha-row">
            <input
              v-model="registerForm.captchaCode"
              type="text"
              placeholder="图形验证码"
              class="login-input"
            />
            <img
              v-if="captchaImage"
              :src="captchaImageSrc"
              class="captcha-image"
              alt="captcha"
              @click="refreshCaptcha"
            />
          </div>
          <div class="captcha-row">
            <input v-model="registerForm.emailCode" type="text" placeholder="邮箱验证码" class="login-input" />
            <button class="btn-cancel send-code-btn" :disabled="sendCodeDisabled" @click="sendEmailCode">
              {{ sendCodeText }}
            </button>
          </div>
        </template>
        <input v-else v-model="registerForm.emailCode" type="text" placeholder="邮箱验证码" class="login-input" />
      </div>

      <div class="login-footer">
        <button class="btn-cancel" @click="emit('request-login')">去登录</button>
        <button class="btn-cancel" @click="close">取消</button>
        <button class="btn-login" @click="handleRegister" :disabled="registerLoading">
          {{ registerLoading ? '注册中...' : '注册' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { showAppMessage } from '../utils/ui-feedback'
import { register, getCaptcha, sendRegisterEmailCode } from '../api/auth'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  captchaEnabled: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:modelValue', 'request-login', 'register-success'])

const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  captchaCode: '',
  emailCode: ''
})

const registerLoading = ref(false)
const sendCodeLoading = ref(false)
const sendCodeCountdown = ref(0)
const captchaId = ref('')
const captchaImage = ref('')

let countdownTimer = null

const sendCodeDisabled = computed(() => {
  return sendCodeLoading.value || sendCodeCountdown.value > 0
})

const sendCodeText = computed(() => {
  if (sendCodeLoading.value) {
    return '发送中...'
  }
  if (sendCodeCountdown.value > 0) {
    return `${sendCodeCountdown.value}s`
  }
  return '发送验证码'
})

const captchaImageSrc = computed(() => {
  if (!captchaImage.value) {
    return ''
  }
  if (captchaImage.value.startsWith('data:image/')) {
    return captchaImage.value
  }
  return `data:image/png;base64,${captchaImage.value}`
})

const close = () => {
  emit('update:modelValue', false)
}

const refreshCaptcha = async () => {
  try {
    const res = await getCaptcha()
    if (res?.code === 200 && res?.data) {
      captchaId.value = res.data.captchaId
      captchaImage.value = res.data.imageBase64
      registerForm.value.captchaCode = ''
    }
  } catch (error) {
    console.error('获取图形验证码失败:', error)
  }
}

const startSendCodeCountdown = (seconds) => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  sendCodeCountdown.value = seconds
  countdownTimer = setInterval(() => {
    if (sendCodeCountdown.value <= 1) {
      clearInterval(countdownTimer)
      countdownTimer = null
      sendCodeCountdown.value = 0
      return
    }
    sendCodeCountdown.value -= 1
  }, 1000)
}

const sendEmailCode = async () => {
  if (!registerForm.value.email) {
    showAppMessage('请输入邮箱', 'warning')
    return
  }
  if (props.captchaEnabled && !registerForm.value.captchaCode) {
    showAppMessage('请输入图形验证码', 'warning')
    return
  }
  if (props.captchaEnabled && !captchaId.value) {
    await refreshCaptcha()
    showAppMessage('图形验证码已刷新，请重新输入', 'warning')
    return
  }

  sendCodeLoading.value = true
  try {
    const payload = { email: registerForm.value.email }
    if (props.captchaEnabled) {
      payload.captchaId = captchaId.value
      payload.captchaCode = registerForm.value.captchaCode
    }
    const res = await sendRegisterEmailCode(payload)

    if (res?.code === 200) {
      showAppMessage('验证码已发送，请检查邮箱', 'success')
      startSendCodeCountdown(60)
    } else {
      showAppMessage(res?.msg || '发送失败', 'error')
      await refreshCaptcha()
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '发送失败，请稍后重试', 'error')
    await refreshCaptcha()
  } finally {
    sendCodeLoading.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.value.username || !registerForm.value.email || !registerForm.value.password) {
    showAppMessage('请填写完整注册信息', 'warning')
    return
  }
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    showAppMessage('两次密码输入不一致', 'warning')
    return
  }
  if (!registerForm.value.emailCode) {
    showAppMessage('请输入邮箱验证码', 'warning')
    return
  }

  registerLoading.value = true
  try {
    const res = await register({
      username: registerForm.value.username,
      email: registerForm.value.email,
      password: registerForm.value.password,
      emailCode: registerForm.value.emailCode
    })

    if (res?.code === 200) {
      showAppMessage('注册成功，请登录', 'success')
      registerForm.value = {
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
        captchaCode: '',
        emailCode: ''
      }
      emit('register-success')
      emit('update:modelValue', false)
    } else {
      showAppMessage(res?.msg || '注册失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '注册失败，请稍后重试', 'error')
  } finally {
    registerLoading.value = false
  }
}

watch(
  () => props.modelValue,
  (val) => {
    if (val && props.captchaEnabled) {
      refreshCaptcha()
    }
  }
)

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})
</script>

<style scoped>
.login-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.login-modal {
  background: var(--al-bg);
  border-radius: 18px;
  width: 90%;
  max-width: 400px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.16);
  animation: slideUp 0.3s ease;
  overflow: hidden;
  border: 1px solid var(--al-border-panel);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--al-border-neutral);
  background: var(--al-bg);
}

.login-header h2 {
  font-size: 1.1rem;
  color: var(--anime-text-main);
  margin: 0;
  font-weight: 700;
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--anime-text-secondary);
  font-size: 1.2rem;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: 0.2s;
  border-radius: 8px;
}

.close-btn:hover {
  color: var(--anime-accent-red);
  background: var(--al-border-hover);
  transform: rotate(90deg);
}

.login-body {
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--al-bg);
}

.login-input {
  padding: 11px 14px;
  border: 1.5px solid var(--al-border-input);
  border-radius: 10px;
  font-size: 0.95rem;
  color: var(--anime-text-main);
  font-family: inherit;
  transition: 0.2s;
  background: var(--al-bg-soft);
}

.login-input:focus {
  outline: none;
  border-color: var(--anime-accent-red);
  background: var(--al-bg);
  box-shadow: 0 0 0 4px rgba(196, 93, 43, 0.12);
}

.captcha-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.captcha-row .login-input {
  flex: 1;
}

.captcha-image {
  width: 110px;
  height: 42px;
  border-radius: 10px;
  border: 1px solid var(--al-border-input);
  cursor: pointer;
  background: var(--al-bg);
}

.send-code-btn {
  min-width: 110px;
  white-space: nowrap;
}

.login-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--al-border-neutral);
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  background: var(--al-bg);
}

.btn-cancel {
  padding: 9px 20px;
  border: 1px solid var(--al-border-input);
  background: var(--al-bg);
  color: var(--anime-text-secondary);
  border-radius: 10px;
  font-weight: 600;
  cursor: pointer;
  font-size: 0.9rem;
  transition: 0.2s;
}

.btn-cancel:hover {
  background: var(--al-bg-soft);
  color: var(--anime-text-main);
  border-color: var(--anime-accent-brown);
}

.btn-login {
  padding: 10px 24px;
  border: none;
  background: var(--anime-accent-red);
  color: var(--al-text-on-accent);
  border-radius: 10px;
  font-weight: 600;
  cursor: pointer;
  font-size: 0.9rem;
  transition: 0.2s;
  box-shadow: 0 2px 8px rgba(196, 93, 43, 0.3);
}

.btn-login:hover:not(:disabled) {
  background: var(--al-accent-strong);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(196, 93, 43, 0.4);
}

.btn-login:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
