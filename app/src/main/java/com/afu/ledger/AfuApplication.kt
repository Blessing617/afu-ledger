package com.afu.ledger

import android.app.Application
import com.afu.ledger.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AfuApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 首次启动写入预置分类；已有数据时内部会跳过
        applicationScope.launch {
            container.categoryRepository.ensurePresetCategories()
        }
    }
}
