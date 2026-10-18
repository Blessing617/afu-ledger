package com.afu.ledger.auto

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.afu.ledger.AfuApplication
import com.afu.ledger.BuildConfig
import com.afu.ledger.MainActivity
import com.afu.ledger.auto.keepalive.AutoLedgerKeepAlive
import com.afu.ledger.auto.ocr.OcrModelInstaller
import com.afu.ledger.auto.ocr.OcrTextChannel
import com.afu.ledger.auto.ocr.PpOcrEngine
import com.afu.ledger.auto.ocr.ScreenshotCapturer
import com.afu.ledger.auto.ocr.ScreenshotThrottle
import com.afu.ledger.auto.overlay.PaymentConfirmOverlay
import com.afu.ledger.data.AppContainer
import com.afu.ledger.data.prefs.ThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 自动记账的无障碍服务。
 *
 * 职责很窄：收事件 → 去抖 → 交给 [AutoRecognitionEngine] → 命中就交给
 * [PaymentConfirmController] 弹窗。文本采集、页面判定、弹窗入账都不在本类里。
 */
class AutoLedgerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val detector = PaymentPageDetector(
        debugPackageName = if (BuildConfig.DEBUG) BuildConfig.APPLICATION_ID else null,
    )

    /** 只用于把截图回调切回主线程，不依赖 androidx 的 ContextCompat。 */
    private val captureExecutor: Executor by lazy {
        val handler = Handler(Looper.getMainLooper())
        Executor { runnable -> handler.post(runnable) }
    }

    private val ocrSetupMutex = Mutex()
    private val ocrSetupStarted = AtomicBoolean(false)

    private var container: AppContainer? = null
    private var overlay: PaymentConfirmOverlay? = null
    private var confirmController: PaymentConfirmController? = null
    private var logger: AutoLedgerLogger? = null
    private var pendingJob: Job? = null
    private var ocrEngine: PpOcrEngine? = null

    @Volatile
    private var engine: AutoRecognitionEngine? = null

    @Volatile
    private var settings = AutoLedgerSettings()

    /** 上一次事件里微信页面的 class，用来识别「在同一 Activity 内翻页」。 */
    @Volatile
    private var lastEventClass: String? = null

    /** 累积的「用户换了页面」标记，等到真正判定时才消费掉。 */
    @Volatile
    private var pageChangePending: Boolean = false

    /**
     * 是否正在截图/识别。
     *
     * 用来避免「截完图又被新事件取消」：全屏截图与 OCR 的代价远高于一次去抖，
     * 已经付掉的成本不应该被丢弃，新事件只需合并进这次识别。
     */
    @Volatile
    private var captureInFlight: Boolean = false

    @Volatile
    private var themeSettings = ThemeSettings()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as AfuApplication
        container = app.container

        // 系统可能重复绑定；复用实例，别把还活着的悬浮窗引用丢了
        val activeOverlay = overlay ?: PaymentConfirmOverlay(this).also { overlay = it }
        // 预热组合环境：把首次弹出的 Compose 初始化开销挪到服务连接时
        activeOverlay.warmUp()
        val activeLogger = AutoLedgerLogger(
            recognitionLog = app.container.recognitionLog,
            enabledProvider = { settings.recognitionLogEnabled },
        )
        logger = activeLogger
        confirmController = PaymentConfirmController(
            service = this,
            container = app.container,
            overlay = activeOverlay,
            deduper = DetectionDeduper(),
            scope = scope,
            settingsProvider = { settings },
            themeProvider = { themeSettings },
            logger = activeLogger,
        )
        engine = AutoRecognitionEngine(this, detector, ocrChannel = null, ocrDebugPackageName = debugOcrPackage)

        scope.launch {
            var ocrWasEnabled = false
            app.container.autoLedgerSettings.settings.collect { current ->
                settings = current
                if (current.ocrEnabled && !ocrWasEnabled) ensureOcrChannel(app.container)
                ocrWasEnabled = current.ocrEnabled
            }
        }
        scope.launch {
            app.container.themePreferences.settings.collect { themeSettings = it }
        }

        // HyperOS 会冻结长期后台的进程；前台服务把进程优先级抬出 cached 区间
        startKeepAlive()
    }

    private fun startKeepAlive() {
        AutoLedgerKeepAlive.ensureChannel(this)
        val notification = AutoLedgerKeepAlive.buildNotification(this)
        val result = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    AutoLedgerKeepAlive.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(AutoLedgerKeepAlive.NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.e(TAG, "startForeground 失败: ${it.message}", it) }
        AutoLedgerKeepAlive.markForegroundRunning(result.isSuccess)
        Log.d(TAG, "keepAlive foreground=${result.isSuccess}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        // 心跳：任何事件都刷新「最近活跃时间」，用于让冻结可被发现
        AutoLedgerKeepAlive.onAccessibilityEvent()
        debugLog("event pkg=$packageName enabled=${settings.enabled} type=${event.eventType}")
        if (!settings.enabled) return
        if (!isSupportedPackage(packageName)) return

        val clicked = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        val kind = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> ScreenshotThrottle.EventKind.WINDOW_STATE_CHANGED
            else -> ScreenshotThrottle.EventKind.CONTENT_CHANGED
        }

        // 微信在同一个 Activity 内翻页（聊天 → 账单列表 → 账单详情）只发内容变化事件。
        // 两个信号用来识别「用户翻到了新页面」：
        //   1. 点击 —— 翻页必然伴随点击，这是最可靠的
        //   2. 页面类名变化 —— 但内容变化事件的 className 常常是 ViewGroup/LinearLayout
        //      这种控件类名，所以在同一页面里也会来回变，必须把 android.* 前缀的排掉
        val eventClass = event.className?.toString()?.takeIf { !it.startsWith("android.") }
        val classChanged = eventClass != null && eventClass != lastEventClass
        if (eventClass != null) lastEventClass = eventClass
        debugLog("event class=$eventClass clicked=$clicked classChanged=$classChanged")

        // 「换页」信号必须累积到真正判定那一刻：微信切页后会继续刷一串内容变化事件，
        // 每次都 cancel 上一个 debounce 任务，如果只保留最后一次事件的标记，
        // 点击带来的「换页」就被后面的内容事件冲掉了，结果又被 8 秒节流拦下（实测踩过）。
        if (clicked || classChanged) pageChangePending = true

        // 页面渲染往往连续触发多个事件，合并到一次识别；这同时是「页面已稳定」的判定。
        //
        // 去抖分两档：
        //  - 用户主动操作（点击/换页）：页面已经跳完了，没必要再等 600ms —— 用短档
        //  - 其余内容变化：可能是滚动/加载动画中，等长档避免无效判定
        //
        // 短档只对**用户明确动作**生效，所以不会让无效判定的次数明显上升。
        val debounceMillis = if (clicked || classChanged) {
            DEBOUNCE_AFTER_USER_ACTION_MILLIS
        } else {
            DEBOUNCE_MILLIS
        }

        // 已有截图任务在执行时不要取消它：截一张全屏再丢掉纯属浪费，
        // 而且会让用户在支付页多等一个完整周期。
        if (captureInFlight) {
            debugLog("capture in flight, coalesce event")
            return
        }

        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(debounceMillis)
            val pageChanged = pageChangePending
            pageChangePending = false
            inspect(packageName, kind, pageChanged)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pendingJob?.cancel()
        confirmController?.release()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        AutoLedgerKeepAlive.markForegroundRunning(false)
        runCatching { ocrEngine?.close() }
        scope.cancel()
        super.onDestroy()
    }

    private fun isSupportedPackage(packageName: String): Boolean =
        packageName in PaymentPageRules.supportedPackages ||
            (BuildConfig.DEBUG && packageName == this.packageName)

    /** debug 构建下允许自家模拟页走 OCR 通道，发布版为 null。 */
    private val debugOcrPackage: String?
        get() = if (BuildConfig.DEBUG) this.packageName else null

    private suspend fun inspect(
        packageName: String,
        kind: ScreenshotThrottle.EventKind,
        pageChanged: Boolean,
    ) {
        val recognition = engine ?: return
        captureInFlight = true
        try {
            val result = recognition.detect(
                packageName = packageName,
                kind = kind,
                screenInteractive = isScreenInteractive(),
                overlayShowing = confirmController?.isShowing == true,
                ocrAllowed = settings.ocrEnabled,
                pageChanged = pageChanged,
            )
            when (result) {
                is AutoRecognitionEngine.EngineResult.Hit -> {
                    // 分段耗时随命中一起进日志：排查「为什么慢」时不必再复现一次
                    logger?.log(
                        packageName = packageName,
                        outcome = "识别命中",
                        amountCents = result.draft.amount.cents,
                        merchant = result.draft.merchant,
                        detail = recognition.lastTiming?.summary(),
                    )
                    confirmController?.handleHit(result.draft)
                }
                is AutoRecognitionEngine.EngineResult.Missed -> {
                    debugLog("miss ${result.reason.name} ${result.detail.orEmpty()}")
                    logger?.logMiss(packageName, result.reason, result.detail)
                }
                AutoRecognitionEngine.EngineResult.Skipped ->
                    debugLog("throttled ${recognition.throttleDecision()}")
            }
            recordOcrOutcome(recognition, result)
        } finally {
            captureInFlight = false
        }
    }

    private fun recordOcrOutcome(
        recognition: AutoRecognitionEngine,
        result: AutoRecognitionEngine.EngineResult,
    ) {
        if (result is AutoRecognitionEngine.EngineResult.Skipped) return
        val status = container?.ocrStatus ?: return
        if (!settings.ocrEnabled || !status.state.value.available) return
        // 分段耗时附着在状态里，供设置页展示「慢在哪一段」
        val timing = recognition.lastTiming?.summary()
        val outcome = when (result) {
            is AutoRecognitionEngine.EngineResult.Hit -> "识别成功"
            is AutoRecognitionEngine.EngineResult.Missed -> result.reason.label
            AutoRecognitionEngine.EngineResult.Skipped -> return
        }
        status.recordOutcome(
            elapsedMillis = ocrEngine?.lastElapsedMillis ?: -1L,
            outcome = outcome,
            detail = timing,
        )
    }

    /** 用户打开 OCR 开关后才加载模型：不开启就不必付出内存与解压成本。 */
    private fun ensureOcrChannel(container: AppContainer) {
        if (!ocrSetupStarted.compareAndSet(false, true)) return
        scope.launch {
            val channel = withContext(Dispatchers.IO) { buildOcrChannel(container) }
            if (channel == null) ocrSetupStarted.set(false)
            else engine = AutoRecognitionEngine(this@AutoLedgerAccessibilityService, detector, channel, debugOcrPackage)
        }
    }

    private suspend fun buildOcrChannel(container: AppContainer): OcrTextChannel? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            container.ocrStatus.markUnavailable("系统版本低于 Android 11，不支持截图")
            return null
        }
        return ocrSetupMutex.withLock {
            val files = OcrModelInstaller.ensureInstalled(
                targetDir = File(filesDir, "ocr"),
                assetOpener = { name -> assets.open(name) },
            )
            if (files == null) {
                container.ocrStatus.markUnavailable("OCR 模型解压失败")
                return@withLock null
            }
            val ppEngine = PpOcrEngine(files)
            if (!ppEngine.warmUp()) {
                val reason = ppEngine.failureReason() ?: "OCR 模型加载失败"
                ppEngine.close()
                container.ocrStatus.markUnavailable(reason)
                return@withLock null
            }
            ocrEngine = ppEngine
            container.ocrStatus.markAvailable()
            debugLog("ocr channel ready")
            OcrTextChannel(
                capturer = ScreenshotCapturer(this, captureExecutor),
                engine = ppEngine,
                throttle = ScreenshotThrottle(),
            )
        }
    }

    private fun isScreenInteractive(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (power?.isInteractive != true) return false
        return keyguard?.isKeyguardLocked != true
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    /** 供悬浮窗里的「打开 App」回调使用。 */
    fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        confirmController?.hide()
    }

    private companion object {
        const val TAG = "AutoLedger"

        /** 内容变化事件的稳定判定时长：页面可能还在滚动/加载，多等一点避免无效判定。 */
        const val DEBOUNCE_MILLIS = 600L

        /**
         * 用户主动操作（点击 / 页面标识变化）后的稳定判定时长。
         *
         * 用户点了「完成」或跳进账单页，页面已经跳完，不需要再等 600ms。
         * 这是支付宝与微信识别延迟里最容易省掉的一段。
         */
        const val DEBOUNCE_AFTER_USER_ACTION_MILLIS = 180L
    }
}
