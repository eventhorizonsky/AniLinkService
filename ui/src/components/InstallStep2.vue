<script setup>
import { ref, defineExpose } from 'vue'

const props = defineProps({
  form: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:form'])

const updateField = (field, value) => {
  emit('update:form', { ...props.form, [field]: value })
}
</script>

<template>
  <div>
    <!-- 站点配置 -->
    <div class="mb-6">
      <h3 class="text-h6 mb-4 text-primary font-weight-medium">
        <v-icon start color="primary">mdi-web</v-icon>
        站点配置
      </h3>
      <v-text-field
        :model-value="form.siteName"
        @update:model-value="updateField('siteName', $event)"
        label="站点名称"
        placeholder="例如：AniLink Service"
        prepend-inner-icon="mdi-web"
        variant="outlined"
        color="primary"
        required
      />
      <v-textarea
        :model-value="form.siteDescription"
        @update:model-value="updateField('siteDescription', $event)"
        label="站点描述"
        placeholder="例如：番剧识别与弹幕播放服务"
        prepend-inner-icon="mdi-text"
        variant="outlined"
        color="primary"
        rows="2"
        required
      />
      <v-text-field
        :model-value="form.siteUrl"
        @update:model-value="updateField('siteUrl', $event)"
        label="站点 URL"
        placeholder="例如：http://localhost:8081"
        prepend-inner-icon="mdi-link"
        variant="outlined"
        color="primary"
        required
      />
    </div>

    <v-divider class="my-4" />

    <!-- 管理员账号 -->
    <div class="mb-6">
      <h3 class="text-h6 mb-4 text-primary font-weight-medium">
        <v-icon start color="primary">mdi-account-circle</v-icon>
        管理员账号
      </h3>
      <v-text-field
        :model-value="form.adminUsername"
        @update:model-value="updateField('adminUsername', $event)"
        label="管理员账号"
        placeholder="请输入管理员账号"
        prepend-inner-icon="mdi-account"
        variant="outlined"
        color="primary"
        required
      />
      <v-text-field
        :model-value="form.adminPassword"
        @update:model-value="updateField('adminPassword', $event)"
        label="管理员密码"
        type="password"
        placeholder="请输入管理员密码（至少6位）"
        prepend-inner-icon="mdi-lock"
        variant="outlined"
        color="primary"
        required
      />
    </div>
  </div>
</template>
