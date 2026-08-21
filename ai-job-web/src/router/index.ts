import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/Dashboard.vue'),
        },
        {
          path: 'resumes',
          name: 'resumes',
          component: () => import('@/views/ResumeList.vue'),
        },
        {
          path: 'resumes/:id/analysis',
          name: 'resume-analysis',
          component: () => import('@/views/ResumeAnalysis.vue'),
        },
        {
          path: 'jobs',
          redirect: '/job-match',
        },
        {
          path: 'job-match',
          name: 'job-match',
          component: () => import('@/views/JobMatch.vue'),
        },
        {
          path: 'job-match/history',
          name: 'job-match-history',
          component: () => import('@/views/JobMatchHistory.vue'),
        },
        {
          path: 'interviews',
          name: 'interviews',
          component: () => import('@/views/InterviewList.vue'),
        },
        {
          path: 'interviews/:id/report',
          name: 'interview-report',
          component: () => import('@/views/InterviewReport.vue'),
        },
        {
          path: 'interviews/:id',
          name: 'interview-room',
          component: () => import('@/views/InterviewRoom.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = getToken()
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return { path: '/dashboard' }
  }
  return true
})

export default router
