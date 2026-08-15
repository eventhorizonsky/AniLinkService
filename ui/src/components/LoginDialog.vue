<template>
  <div v-if="modelValue" class="login-modal-overlay" @click.self="close">
    <div class="login-modal" @click.stop>
      <div class="login-header">
        <h2>用户登录</h2>
        <button class="close-btn" @click="close">
          <i class="mdi mdi-close"></i>
        </button>
      </div>

      <div class="login-body">
        <input
          v-model="loginForm.account"
          type="text"
          placeholder="用户名或邮箱"
          class="login-input"
          @keyup.enter="handleLogin"
        />
        <input
          v-model="loginForm.password"
          type="password"
          placeholder="密码"
          class="login-input"
          @keyup.enter="handleLogin"
        />
      </div>

      <div class="login-footer">
        <button v-if="registerEnabled" class="btn-cancel" @click="emit('request-register')">去注册</button>
        <button class="btn-cancel" @click="close">取消</button>
        <button class="btn-login" @click="handleLogin" :disabled="loginLoading">
          {{ loginLoading ? '登录中...' : '登录' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showAppMessage } from '../utils/ui-feedback'
import { useAuth } from '../composables/useAuth'
import { login } from '../api/auth'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  registerEnabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'request-register', 'login-success'])

const { setToken } = useAuth()

const loginForm = ref({
  account: '',
  password: ''
})

const loginLoading = ref(false)

const close = () => {
  emit('update:modelValue', false)
}

const handleLogin = async () => {
  if (!loginForm.value.account || !loginForm.value.password) {
    showAppMessage('请输入用户名/邮箱和密码', 'warning')
    return
  }

  loginLoading.value = true
  try {
    const res = await login({
      account: loginForm.value.account,
      password: loginForm.value.password
    })

    if (res?.code === 200 && res?.data) {
      const { tokenValue } = res.data
      setToken(tokenValue)
      loginForm.value = { account: '', password: '' }
      emit('login-success')
      emit('update:modelValue', false)
      return
    } else {
      showAppMessage(res?.msg || '登录失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '登录失败，请重试', 'error')
  } finally {
    loginLoading.value = false
  }
}
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
