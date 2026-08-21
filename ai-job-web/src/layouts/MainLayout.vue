<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="brand">
        <span v-if="!collapsed">AI 智能求职平台</span>
        <span v-else>AI</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        router
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/resumes">
          <el-icon><Document /></el-icon>
          <span>我的简历</span>
        </el-menu-item>
        <el-menu-item index="/resumes">
          <el-icon><DataAnalysis /></el-icon>
          <span>AI简历分析</span>
        </el-menu-item>
        <el-menu-item index="/job-match">
          <el-icon><Suitcase /></el-icon>
          <span>岗位匹配</span>
        </el-menu-item>
        <el-menu-item index="/interviews">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI模拟面试</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button text @click="collapsed = !collapsed">
            <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </el-button>
          <span class="header-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <span class="username">{{ userStore.username }}</span>
          <el-button type="primary" link @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

const activeMenu = computed(() => {
  if (route.path.startsWith('/resumes')) return '/resumes'
  if (route.path.startsWith('/job-match')) return '/job-match'
  if (route.path.startsWith('/interviews')) return '/interviews'
  return route.path
})

const pageTitle = computed(() => {
  if (route.path.startsWith('/resumes/') && route.path.endsWith('/analysis')) {
    return 'AI 简历分析'
  }
  if (route.path.startsWith('/resumes')) return '我的简历'
  if (route.path.startsWith('/job-match/history')) return '匹配历史'
  if (route.path.startsWith('/job-match')) return 'AI 岗位匹配'
  if (route.path.endsWith('/report') && route.path.includes('/interviews/')) {
    return '面试报告'
  }
  if (route.path.match(/\/interviews\/\d+/)) return '面试进行中'
  if (route.path.startsWith('/interviews')) return 'AI 模拟面试'
  return '首页'
})

function onLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #1f2d3d;
  transition: width 0.2s ease;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding: 0 12px;
  white-space: nowrap;
  overflow: hidden;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--border);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.username {
  color: var(--text-secondary);
}

.main {
  background: var(--bg);
  min-height: calc(100vh - 60px);
}

@media (max-width: 768px) {
  .aside {
    position: fixed;
    z-index: 20;
    height: 100vh;
  }

  .main {
    padding: 12px;
  }

  .username {
    display: none;
  }
}
</style>
