package cc.ai.zz.feature.automation.executor

class AccessibilityGestureExecutor(
    private val accessibilityService: GestureAccessibilityService
) : GestureExecutor {
    override fun swipeUp() {
        accessibilityService.executeSwipeUp()
    }

    override fun click(x: Float, y: Float) {
        accessibilityService.executeClick(x, y)
    }

    override fun back() {
        accessibilityService.executeBack()
    }
}
