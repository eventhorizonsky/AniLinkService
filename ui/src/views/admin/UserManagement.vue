<script setup>
import { computed, onMounted, ref } from 'vue'
import { showAppMessage } from '../../utils/ui-feedback'
import { getUserRoles, getUsers, updateUser } from '../../api/system'
import { useServerPagination } from '../../composables/useServerPagination'

const saving = ref(false)
const users = ref([])
const roleOptions = ref([])
const keyword = ref('')

const {
  page,
  pageSize,
  totalElements,
  totalPages,
  loading,
  fetchPage
} = useServerPagination({
  pageSize: 10,
  fetchFn: async (query) => {
    try {
      const res = await getUsers({
        page: query.page,
        pageSize: query.pageSize,
        keyword: keyword.value?.trim() || undefined
      })

      if (res?.code === 200 && res?.data) {
        users.value = res.data.content || []

        const current = Number(res.data.currentPage || 0) + 1
        if (current !== page.value) {
          page.value = current
        }
      }
      return res
    } catch (error) {
      showAppMessage(error.response?.data?.msg || '获取用户列表失败', 'error')
      return null
    }
  }
})

const editDialog = ref(false)
const editForm = ref({
  id: null,
  username: '',
  email: '',
  isActive: true,
  roleCodeList: []
})

const roleCodeToName = computed(() => {
  const map = {}
  for (const role of roleOptions.value) {
    map[role.roleCode] = role.roleName || role.roleCode
  }
  return map
})

const roleItems = computed(() => roleOptions.value.map(role => ({
  title: role.roleName || role.roleCode,
  value: role.roleCode,
  subtitle: role.description || ''
})))

const fetchRoleOptions = async () => {
  try {
    const res = await getUserRoles()
    if (res?.code === 200) {
      roleOptions.value = res.data || []
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '获取角色列表失败', 'error')
  }
}

const searchUsers = async () => {
  page.value = 1
  await fetchPage()
}

const resetSearch = async () => {
  keyword.value = ''
  page.value = 1
  await fetchPage()
}

const onPageChange = async (newPage) => {
  page.value = newPage
  await fetchPage()
}

const openEditDialog = (user) => {
  editForm.value = {
    id: user.id,
    username: user.username || '',
    email: user.email || '',
    isActive: user.isActive !== false,
    roleCodeList: [...(user.roleCodeList || [])]
  }
  editDialog.value = true
}

const submitEdit = async () => {
  if (!editForm.value.username?.trim()) {
    showAppMessage('用户名不能为空', 'warning')
    return
  }

  if (!editForm.value.roleCodeList || editForm.value.roleCodeList.length === 0) {
    showAppMessage('请至少选择一个角色', 'warning')
    return
  }

  saving.value = true
  try {
    const payload = {
      username: editForm.value.username.trim(),
      email: editForm.value.email?.trim() || null,
      isActive: !!editForm.value.isActive,
      roleCodeList: editForm.value.roleCodeList
    }

    const res = await updateUser(editForm.value.id, payload)
    if (res?.code === 200) {
      showAppMessage('用户更新成功', 'success')
      editDialog.value = false
      await fetchPage()
    } else {
      showAppMessage(res?.msg || '用户更新失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '用户更新失败', 'error')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await fetchRoleOptions()
  await fetchPage()
})
</script>

<template>
  <div>
    <v-card class="mb-4">
      <v-card-title class="d-flex align-center ga-2">
        <i class="mdi mdi-account-search" style="color: var(--al-accent);"></i>
        用户搜索
      </v-card-title>
      <v-card-text>
        <v-row dense class="align-center">
          <v-col cols="12" md="8">
            <v-text-field
              v-model="keyword"
              label="搜索用户名或邮箱"
              variant="outlined"
              density="compact"
              prepend-inner-icon="mdi-magnify"
              hide-details
              clearable
              @keyup.enter="searchUsers"
            />
          </v-col>
          <v-col cols="12" md="4" class="d-flex ga-2 justify-md-end">
            <v-btn color="primary" variant="elevated" size="small" @click="searchUsers">
              <v-icon start>mdi-magnify</v-icon>
              搜索
            </v-btn>
            <v-btn color="grey" variant="text" size="small" @click="resetSearch">重置</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card>
      <v-card-title class="d-flex align-center justify-space-between">
        <span>
          <v-icon start>mdi-account-cog</v-icon>
          用户管理
        </span>
        <v-chip color="primary" variant="tonal">共 {{ totalElements }} 个用户</v-chip>
      </v-card-title>

      <v-card-text>
        <div class="d-none d-lg-block">
          <v-data-table
            :headers="[
              { title: 'ID', key: 'id', width: '80px' },
              { title: '用户名', key: 'username' },
              { title: '邮箱', key: 'email' },
              { title: '角色', key: 'roles', sortable: false },
              { title: '状态', key: 'isActive', width: '100px' },
              { title: '操作', key: 'actions', sortable: false, width: '120px' }
            ]"
            :items="users"
            :loading="loading"
            :items-per-page="pageSize"
            item-value="id"
            hide-default-footer
          >
            <template #item.email="{ item }">
              {{ item.email || '-' }}
            </template>

            <template #item.roles="{ item }">
              <div class="d-flex flex-wrap ga-1">
                <v-chip
                  v-for="roleCode in item.roleCodeList || []"
                  :key="`${item.id}-${roleCode}`"
                  size="small"
                  color="primary"
                  variant="outlined"
                >
                  {{ roleCodeToName[roleCode] || roleCode }}
                </v-chip>
                <span v-if="!item.roleCodeList || item.roleCodeList.length === 0">-</span>
              </div>
            </template>

            <template #item.isActive="{ item }">
              <v-chip :color="item.isActive ? 'success' : 'error'" size="small" variant="tonal">
                {{ item.isActive ? '启用' : '禁用' }}
              </v-chip>
            </template>

            <template #item.actions="{ item }">
              <v-btn size="small" color="primary" variant="tonal" @click="openEditDialog(item)">
                编辑
              </v-btn>
            </template>
          </v-data-table>
        </div>

        <div class="d-lg-none">
          <div v-if="loading" class="d-flex justify-center py-8">
            <v-progress-circular indeterminate color="primary" />
          </div>

          <template v-else>
            <v-card
              v-for="user in users"
              :key="user.id"
              class="mb-3"
              variant="outlined"
            >
              <v-card-item>
                <template #prepend>
                  <v-avatar color="primary" variant="tonal">
                    <v-icon>mdi-account</v-icon>
                  </v-avatar>
                </template>
                <v-card-title class="text-body-1 text-wrap">{{ user.username }}</v-card-title>
                <v-card-subtitle class="text-body-2">{{ user.email || '-' }}</v-card-subtitle>
                <template #append>
                  <v-chip :color="user.isActive ? 'success' : 'error'" size="small" variant="tonal">
                    {{ user.isActive ? '启用' : '禁用' }}
                  </v-chip>
                </template>
              </v-card-item>

              <v-card-text class="pt-2">
                <div class="d-flex flex-wrap ga-1">
                  <v-chip
                    v-for="roleCode in user.roleCodeList || []"
                    :key="`${user.id}-${roleCode}`"
                    size="small"
                    color="primary"
                    variant="outlined"
                  >
                    {{ roleCodeToName[roleCode] || roleCode }}
                  </v-chip>
                  <span v-if="!user.roleCodeList || user.roleCodeList.length === 0">-</span>
                </div>
              </v-card-text>

              <v-card-actions>
                <v-spacer />
                <v-btn size="small" color="primary" variant="tonal" @click="openEditDialog(user)">
                  <v-icon start>mdi-account-edit</v-icon>
                  编辑
                </v-btn>
              </v-card-actions>
            </v-card>

            <div v-if="users.length === 0" class="text-center text-medium-emphasis py-8">
              暂无用户
            </div>
          </template>
        </div>

        <div class="d-flex justify-end mt-4">
          <v-pagination
            v-model="page"
            :length="Math.max(totalPages, 1)"
            @update:model-value="onPageChange"
          />
        </div>
      </v-card-text>
    </v-card>

    <v-dialog v-model="editDialog" max-width="680">
      <v-card>
        <v-card-title>
          <v-icon start>mdi-account-edit</v-icon>
          编辑用户
        </v-card-title>

        <v-card-text class="pt-4">
          <v-row>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="editForm.username"
                label="用户名"
                variant="outlined"
                required
              />
            </v-col>

            <v-col cols="12" md="6">
              <v-text-field
                v-model="editForm.email"
                label="邮箱"
                variant="outlined"
                type="email"
              />
            </v-col>

            <v-col cols="12">
              <v-select
                v-model="editForm.roleCodeList"
                :items="roleItems"
                item-title="title"
                item-value="value"
                label="用户角色"
                variant="outlined"
                multiple
                chips
                closable-chips
                persistent-hint
                hint="可多选，至少保留一个角色"
              >
                <template #item="{ props, item }">
                  <v-list-item v-bind="props" :subtitle="item.raw.subtitle" />
                </template>
              </v-select>
            </v-col>

            <v-col cols="12">
              <v-switch
                v-model="editForm.isActive"
                label="账号启用"
                color="primary"
                inset
              />
            </v-col>
          </v-row>
        </v-card-text>

        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="editDialog = false">取消</v-btn>
          <v-btn color="primary" :loading="saving" @click="submitEdit">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
