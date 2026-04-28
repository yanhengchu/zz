package cc.ai.zz.feature.home

import android.content.Intent
import android.net.Uri
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import cc.ai.zz.R
import cc.ai.zz.core.permission.NotificationPermissionController
import cc.ai.zz.feature.automation.command.GestureEvent
import cc.ai.zz.feature.automation.command.emit
import cc.ai.zz.feature.automation.executor.GestureAccessibilityService
import cc.ai.zz.feature.automation.service.GestureService
import cc.ai.zz.feature.overlay.manager.CoordinateLocatorFloatingWindowManager
import cc.ai.zz.feature.overlay.manager.FloatingWindowManager
import cc.ai.zz.feature.ocr.rule.OcrRuleRepository
import com.hjq.toast.Toaster

class MainActivity : ComponentActivity() {
    companion object {
        private const val SWIPE_UP_FIXED_PERIOD_MS = 3_000L
        private const val CLICK_BACK_FIXED_STAY_MS = 35_000L
    }

    private val ocrRuleRepository by lazy { OcrRuleRepository(this) }

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val projectionData = result.data
            if (result.resultCode != RESULT_OK || projectionData == null) {
                Toaster.show("未完成屏幕录制授权")
                return@registerForActivityResult
            }
            GestureEvent.ACT_START_OCR.emit {
                putExtra(GestureService.EXTRA_PROJECTION_RESULT_CODE, result.resultCode)
                putExtra(GestureService.EXTRA_PROJECTION_DATA, projectionData)
            }
        }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(NotificationPermissionController(this))
        overridePendingTransition(0, 0)
        setContentView(R.layout.activity_main)
        initViews()
        syncPageState()
        syncHomeInfo()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        syncPageState()
        syncCoordinateLocatorButton()
        syncHomeInfo()
    }

    private fun syncPageState() {
        // 当前设计里，“打开操作页”本身就是显式停止入口：
        // 用户回到操作页意味着准备重新配置或重新发起任务，因此这里会主动停止已有周期任务。
        // 现阶段项目没有单独的停止按钮，停止方式统一收敛在这个入口上。
        if (GestureService.hasRunningWork()) {
            GestureEvent.ACT_STOP.emit()
        }
    }

    private fun ensureAccessibilityReady(): Boolean {
        return !GestureAccessibilityService.checkAccessibilityServiceDisabled(this)
    }

    private fun ensureOverlayPermissionForFixedTask(): Boolean {
        if (FloatingWindowManager.hasOverlayPermission()) return true
        Toaster.show("请先开启悬浮窗权限以显示控制球")
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
        return false
    }

    private fun initViews() {
        findViewById<android.widget.Button>(R.id.btnStartSwipeUp).setOnClickListener {
            if (!ensureAccessibilityReady()) return@setOnClickListener
            if (!ensureOverlayPermissionForFixedTask()) return@setOnClickListener
            GestureEvent.ACT_SWIPE_UP.emit(startTime = SWIPE_UP_FIXED_PERIOD_MS)
        }
        findViewById<android.widget.Button>(R.id.btnStartClick).setOnClickListener {
            if (!ensureAccessibilityReady()) return@setOnClickListener
            if (!ensureOverlayPermissionForFixedTask()) return@setOnClickListener
            // 当前上滑和点击任务都不默认绑定 OCR；
            // 后续如果某个新场景需要边执行边识别，再在该场景入口上显式补 OCR 绑定逻辑。
            GestureEvent.ACT_CLICK_BACK.emit(startTime = CLICK_BACK_FIXED_STAY_MS)
        }
        findViewById<android.widget.Button>(R.id.btnStartOcr).setOnClickListener {
            if (!ensureAccessibilityReady()) return@setOnClickListener
            if (GestureService.isOcrActive) {
                return@setOnClickListener
            }
            requestProjectionThenStartOcr()
        }
        findViewById<android.widget.Button>(R.id.btnToggleCoordinateLocator).setOnClickListener {
            if (!ensureOverlayPermissionForFixedTask()) return@setOnClickListener
            if (CoordinateLocatorFloatingWindowManager.isShowing()) {
                CoordinateLocatorFloatingWindowManager.tryHide()
            } else {
                CoordinateLocatorFloatingWindowManager.tryShow()
            }
            syncCoordinateLocatorButton()
        }
        syncCoordinateLocatorButton()
    }

    private fun requestProjectionThenStartOcr() {
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun onResume() {
        super.onResume()
        syncCoordinateLocatorButton()
        syncHomeInfo()
    }

    private fun syncCoordinateLocatorButton() {
        val button = findViewById<android.widget.Button>(R.id.btnToggleCoordinateLocator)
        button.text = if (CoordinateLocatorFloatingWindowManager.isShowing()) {
            "隐藏坐标定位"
        } else {
            "定位屏幕坐标"
        }
    }

    private fun syncHomeInfo() {
        val info = ocrRuleRepository.getBundledRuleInfo()
        findViewById<android.widget.TextView>(R.id.tvInfoScreenValue).text =
            "${info.screenWidth}x${info.screenHeight}"
        findViewById<android.widget.TextView>(R.id.tvInfoRulesValue).text =
            when (val resolution = info.resolutionAssetPath) {
                null, info.defaultAssetPath -> "默认"
                else -> resolution
            }
    }
}
