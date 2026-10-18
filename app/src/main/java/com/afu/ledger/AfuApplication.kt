package com.afu.ledger

import android.app.Application
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.afu.ledger.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

        // 预加载背景图：让首页首次组合直接命中图片缓存，
        // 避免「先空一段时间、再整张图突然出现」
        applicationScope.launch {
            val backgroundUri = container.themePreferences.settings.first().backgroundUri ?: return@launch
            runCatching {
                val request = ImageRequest.Builder(this@AfuApplication)
                    .data(backgroundUri)
                    .build()
                SingletonImageLoader.get(this@AfuApplication).enqueue(request)
            }
        }
    }
}
