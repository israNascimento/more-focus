# MoreFocus - Play Console Accessibility API Declaration

## Core functionality

MoreFocus is a focus app that helps users set local daily limits for selected apps and keep a shared daily limit for vertical videos across Instagram and Facebook Reels, YouTube Shorts, the full-screen video viewer on X, the TikTok For You feed, and the Kwai Home video feed. The accessibility service identifies the app in the foreground, counts local usage time for user-selected app rules, detects these video viewers, and shows a user-controlled focus warning after a chosen daily limit is reached. An optional setting covers inline Reels in the Instagram home feed with a neutral MoreFocus overlay that allows scrolling and blocks taps on the Reels tab in the Instagram bottom navigation bar. A separate YouTube setting covers Shorts cards on Home and blocks taps on the cards and the Shorts tab. Vertical swipes on a covered card scroll the feed. Users can also enable hard blocking for vertical videos in General preferences. Once the shared daily limit is reached, MoreFocus automatically goes back when it detects a viewer with an enabled rule on Instagram, Facebook, YouTube, or X. On TikTok and Kwai it opens the Profile tab to leave the feed when available, falling back to Back. It also intercepts taps on feed Reels or Shorts when the corresponding video rule is enabled.

Users can also start a 30-minute focus session from the Usage tab. During the session, the service exits any recognized short-video viewer with an enabled rule using the same Back or Profile action. Blocked viewing time does not use the daily allowance. Whole-app limits still apply, and the usual daily video rules resume at the displayed end time.

## Accessibility API usage

MoreFocus uses `AccessibilityService` to identify the foreground app package name for user-selected app limits. For the vertical videos rules, it also reads the active Instagram, Facebook, YouTube, X, TikTok, and Kwai window hierarchies, including visible text, content descriptions, view identifiers, package name, and class name.

The app uses this data only on the device to decide whether selected app usage or vertical video viewing time should be counted and whether to show the MoreFocus warning overlay. When feed masking is enabled, the service also reads the bounds of the Reels or Shorts cards and their navigation tab to position the feed mask and the touch blocker. Hard blocking uses the same card bounds to intercept taps and forwards vertical swipes on a blocked card to the feed through accessibility scroll actions. With hard blocking enabled, the service performs the Android Back action to leave an enabled viewer over the limit, or selects Profile on TikTok or Kwai. It checks the selected TikTok tab and the visible feed player to count time on For You. On Kwai, it checks the selected Home tab and the visible video pager. Messages and Profile stay outside the shared limit. On Facebook, the service checks the size and accessibility label of the open Reel, or the full-screen replay pager and its comment controls. Browsing inline videos, Groups, or Marketplace does not use the shared budget. On X, videos playing inline in the timeline remain available and do not consume the shared budget. It does not capture screenshots, record video, use OCR, upload screen data, sell data, share data, or use the data for ads or analytics.

## User disclosure and consent

Before opening Android Accessibility settings, the app displays an in-app disclosure explaining that MoreFocus uses accessibility events to identify the foreground app, reads Instagram and YouTube screen text, descriptions, and identifiers to detect Reels and Shorts, identifies full-screen Reels on Facebook and the full-screen video viewer on X, recognizes the selected For You tab and player on TikTok, and checks the Home tab and video pager on Kwai. The disclosure describes how a user-started focus session exits enabled viewers even with time left. It also explains how MoreFocus uses the positions of Reels and Shorts in their feeds when feed masking or hard blocking is enabled and when it automatically leaves the viewer using Back or the Profile tab on TikTok or Kwai. Users must manually enable the MoreFocus service in Android settings.

The app does not set `isAccessibilityTool=true` because its primary purpose is focus/productivity, not disability support.

## User controls

Users can disable MoreFocus globally, enable or disable the Instagram Reels, Facebook Reels, YouTube Shorts, X video, TikTok For You, and Kwai feed rules independently, choose their shared daily vertical videos limit, add or remove selected app limits, enable or disable each app limit, change each app limit duration, turn off the accessibility service in Android settings, tap "Sair" on the warning overlay, or tap "Ignorar 1 min" to pause warnings temporarily.

## Suggested Play Store description

MoreFocus helps you stay focused by showing a local warning after you reach your daily limit for selected apps or vertical videos across Instagram and Facebook Reels, YouTube Shorts, the full-screen video viewer on X, the TikTok For You feed, and the Kwai Home video feed.

To apply limits, MoreFocus uses Android Accessibility access after your explicit consent. The app identifies the foreground app, reads visible Instagram and YouTube screen information to detect Reels and Shorts, checks the Facebook and X screens for their respective full-screen viewers, checks the selected TikTok tab and player for For You, and recognizes the video feed on the selected Kwai Home tab. It does not capture images or videos, and it does not send your screen data anywhere.
