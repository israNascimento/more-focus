package br.com.morefocus.accessibility

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import br.com.morefocus.R
import br.com.morefocus.blocking.INSTAGRAM_PACKAGE_NAME
import kotlin.math.abs

internal class FeedReelMask(private val context: Context, private val windowManager: WindowManager) {
    private var tabBounds: Rect? = null
    private val tabBlocker = View(context).apply {
        setBackgroundColor(Color.argb(104, 128, 128, 128))
        contentDescription = context.getString(R.string.reels_tab_blocked)
        isClickable = true
        isEnabled = false
    }
    private val covers = mutableListOf<ReelCover>()

    fun update(root: AccessibilityNodeInfo, hideReels: Boolean, blockTaps: Boolean) {
        val tab = if (hideReels) root.nodes("clips_tab").firstOrNull {
            it.isVisibleToUser && it.isClickable
        } else null
        updateTabBlocker(tab?.bounds()?.takeUnless { it.isEmpty })

        val feedBar = root.nodes("main_feed_action_bar").firstOrNull()
        val feed = root.nodes("refreshable_container").firstOrNull { it.isVisibleToUser }
        var count = 0
        if (feedBar != null && feed != null) {
            val viewport = feed.bounds()
            if (feedBar.isVisibleToUser) viewport.top = maxOf(viewport.top, feedBar.bounds().bottom)
            for (media in root.nodes("media_group")) {
                // The inline Reel player contains media_group. Carousel slides use separate media IDs.
                if (!media.isVisibleToUser || media.parent?.viewIdResourceName !=
                    "$INSTAGRAM_PACKAGE_NAME:id/video_container"
                ) continue
                val bounds = media.bounds()
                if (!bounds.intersect(viewport)) continue
                updateCover(count++, media, bounds, hideReels, blockTaps)
            }
        }
        removeExtraCovers(count)
    }

    fun clear() {
        updateTabBlocker(null)
        removeExtraCovers(0)
    }

    private fun removeExtraCovers(count: Int) {
        while (covers.size > count) windowManager.removeViewImmediate(covers.removeAt(covers.lastIndex))
    }

    private fun updateCover(
        index: Int, media: AccessibilityNodeInfo, bounds: Rect, hideReels: Boolean, blockTaps: Boolean
    ) {
        val cover = covers.getOrNull(index) ?: ReelCover(context).also(covers::add)
        val wasTouchable = cover.media != null
        cover.media = media.takeIf { blockTaps }
        val color = if (hideReels) Color.BLACK else Color.TRANSPARENT
        if (cover.color != color) {
            cover.setBackgroundColor(color)
            cover.color = color
        }
        if (bounds == cover.bounds && wasTouchable == blockTaps) return
        val params = overlayParams(bounds, "MoreFocus Feed Reel")
        if (!blockTaps) params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (cover.bounds == null) windowManager.addView(cover, params)
        else windowManager.updateViewLayout(cover, params)
        cover.bounds = bounds
    }

    private fun updateTabBlocker(bounds: Rect?) {
        if (bounds == tabBounds) return
        val wasVisible = tabBounds != null
        tabBounds = bounds
        if (bounds == null) {
            windowManager.removeViewImmediate(tabBlocker)
            return
        }
        val params = overlayParams(bounds, "MoreFocus Reels Tab")
        if (wasVisible) windowManager.updateViewLayout(tabBlocker, params)
        else windowManager.addView(tabBlocker, params)
    }

    private fun overlayParams(bounds: Rect, windowTitle: String) = WindowManager.LayoutParams(
        bounds.width(), bounds.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        title = windowTitle
        gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        x = bounds.left
        y = bounds.top
    }

    private fun AccessibilityNodeInfo.nodes(id: String): List<AccessibilityNodeInfo> =
        findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE_NAME:id/$id")

    private fun AccessibilityNodeInfo.bounds() = Rect().also(::getBoundsInScreen)

    private class ReelCover(context: Context) : View(context) {
        var bounds: Rect? = null
        var color = Color.TRANSPARENT
        var media: AccessibilityNodeInfo? = null
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var scrolled = false

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    scrolled = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    if (!scrolled && abs(dy) > touchSlop && abs(dy) > abs(event.rawX - downX)) {
                        scrolled = true
                        val action = if (dy < 0) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        var node = media?.parent
                        while (node != null) {
                            if (node.isScrollable && node.performAction(action)) break
                            node = node.parent
                        }
                    }
                }
            }
            return true
        }
    }
}
