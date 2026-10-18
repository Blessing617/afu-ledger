package com.afu.ledger.auto.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * 给悬浮窗里的 ComposeView 提供一个最小的生命周期宿主。
 *
 * AccessibilityService 本身不是 LifecycleOwner，Compose 又必须要一个；
 * 与其引入第三方 lifecycle-service 库，不如手写这几十行。
 */
class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry

    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore get() = store

    fun attach() {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun detach() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
