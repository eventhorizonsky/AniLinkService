<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const API_BASE = '/api'

const systemInfo = ref(null)
const loading = ref(true)
const errorMessage = ref('')

const fetchSystemInfo = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await axios.get(`${API_BASE}/init/system-info`)
    if (res.data?.code === 200) {
      systemInfo.value = res.data.data
    } else {
      errorMessage.value = res.data?.msg || '获取系统信息失败'
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.msg || '获取系统信息失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const getMemoryUsedPercent = () => {
  if (!systemInfo.value || !systemInfo.value.totalMemoryMB || !systemInfo.value.maxMemoryMB) return 0
  return ((systemInfo.value.totalMemoryMB / systemInfo.value.maxMemoryMB) * 100).toFixed(1)
}

const formatUptime = (seconds) => {
  if (!seconds) return '-'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours > 24) {
    const days = Math.floor(hours / 24)
    return `${days}天 ${hours % 24}小时`
  }
  return `${hours}小时 ${minutes}分钟`
}

onMounted(() => {
  fetchSystemInfo()
})

defineExpose({
  systemInfo
})
</script>

<template>
  <div>
    <h3 class="text-h6 mb-4 text-primary font-weight-medium">
      <v-icon start color="primary">mdi-information</v-icon>
      系统信息
    </h3>

    <v-alert v-if="errorMessage" type="error" class="mb-4" closable>
      {{ errorMessage }}
    </v-alert>

    <v-skeleton-loader v-if="loading" type="card" height="400" />

    <div v-else-if="systemInfo" class="system-info-container">
      <v-row>
        <!-- 数据库信息 -->
        <v-col cols="12" sm="6">
          <v-card class="mb-3">
            <v-card-text>
              <div class="d-flex align-center mb-2">
                <v-icon color="primary" class="mr-2">mdi-database</v-icon>
                <span class="font-weight-medium">数据库</span>
              </div>
              <div class="info-item">
                <span class="label">类型：</span>
                <v-chip size="small" color="primary">{{ systemInfo.dbType }}</v-chip>
              </div>
              <div class="info-item">
                <span class="label">版本：</span>
                <span class="value">{{ systemInfo.dbVersion }}</span>
              </div>
            </v-card-text>
          </v-card>
        </v-col>

        <!-- 服务器信息 -->
        <v-col cols="12" sm="6">
          <v-card class="mb-3">
            <v-card-text>
              <div class="d-flex align-center mb-2">
                <v-icon color="success" class="mr-2">mdi-server-network</v-icon>
                <span class="font-weight-medium">服务器</span>
              </div>
              <div class="info-item">
                <span class="label">主机名：</span>
                <span class="value">{{ systemInfo.hostname }}</span>
              </div>
              <div class="info-item">
                <span class="label">IP 地址：</span>
                <span class="value">{{ systemInfo.serverIp }}</span>
              </div>
            </v-card-text>
          </v-card>
        </v-col>

        <!-- 操作系统信息 -->
        <v-col cols="12" sm="6">
          <v-card class="mb-3">
            <v-card-text>
              <div class="d-flex align-center mb-2">
                <v-icon color="info" class="mr-2">mdi-laptop</v-icon>
                <span class="font-weight-medium">操作系统</span>
              </div>
              <div class="info-item">
                <span class="label">名称：</span>
                <span class="value">{{ systemInfo.osName }}</span>
              </div>
              <div class="info-item">
                <span class="label">版本：</span>
                <span class="value">{{ systemInfo.osVersion }}</span>
              </div>
              <div class="info-item">
                <span class="label">架构：</span>
                <span class="value">{{ systemInfo.osArch }}</span>
              </div>
            </v-card-text>
          </v-card>
        </v-col>

        <!-- 硬件信息 -->
        <v-col cols="12" sm="6">
          <v-card class="mb-3">
            <v-card-text>
              <div class="d-flex align-center mb-2">
                <v-icon color="warning" class="mr-2">mdi-cpu-64-bit</v-icon>
                <span class="font-weight-medium">硬件配置</span>
              </div>
              <div class="info-item">
                <span class="label">CPU 核数：</span>
                <span class="value">{{ systemInfo.availableProcessors }} 核</span>
              </div>
              <div class="info-item">
                <span class="label">最大内存：</span>
                <span class="value">{{ systemInfo.maxMemoryMB }} MB</span>
              </div>
              <div class="info-item">
                <span class="label">已用内存：</span>
                <div class="d-flex align-center">
                  <v-progress-linear
                    :model-value="getMemoryUsedPercent()"
                    color="primary"
                    height="6"
                    rounded
                    class="flex-grow-1 mr-2"
                  />
                  <span class="value">{{ systemInfo.totalMemoryMB }} MB</span>
                </div>
              </div>
            </v-card-text>
          </v-card>
        </v-col>

        <!-- Java 信息 -->
        <v-col cols="12" sm="6">
          <v-card class="mb-3">
            <v-card-text>
              <div class="d-flex align-center mb-2">
                <v-icon color="orange" class="mr-2">mdi-coffee</v-icon>
                <span class="font-weight-medium">Java 环境</span>
              </div>
              <div class="info-item">
                <span class="label">版本：</span>
                <span class="value">{{ systemInfo.javaVersion }}</span>
              </div>
              <div class="info-item">
                <span class="label">供应商：</span>
                <span class="value">{{ systemInfo.javaVendor }}</span>
              </div>
              <div class="info-item">
                <span class="label">运行时间：</span>
                <span class="value">{{ formatUptime(systemInfo.uptimeSeconds) }}</span>
              </div>
            </v-card-text>
          </v-card>
        </v-col>

        <!-- Liquibase 信息 -->
        <v-col cols="12" sm="6">
          <v-card class="mb-3">
            <v-card-text>
              <div class="d-flex align-center mb-2">
                <v-icon :color="systemInfo.liquibaseEnabled ? 'success' : 'grey'" class="mr-2">
                  mdi-database-sync
                </v-icon>
                <span class="font-weight-medium">数据库迁移</span>
              </div>
              <div class="info-item">
                <span class="label">状态：</span>
                <v-chip 
                  size="small" 
                  :color="systemInfo.liquibaseEnabled ? 'success' : 'error'"
                >
                  {{ systemInfo.liquibaseEnabled ? '已启用' : '未启用' }}
                </v-chip>
              </div>
              <div v-if="systemInfo.liquibaseEnabled" class="info-item">
                <span class="label">初始化状态：</span>
                <v-chip 
                  size="small" 
                  :color="systemInfo.liquibaseInitialized ? 'success' : 'warning'"
                >
                  {{ systemInfo.liquibaseInitialized ? '已完成' : '未完成' }}
                </v-chip>
              </div>
              <div v-if="systemInfo.liquibaseEnabled" class="info-item">
                <span class="label">变更集数量：</span>
                <span class="value">{{ systemInfo.liquibaseChangeSets }}</span>
              </div>
              <div v-if="systemInfo.liquibaseEnabled && systemInfo.liquibaseLastExecuted" class="info-item">
                <span class="label">最后执行：</span>
                <span class="value text-caption">{{ systemInfo.liquibaseLastExecuted }}</span>
              </div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>
    </div>
  </div>
</template>

<style scoped>
.info-item {
  display: flex;
  align-items: center;
  padding: 4px 0;
  font-size: 14px;
}

.info-item .label {
  color: rgba(0, 0, 0, 0.6);
  min-width: 80px;
}

.info-item .value {
  font-weight: 500;
  color: rgba(0, 0, 0, 0.87);
}
</style>
