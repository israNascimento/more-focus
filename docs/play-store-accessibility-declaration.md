# MoreFocus - Play Console Accessibility API Declaration

## Core functionality

MoreFocus is a focus app that helps users set local daily limits for selected apps and keep a separate daily limit for Instagram Reels. The accessibility service identifies the app in the foreground, counts local usage time for user-selected app rules, detects when the active Instagram screen appears to be Reels, and shows a user-controlled focus warning after a chosen daily limit is reached.

## Accessibility API usage

MoreFocus uses `AccessibilityService` to identify the foreground app package name for user-selected app limits. For the Instagram Reels rule, it also reads the active Instagram window hierarchy, including visible text, content descriptions, view identifiers, package name, and class name.

The app uses this data only on the device to decide whether selected app usage or Reels viewing time should be counted and whether to show the MoreFocus warning overlay. It does not capture screenshots, record video, use OCR, upload screen data, sell data, share data, or use the data for ads or analytics.

## User disclosure and consent

Before opening Android Accessibility settings, the app displays an in-app disclosure explaining that MoreFocus uses accessibility events to identify the foreground app and reads Instagram screen text, descriptions, and identifiers only to detect Reels. Users must manually enable the MoreFocus service in Android settings.

The app does not set `isAccessibilityTool=true` because its primary purpose is focus/productivity, not disability support.

## User controls

Users can disable MoreFocus globally, disable the Instagram Reels rule, choose a daily Reels limit, add or remove selected app limits, enable or disable each app limit, change each app limit duration, turn off the accessibility service in Android settings, tap "Sair" on the warning overlay, or tap "Ignorar 1 min" to pause warnings temporarily.

## Suggested Play Store description

MoreFocus helps you stay focused by showing a local warning after you reach your daily limit for selected apps or Instagram Reels.

To apply limits, MoreFocus uses Android Accessibility access after your explicit consent. The app identifies the foreground app and reads visible Instagram screen information only to detect Reels. It does not capture images or videos, and it does not send your screen data anywhere.
