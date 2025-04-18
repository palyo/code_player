package core.rocket.player.widgets.controls

import android.content.res.Resources
import android.view.GestureDetector
import android.view.MotionEvent

class GestureHandler(
    private val onSingleTap: () -> Unit = {},
    private val onDoubleTapAction: (e: MotionEvent) -> Unit = {},
    private val onSwipeVolumeUp: () -> Unit = {},
    private val onSwipeVolumeDown: () -> Unit = {},
    private val onSwipeBrightnessUp: () -> Unit = {},
    private val onSwipeBrightnessDown: () -> Unit = {},
) : GestureDetector.SimpleOnGestureListener() {

    private var screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        onSingleTap()
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        onDoubleTapAction(e)
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null || e2 == null) return false
        val deltaY = e1.y - e2.y
        val absDeltaY = kotlin.math.abs(deltaY)

        if (absDeltaY < 50) return false

        val isLeftSide = e1.x < screenWidth / 2

        if (deltaY > 0) {
            if (isLeftSide) onSwipeBrightnessUp() else onSwipeVolumeUp()
        } else {
            if (isLeftSide) onSwipeBrightnessDown() else onSwipeVolumeDown()
        }

        return true
    }
}
