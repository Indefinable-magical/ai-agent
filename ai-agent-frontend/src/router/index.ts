import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import StockMasterPage from '@/pages/StockMasterPage.vue'
import StockWorkbenchPage from '@/pages/StockWorkbenchPage.vue'
import SuperAgentPage from '@/pages/SuperAgentPage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserProfilePage from '@/pages/user/UserProfilePage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import StockPoolManagePage from '@/pages/admin/StockPoolManagePage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'HomePage',
      component: HomePage,
    },
    {
      path: '/stock-master',
      name: 'StockMasterPage',
      component: StockMasterPage,
      meta: {
        embedUserPanel: true,
        requireLogin: true,
      },
    },
    {
      path: '/stock-workbench',
      name: 'StockWorkbenchPage',
      component: StockWorkbenchPage,
      meta: {
        embedUserPanel: true,
        requireLogin: true,
        title: '自选股工作台',
      },
    },
    {
      path: '/super-agent',
      name: 'SuperAgentPage',
      component: SuperAgentPage,
      meta: {
        embedUserPanel: true,
        requireLogin: true,
      },
    },
    {
      path: '/user/login',
      name: 'UserLoginPage',
      component: UserLoginPage,
      meta: {
        hideUserPanel: true,
      },
    },
    {
      path: '/user/register',
      name: 'UserRegisterPage',
      component: UserRegisterPage,
      meta: {
        hideUserPanel: true,
      },
    },
    {
      path: '/user/profile',
      name: 'UserProfilePage',
      component: UserProfilePage,
      meta: {
        requireLogin: true,
        title: '用户资料',
      },
    },
    {
      path: '/admin/userManage',
      name: 'UserManagePage',
      component: UserManagePage,
    },
    {
      path: '/admin/stockPool',
      name: 'StockPoolManagePage',
      component: StockPoolManagePage,
    },
  ],
})

router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = String(to.meta.title)
  }
  next()
})

export default router
