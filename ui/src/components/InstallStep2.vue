<script setup>

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
  <div class="step2-wrap">
    <v-row>
      <v-col cols="12" lg="7" class="pr-lg-6">
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
          required
          class="mb-2"
        />
        <v-textarea
          :model-value="form.siteDescription"
          @update:model-value="updateField('siteDescription', $event)"
          label="站点描述"
          placeholder="例如：番剧识别与弹幕播放服务"
          prepend-inner-icon="mdi-text"
          rows="3"
          required
          class="mb-2"
        />
        <v-text-field
          :model-value="form.siteUrl"
          @update:model-value="updateField('siteUrl', $event)"
          label="站点 URL"
          placeholder="例如：http://localhost:8081"
          prepend-inner-icon="mdi-link"
          required
        />
      </v-col>

      <v-col cols="12" lg="5" class="pl-lg-6">
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
          required
          class="mb-2"
        />
        <v-text-field
          :model-value="form.adminPassword"
          @update:model-value="updateField('adminPassword', $event)"
          label="管理员密码"
          type="password"
          placeholder="请输入管理员密码（至少6位）"
          prepend-inner-icon="mdi-lock"
          required
        />

        <v-divider class="my-5" />

        <h3 class="text-subtitle-1 mb-3 text-primary font-weight-medium">
          <v-icon start color="primary">mdi-shield-key</v-icon>
          弹弹PLAY开放平台配置
        </h3>
        <v-text-field
          :model-value="form.dandanAppId"
          @update:model-value="updateField('dandanAppId', $event)"
          label="Dandan App ID"
          placeholder="例如：your_app_id"
          prepend-inner-icon="mdi-account-key"
          class="mb-2"
        />
        <v-text-field
          :model-value="form.dandanAppSecret"
          @update:model-value="updateField('dandanAppSecret', $event)"
          label="Dandan App Secret"
          type="password"
          placeholder="例如：your_app_secret"
          prepend-inner-icon="mdi-lock"
          class="mb-2"
        />
        <v-text-field
          :model-value="form.dandanBaseUrl"
          @update:model-value="updateField('dandanBaseUrl', $event)"
          label="Dandan API 地址"
          placeholder="例如：https://api.dandanplay.net"
          prepend-inner-icon="mdi-web"
        />
      </v-col>
    </v-row>
  </div>
</template>

<style scoped>
.step2-wrap {
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  padding: 20px;
}

@media (max-width: 959px) {
  .step2-wrap {
    padding: 14px;
  }
}
</style>
