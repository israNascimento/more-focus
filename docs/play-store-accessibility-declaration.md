# MoreFocus - Play Console Accessibility API Declaration

## Core functionality

MoreFocus is a focus app that helps users set local daily limits for selected apps and keep a shared daily limit for vertical videos on Instagram Reels and YouTube Shorts. The accessibility service identifies the app in the foreground, counts local usage time for user-selected app rules, detects when the active Instagram or YouTube screen appears to be Reels or Shorts, and shows a user-controlled focus warning after a chosen daily limit is reached. An optional setting covers inline Reels in the Instagram home feed with a black overlay that allows scrolling and blocks taps on the Reels tab in the Instagram bottom navigation bar. Users can also enable a hard block for Instagram Reels. Once the shared daily limit is reached, it intercepts taps on inline feed Reels and automatically goes back if the Reels viewer opens.

## Accessibility API usage

MoreFocus uses `AccessibilityService` to identify the foreground app package name for user-selected app limits. For the vertical videos rules, it also reads the active Instagram and YouTube window hierarchies, including visible text, content descriptions, view identifiers, package name, and class name.

The app uses this data only on the device to decide whether selected app usage or vertical video viewing time should be counted and whether to show the MoreFocus warning overlay. When feed masking is enabled, the service also reads the bounds of the media and the Reels tab to position the feed mask and the touch blocker. Hard blocking uses the same Reel bounds to intercept taps and forwards vertical swipes on a blocked Reel to the feed through accessibility scroll actions. When the service detects an open Reel over the limit, it performs the Android Back action. It does not capture screenshots, record video, use OCR, upload screen data, sell data, share data, or use the data for ads or analytics.

## User disclosure and consent

Before opening Android Accessibility settings, the app displays an in-app disclosure explaining that MoreFocus uses accessibility events to identify the foreground app and reads Instagram and YouTube screen text, descriptions, and identifiers only to detect Reels and Shorts. The disclosure also explains how MoreFocus uses the position of a Reel when feed masking or hard blocking is enabled and when it automatically performs Back. Users must manually enable the MoreFocus service in Android settings.

The app does not set `isAccessibilityTool=true` because its primary purpose is focus/productivity, not disability support.

## User controls

Users can disable MoreFocus globally, enable or disable the Instagram Reels and YouTube Shorts rules independently, choose their shared daily vertical videos limit, add or remove selected app limits, enable or disable each app limit, change each app limit duration, turn off the accessibility service in Android settings, tap "Sair" on the warning overlay, or tap "Ignorar 1 min" to pause warnings temporarily.

## Suggested Play Store description

MoreFocus helps you stay focused by showing a local warning after you reach your daily limit for selected apps or vertical videos on Instagram Reels and YouTube Shorts.

To apply limits, MoreFocus uses Android Accessibility access after your explicit consent. The app identifies the foreground app and reads visible Instagram and YouTube screen information only to detect Reels and Shorts. It does not capture images or videos, and it does not send your screen data anywhere.
