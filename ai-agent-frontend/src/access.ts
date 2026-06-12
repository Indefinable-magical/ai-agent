import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router/index'
import { setInterfaceTheme } from '@/composables/useInterfaceTheme'

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验
 */
router.beforeEach(async (to, from, next) => {
    const loginUserStore = useLoginUserStore()
    let loginUser = loginUserStore.loginUser
    // 确保页面刷新，首次加载时，能够等后端返回用户信息后再校验权限
    if (firstFetchLoginUser) {
        await loginUserStore.fetchLoginUser()
        loginUser = loginUserStore.loginUser
        if (loginUser?.id) {
            const preference = await loginUserStore.fetchUserPreference()
            setInterfaceTheme(preference.theme)
        }
        firstFetchLoginUser = false
    }
    const toUrl = to.fullPath
    if (to.meta.requireLogin && !loginUser?.id) {
        message.warning('请先登录，登录后会回到刚才的页面')
        next({
            path: '/user/login',
            query: {
                redirect: to.fullPath,
            },
        })
        return
    }
    if (toUrl.startsWith('/admin')) {
        if (!loginUser?.id) {
            message.warning('请先登录，登录后会继续前往管理后台')
            next({
                path: '/user/login',
                query: {
                    redirect: to.fullPath,
                },
            })
            return
        }
        if (loginUser.userRole !== 'admin') {
            message.error('没有权限')
            next('/')
            return
        }
    }
    next()
})
