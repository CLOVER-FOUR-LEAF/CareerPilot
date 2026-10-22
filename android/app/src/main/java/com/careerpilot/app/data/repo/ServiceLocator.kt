package com.careerpilot.app.data.repo

import android.content.Context
import com.careerpilot.app.core.TokenStore
import com.careerpilot.app.data.api.ApiClient

/** 简单的服务定位器，避免引入完整的依赖注入框架。 */
object ServiceLocator {

    lateinit var tokenStore: TokenStore
        private set

    lateinit var repository: CareerPilotRepository
        private set

    fun init(context: Context) {
        if (::tokenStore.isInitialized) return
        tokenStore = TokenStore(context)
        ApiClient.bind(tokenStore)
        repository = CareerPilotRepository(tokenStore)
        Session.restore(tokenStore)
    }
}
