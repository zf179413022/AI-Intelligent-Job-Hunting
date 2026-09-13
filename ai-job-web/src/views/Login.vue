<template>
  <div class="login-page">
    <div class="login-panel page-card">
      <h1>AI 智能求职平台</h1>
      <p class="subtitle">登录后管理简历并进行 AI 分析</p>

      <el-form :model="form" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    await userStore.login({
      username: form.username,
      password: form.password,
    })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.push(redirect)
  } catch {
    // 错误已在 axios 拦截器提示；后端 RuntimeException 可能是纯文本
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at top right, rgba(64, 158, 255, 0.18), transparent 40%),
    linear-gradient(160deg, #f5f7fa 0%, #e8eef8 100%);
}

.login-panel {
  width: 100%;
  max-width: 420px;
}

h1 {
  margin: 0 0 8px;
  font-size: 28px;
  color: #1f2d3d;
}

.subtitle {
  margin: 0 0 24px;
  color: var(--text-secondary);
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}
</style>
