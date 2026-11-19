package br.com.morefocus.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import br.com.morefocus.R
import br.com.morefocus.blocking.INSTAGRAM_PACKAGE_NAME

internal class FeedReelMask(context: Context, private val windowManager: WindowManager) {
    private var tabBounds: Rect? = null
    private val tabBlocker = View(context).apply {
        setBackgroundColor(Color.argb(104, 128, 128, 128))
        contentDescription = context.getString(R.string.reels_tab_blocked)
        isClickable = true
        isEnabled = false
    }
    private var areas: List<Rect> = emptyList()
    private val paint = Paint().apply { color = Color.BLACK }
    private val origin = IntArray(2)
    private val view = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            getLocationOnScreen(origin)
            canvas.save()
            canvas.translate(-origin[0].toFloat(), -origin[1].toFloat())
            for (area in areas) canvas.drawRect(area, paint)
            canvas.restore()
        }
    }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun update(root: AccessibilityNodeInfo?) {
        if (root?.packageName != INSTAGRAM_PACKAGE_NAME) {
            clear()
            return
        }
        val tab = root.nodes("clips_tab").firstOrNull {
            it.isVisibleToUser && it.isClickable
        }
        updateTabBlocker(tab?.bounds()?.takeUnless { it.isEmpty })
        val next = reelAreas(root)
        if (next == areas) return
        if (next.isEmpty()) {
            clearMedia()
            return
        }
        val needsWindow = areas.isEmpty()
        areas = next
        if (needsWindow) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { title = "MoreFocus Feed Reels" }
            windowManager.addView(view, params)
        }
        view.invalidate()
    }

    fun clear() {
        updateTabBlocker(null)
        clearMedia()
    }

    private fun updateTabBlocker(bounds: Rect?) {
        if (bounds == tabBounds) return
        val wasVisible = tabBounds != null
        tabBounds = bounds
        if (bounds == null) {
            windowManager.removeViewImmediate(tabBlocker)
            return
        }
        val params = WindowManager.LayoutParams(
            bounds.width(), bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = "MoreFocus Reels Tab"
            gravity = Gravity.TOP or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            x = bounds.left
            y = bounds.top
        }
        if (wasVisible) windowManager.updateViewLayout(tabBlocker, params)
        else windowManager.addView(tabBlocker, params)
    }

    private fun clearMedia() {
        if (areas.isEmpty()) return
        areas = emptyList()
        windowManager.removeViewImmediate(view)
    }

    private fun reelAreas(root: AccessibilityNodeInfo): List<Rect> {
        val feedBar = root.nodes("main_feed_action_bar").firstOrNull() ?: return emptyList()
        val feed = root.nodes("refreshable_container").firstOrNull { it.isVisibleToUser }
            ?: return emptyList()
        val viewport = feed.bounds()
        if (feedBar.isVisibleToUser) viewport.top = maxOf(viewport.top, feedBar.bounds().bottom)
        return root.nodes("media_group").mapNotNull { media ->
            // The inline Reel player contains media_group. Carousel slides use separate media IDs.
            val isInlinePlayer = media.parent?.viewIdResourceName == "$INSTAGRAM_PACKAGE_NAME:id/video_container"
            if (!media.isVisibleToUser || !isInlinePlayer) {
                return@mapNotNull null
            }
            val area = media.bounds()
            area.takeIf { it.intersect(viewport) }
        }
    }

    private fun AccessibilityNodeInfo.nodes(id: String): List<AccessibilityNodeInfo> =
        findAccessibilityNodeInfosByViewId("$INSTAGRAM_PACKAGE_NAME:id/$id")

    private fun AccessibilityNodeInfo.bounds() = Rect().also(::getBoundsInScreen)
}
