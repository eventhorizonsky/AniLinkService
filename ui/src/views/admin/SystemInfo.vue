<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'

const API_BASE = '/api'

const loading = ref(false)
const systemInfo = ref(null)

const fetchSystemInfo = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/system/info`)
    if (res.data?.data) {
      systemInfo.value = res.data.data
    }
  } catch (error) {
    console.error('获取系统信息失败:', error)
  } finally {
    loading.value = false
  }
}

const infoItems = computed(() => [
  { label: '数据库类型', icon: 'mdi-database', value: systemInfo.value?.dbType },
  { label: '数据库版本', icon: 'mdi-database', value: systemInfo.value?.dbVersion },
  { label: '主机名', icon: 'mdi-server-network', value: systemInfo.value?.hostname },
  { label: 'IP 地址', icon: 'mdi-ip', value: systemInfo.value?.serverIp },
  { label: '操作系统', icon: 'mdi-laptop', value: systemInfo.value?.osName },
  { label: '系统版本', icon: 'mdi-laptop', value: systemInfo.value?.osVersion },
  { label: '系统架构', icon: 'mdi-laptop', value: systemInfo.value?.osArch },
  { label: 'CPU 核数', icon: 'mdi-cpu-64-bit', value: systemInfo.value?.availableProcessors },
  { label: '最大内存', icon: 'mdi-memory', value: systemInfo.value?.maxMemoryMB + ' MB' },
  { label: 'Java 版本', icon: 'mdi-coffee', value: systemInfo.value?.javaVersion },
  { label: 'Java 供应商', icon: 'mdi-coffee', value: systemInfo.value?.javaVendor }
])

onMounted(() => {
  fetchSystemInfo()
})
</script>

<template>
  <div>
    <v-card v-if="loading" class="text-center pa-8">
      <v-progress-circular indeterminate color="primary" size="48" />
      <p class="mt-4 text-body-1">加载中...</p>
    </v-card>

    <v-card v-else-if="systemInfo">
      <v-card-title>
        <v-icon start>mdi-information</v-icon>
        系统信息
      </v-card-title>
      <v-card-text>
        <v-list>
          <v-list-item v-for="(item, index) in infoItems" :key="index">
            <template v-slot:prepend>
              <v-icon color="primary">{{ item.icon }}</v-icon>
            </template>
            <v-list-item-title class="font-weight-medium">{{ item.label }}</v-list-item-title>
            <v-list-item-subtitle>{{ item.value || '-' }}</v-list-item-subtitle>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>
  </div>
</template>

