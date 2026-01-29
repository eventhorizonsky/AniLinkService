<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const API_BASE = '/api'

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const form = ref({
  siteName: '',
  siteDescription: '',
  siteUrl: ''
})

const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/site/config`)
    if (res.data?.data) {
      form.value = {
        siteName: res.data.data.siteName || '',
        siteDescription: res.data.data.siteDescription || '',
        siteUrl: res.data.data.siteUrl || ''
      }
    }
  } catch (error) {
    console.error('获取站点配置失败:', error)
    errorMessage.value = '获取配置失败'
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  if (!form.value.siteName || !form.value.siteDescription || !form.value.siteUrl) {
    errorMessage.value = '请填写所有必填项'
    return
  }

  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const res = await axios.put(`${API_BASE}/site/config`, {
      siteName: form.value.siteName,
      siteDescription: form.value.siteDescription,
      siteUrl: form.value.siteUrl
    })

    if (res.data?.code === 200) {
      successMessage.value = '保存成功'
      setTimeout(() => {
        successMessage.value = ''
      }, 3000)
    } else {
      errorMessage.value = res.data?.msg || '保存失败'
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.msg || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchConfig()
})
</script>

<template>
  <div>
    <v-alert v-if="errorMessage" type="error" class="mb-4" closable>
      {{ errorMessage }}
    </v-alert>
    <v-alert v-if="successMessage" type="success" class="mb-4" closable>
      {{ successMessage }}
    </v-alert>

    <v-card>
      <v-card-title>
        <v-icon start>mdi-web</v-icon>
        站点配置
      </v-card-title>
      <v-card-text>
        <v-form>
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
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="primary"
          variant="elevated"
          :loading="saving"
          :disabled="saving"
          @click="saveConfig"
        >
          <v-icon start>mdi-content-save</v-icon>
          保存配置
        </v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>
