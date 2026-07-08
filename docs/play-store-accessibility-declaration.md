# MoreFocus - Play Console Accessibility API Declaration

## Core functionality

MoreFocus is a focus app that helps users avoid Instagram Reels without blocking the entire Instagram app. The accessibility service detects when the active Instagram screen appears to be Reels and shows a user-controlled focus warning.

## Accessibility API usage

MoreFocus uses `AccessibilityService` to read the active Instagram window hierarchy, including visible text, content descriptions, view identifiers, package name, and class name. This access is limited to `com.instagram.android` through the service configuration.

The app uses this data only on the device to decide whether to show the MoreFocus warning overlay. It does not capture screenshots, record video, use OCR, upload screen data, sell data, share data, or use the data for ads or analytics.

## User disclosure and consent

Before opening Android Accessibility settings, the app displays an in-app disclosure explaining that MoreFocus reads Instagram screen text, descriptions, and identifiers only to detect Reels. Users must manually enable the MoreFocus service in Android settings.

The app does not set `isAccessibilityTool=true` because its primary purpose is focus/productivity, not disability support.

## User controls

Users can disable MoreFocus globally, disable the Instagram Reels rule, turn off the accessibility service in Android settings, tap "Sair" on the warning overlay, or tap "Ignorar 5 min" to pause warnings temporarily.

## Suggested Play Store description

MoreFocus helps you stay focused by showing a local warning when you open Instagram Reels. It does not block the rest of Instagram.

To detect Reels, MoreFocus uses Android Accessibility access after your explicit consent. The app reads visible Instagram screen information only on your device, does not capture images or videos, and does not send your screen data anywhere.
