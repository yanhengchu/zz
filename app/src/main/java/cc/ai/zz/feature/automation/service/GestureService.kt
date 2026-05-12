package cc.ai.zz.feature.automation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import cc.ai.zz.core.navigation.AppNavigator
import cc.ai.zz.feature.automation.command.GestureEvent
import cc.ai.zz.feature.automation.executor.AccessibilityGestureExecutor
import cc.ai.zz.feature.automation.executor.GestureAccessibilityService
import cc.ai.zz.feature.automation.plan.GesturePlanFactory
import cc.ai.zz.feature.overlay.manager.FloatingWindowManager
import cc.ai.zz.feature.ocr.coordinator.OcrCoordinator
import com.hjq.toast.Toaster

class GestureService : Service() {
    companion object {
        private const val TAG = "GestureService"
        private const val NOTIFICATION_CHANNEL_ID = "gesture_service_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_PROJECTION_RESULT_CODE = "extra_projection_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        @Volatile
        var isOcrActive = false
            private set
        @Volatile
        var isPeriodicTaskActive = false
            private set

        fun hasRunningWork(): Boolean = isOcrActive || isPeriodicTaskActive
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isInForeground = false
    private var currentForegroundServiceType: Int? = null
    private val ocrCoordinator by lazy {
        OcrCoordinator(
            onStopped = { isOcrActive = false },
            onReauthorizationRequired = { Toaster.show("OCR 需要重新授权") },
            onShowMessage = { Toaster.show(it) }
        )
    }
    private val periodicTaskRunner by lazy {
        PeriodicTaskRunner(
            executorProvider = {
                GestureAccessibilityService.instance?.let(::AccessibilityGestureExecutor)
            },
            floatingPositionProvider = { FloatingWindowManager.getMainPosition() },
            onShowMessage = { Toaster.show(it) },
            onShowCountdown = { FloatingWindowManager.updateTimeLimit(it) },
            onAccessibilityLost = {
                isPeriodicTaskActive = false
                FloatingWindowManager.tryHide()
                Toaster.show("无障碍不可用，已停止当前周期任务")
            },
            onPrepareOverlay = { FloatingWindowManager.tryShow(it) }
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "service created")
        // 兜住 startForegroundService 的时限要求：
        // 服务实例一创建就先进入最基础的 dataSync 前台态，避免后续命令分发、
        // intent 解析或 OCR 初始化分支出现波动时触发 ForegroundServiceDidNotStartInTimeException。
        ensureForegroundService(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resolveForegroundServiceType(): Int {
        return if (ocrCoordinator.isActive) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }

    private fun ensureForegroundService(serviceType: Int) {
        ensureNotificationChannel()
        if (isInForeground && currentForegroundServiceType == serviceType) return
        Log.d(TAG, "enter foreground serviceType=$serviceType")
        startForeground(NOTIFICATION_ID, createForegroundNotification(), serviceType)
        isInForeground = true
        currentForegroundServiceType = serviceType
    }

    private fun ensureNotificationChannel() {
        val channel =
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "手势服务", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "手势服务"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundNotification() =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("手势服务").setContentText("周期手势服务...").setContentIntent(createNotificationIntent())
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(false).build()

    private fun createNotificationIntent(): PendingIntent {
        val notificationIntent = AppNavigator.homeIntent()
        return PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val gestureEvent = intent?.getSerializableExtra("GestureEvent", GestureEvent::class.java)
        Log.d(TAG, "onStartCommand startId=$startId flags=$flags action=${gestureEvent?.action}")
        // GestureService 只认 GestureEvent.action 这一套分发模型。
        // 后续新增命令时也应继续沿用这一路由，不再额外引入 Intent.action 第二套协议。
        when (gestureEvent?.action) {
            GestureEvent.ACT_STOP -> stopCurrentWork()
            GestureEvent.ACT_BACK -> {
                ensureForegroundService(resolveForegroundServiceType())
                startBackGesture()
            }
            GestureEvent.ACT_SWIPE_UP -> {
                ensureForegroundService(resolveForegroundServiceType())
                startPeriodicSwipeUp(gestureEvent)
            }
            GestureEvent.ACT_CLICK_BACK -> {
                ensureForegroundService(resolveForegroundServiceType())
                startPeriodicClickBack(gestureEvent)
            }
            GestureEvent.ACT_START_OCR -> {
                // startForegroundService() 启动后必须先在时限内进入前台；
                // 等拿到有效授权并完成初始化后，再升级为 mediaProjection 类型。
                ensureForegroundService(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                startOcr(intent)
            }
            null -> {
                Log.w(TAG, "ignore start command because gestureEvent is missing")
            }
        }
        return START_NOT_STICKY
    }

    fun startBackGesture() {
        handler.post {
            GestureAccessibilityService.instance?.executeBack()
        }
    }

    private fun stopCurrentWork() {
        // ACT_STOP 当前收敛统一停止语义：
        // 停止当前周期任务和当前 OCR，但不主动降级或关闭服务宿主。
        Log.d(TAG, "stop current work ocrActive=$isOcrActive foregroundType=$currentForegroundServiceType")
        periodicTaskRunner.stop()
        isPeriodicTaskActive = false
        ocrCoordinator.stop()
        FloatingWindowManager.tryHide()
        isOcrActive = false
    }

    private fun startPeriodicSwipeUp(event: GestureEvent?) {
        event ?: return
        isPeriodicTaskActive = true
        periodicTaskRunner.start(event, GesturePlanFactory.buildSwipeUpPlan(event))
    }

    private fun startPeriodicClickBack(event: GestureEvent?) {
        event ?: return
        isPeriodicTaskActive = true
        periodicTaskRunner.start(event, GesturePlanFactory.buildClickBackPlan(event))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "service destroyed")
        handler.removeCallbacksAndMessages(null)
        periodicTaskRunner.release()
        ocrCoordinator.release()
        isOcrActive = false
        isPeriodicTaskActive = false
        isInForeground = false
        currentForegroundServiceType = null
    }

    private fun startOcr(intent: Intent) {
        Log.d(
            TAG,
            "start OCR requested hasResultCode=${intent.hasExtra(EXTRA_PROJECTION_RESULT_CODE)} hasData=${intent.hasExtra(EXTRA_PROJECTION_DATA)}"
        )
        val grant = intent.extractProjectionGrant() ?: run {
            Log.w(TAG, "start OCR aborted because projection grant is invalid")
            Toaster.show("OCR 授权数据无效")
            return
        }
        try {
            startOcrWithGrant(grant)
        } catch (error: SecurityException) {
            Log.e(TAG, "start OCR failed with SecurityException", error)
            handleOcrStartFailure("OCR 授权失败，请重新授权")
        } catch (error: Throwable) {
            Log.e(TAG, "start OCR failed during initialization", error)
            handleOcrStartFailure("OCR 初始化失败")
        }
    }

    private fun startOcrWithGrant(grant: ProjectionGrant) {
        Log.d(TAG, "start OCR with grant resultCode=${grant.resultCode}")
        ensureForegroundService(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        ocrCoordinator.start(grant.resultCode, grant.data)
        isOcrActive = true
        Log.d(TAG, "OCR started successfully")
    }

    private fun handleOcrStartFailure(message: String) {
        isOcrActive = false
        Log.w(TAG, "OCR start failed: $message")
        Toaster.show(message)
        stopServiceHost()
    }

    private fun stopServiceHost() {
        Log.d(TAG, "stop service host")
        stopForeground(STOP_FOREGROUND_REMOVE)
        isInForeground = false
        currentForegroundServiceType = null
        stopSelf()
    }

    private fun Intent.extractProjectionGrant(): ProjectionGrant? {
        val resultCode = getIntExtra(EXTRA_PROJECTION_RESULT_CODE, Int.MIN_VALUE)
        val data = getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        if (resultCode == Int.MIN_VALUE || data == null) return null
        return ProjectionGrant(resultCode = resultCode, data = data)
    }

    private data class ProjectionGrant(
        val resultCode: Int,
        val data: Intent
    )
}
