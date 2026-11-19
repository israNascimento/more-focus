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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import br.com.morefocus.R
import br.com.morefocus.blocking.INSTAGRAM_PACKAGE_NAME
import br.com.morefocus.blocking.YOUTUBE_PACKAGE_NAME
import br.com.morefocus.blocking.isYouTubeFeedShort
import kotlin.math.abs

internal class FeedVideoMask(private val context: Context, private val windowManager: WindowManager) {
    private var tabBounds: Rect? = null
    private val tabBlocker = View(context).apply {
        setBackgroundColor(Color.argb(104, 128, 128, 128))
        isClickable = true
        isEnabled = false
    }
    private val covers = mutableListOf<VideoCover>()

    fun update(root: AccessibilityNodeInfo, hideVideos: Boolean, blockTaps: Boolean) {
        when (root.packageName?.toString()) {
            INSTAGRAM_PACKAGE_NAME -> updateInstagram(root, hideVideos, blockTaps)
            YOUTUBE_PACKAGE_NAME -> updateYouTube(root, hideVideos, blockTaps)
            else -> clear()
        }
    }

    private fun updateInstagram(root: AccessibilityNodeInfo, hideReels: Boolean, blockTaps: Boolean) {
        val tab = if (hideReels) root.nodes("clips_tab").firstOrNull {
            it.isVisibleToUser && it.isClickable
        } else null
        updateTabBlocker(tab?.bounds()?.takeUnless { it.isEmpty }, R.string.reels_tab_blocked)

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

    private fun updateYouTube(root: AccessibilityNodeInfo, hideShorts: Boolean, blockTaps: Boolean) {
        val pivot = root.nodes("pivot_bar").firstOrNull { it.isVisibleToUser }
        val tab = pivot?.findAccessibilityNodeInfosByText("Shorts")?.firstOrNull {
            it.isVisibleToUser && it.isClickable && it.contentDescription?.toString() == "Shorts"
        }
        updateTabBlocker(tab?.takeIf { hideShorts }?.bounds(), R.string.shorts_tab_blocked)

        // The bottom bar disappears on upward scroll. Home's browse pager stays in the window.
        val browse = root.nodes("browse_fragment_layout_coordinator_layout").firstOrNull { it.isVisibleToUser }
        val pager = browse?.nodes("view_pager")?.firstOrNull { it.isVisibleToUser }
        val inFeed = pivot == null || tab?.parent?.getChild(0)?.isSelected == true
        val feed = if (inFeed) pager?.nodes("results")?.firstOrNull {
            it.isVisibleToUser && !it.bounds().isEmpty
        } else null
        var count = 0
        if (feed != null) {
            val viewport = feed.bounds()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = windowManager.currentWindowMetrics
                val content = Rect(metrics.bounds)
                content.inset(metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                ))
                if (!viewport.intersect(content)) viewport.setEmpty()
            } else {
                val statusBar = root.nodes("global_status_bar_view").firstOrNull { it.isVisibleToUser }
                if (statusBar != null) viewport.top = maxOf(viewport.top, statusBar.bounds().bottom)
            }
            // YouTube's virtual card buttons expose their playback action in the description,
            // but its accessibility provider does not return them in a text search.
            val pending = ArrayDeque<AccessibilityNodeInfo>()
            pending.add(feed)
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                val bounds = node.bounds()
                if (!node.isVisibleToUser || !bounds.intersect(viewport)) continue
                if (node.isClickable && isYouTubeFeedShort(node.contentDescription)) {
                    updateCover(count++, node, bounds, hideShorts, hideShorts || blockTaps)
                } else {
                    for (index in node.childCount - 1 downTo 0) node.getChild(index)?.let(pending::addLast)
                }
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
        index: Int, media: AccessibilityNodeInfo, bounds: Rect, hideVideos: Boolean, blockTaps: Boolean
    ) {
        val cover = covers.getOrNull(index) ?: VideoCover(context).also(covers::add)
        val wasTouchable = cover.media != null
        cover.media = media.takeIf { blockTaps }
        if (hideVideos != (cover.drawable != null)) {
            cover.setBackgroundResource(if (hideVideos) R.color.feed_cover_surface else 0)
            cover.setImageResource(if (hideVideos) R.mipmap.ic_launcher else 0)
        }
        if (bounds == cover.bounds && wasTouchable == blockTaps) return
        val params = overlayParams(bounds, "MoreFocus Feed Video")
        if (!blockTaps) params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (cover.bounds == null) windowManager.addView(cover, params)
        else windowManager.updateViewLayout(cover, params)
        cover.bounds = bounds
    }

    private fun updateTabBlocker(bounds: Rect?, description: Int = 0) {
        if (bounds != null) tabBlocker.contentDescription = context.getString(description)
        if (bounds == tabBounds) return
        val wasVisible = tabBounds != null
        tabBounds = bounds
        if (bounds == null) {
            windowManager.removeViewImmediate(tabBlocker)
            return
        }
        val params = overlayParams(bounds, "MoreFocus Video Tab")
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
        findAccessibilityNodeInfosByViewId("${packageName}:id/$id")

    private fun AccessibilityNodeInfo.bounds() = Rect().also(::getBoundsInScreen)

    private class VideoCover(context: Context) : ImageView(context) {
        var bounds: Rect? = null
        var media: AccessibilityNodeInfo? = null
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var scrolled = false

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ScaleType.CENTER_INSIDE
            val inset = (12 * resources.displayMetrics.density).toInt()
            setPadding(inset, inset, inset, inset)
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
