# MoreFocus - Play Console Accessibility API Declaration

## Core functionality

MoreFocus is a focus app that helps users set local daily limits for selected apps and keep a shared daily limit for vertical videos across Instagram Reels, YouTube Shorts, and the full-screen video viewer on X. The accessibility service identifies the app in the foreground, counts local usage time for user-selected app rules, detects these video viewers, and shows a user-controlled focus warning after a chosen daily limit is reached. An optional setting covers inline Reels in the Instagram home feed with a neutral MoreFocus overlay that allows scrolling and blocks taps on the Reels tab in the Instagram bottom navigation bar. A separate YouTube setting covers Shorts cards on Home and blocks taps on the cards and the Shorts tab. Vertical swipes on a covered card scroll the feed. Users can also enable hard blocking for vertical videos in General preferences. Once the shared daily limit is reached, MoreFocus automatically goes back when it detects a viewer with an enabled rule, including the full-screen viewer on X. It also intercepts taps on feed Reels or Shorts when the corresponding video rule is enabled.

## Accessibility API usage

MoreFocus uses `AccessibilityService` to identify the foreground app package name for user-selected app limits. For the vertical videos rules, it also reads the active Instagram, YouTube, and X window hierarchies, including visible text, content descriptions, view identifiers, package name, and class name.

The app uses this data only on the device to decide whether selected app usage or vertical video viewing time should be counted and whether to show the MoreFocus warning overlay. When feed masking is enabled, the service also reads the bounds of the Reels or Shorts cards and their navigation tab to position the feed mask and the touch blocker. Hard blocking uses the same card bounds to intercept taps and forwards vertical swipes on a blocked card to the feed through accessibility scroll actions. With hard blocking enabled, the service performs the Android Back action when it detects an enabled viewer over the limit. On X, videos playing inline in the timeline remain available and do not consume the shared budget. It does not capture screenshots, record video, use OCR, upload screen data, sell data, share data, or use the data for ads or analytics.

## User disclosure and consent

Before opening Android Accessibility settings, the app displays an in-app disclosure explaining that MoreFocus uses accessibility events to identify the foreground app and reads Instagram and YouTube screen text, descriptions, and identifiers to detect Reels and Shorts, and identifies the full-screen video viewer on X. The disclosure also explains how MoreFocus uses the positions of Reels and Shorts in their feeds when feed masking or hard blocking is enabled and when it automatically performs Back. Users must manually enable the MoreFocus service in Android settings.

The app does not set `isAccessibilityTool=true` because its primary purpose is focus/productivity, not disability support.

## User controls

Users can disable MoreFocus globally, enable or disable the Instagram Reels, YouTube Shorts, and X video rules independently, choose their shared daily vertical videos limit, add or remove selected app limits, enable or disable each app limit, change each app limit duration, turn off the accessibility service in Android settings, tap "Sair" on the warning overlay, or tap "Ignorar 1 min" to pause warnings temporarily.

## Suggested Play Store description

MoreFocus helps you stay focused by showing a local warning after you reach your daily limit for selected apps or vertical videos across Instagram Reels, YouTube Shorts, and the full-screen video viewer on X.

To apply limits, MoreFocus uses Android Accessibility access after your explicit consent. The app identifies the foreground app and reads visible Instagram and YouTube screen information to detect Reels and Shorts and checks the X screen for its full-screen video viewer. It does not capture images or videos, and it does not send your screen data anywhere.
