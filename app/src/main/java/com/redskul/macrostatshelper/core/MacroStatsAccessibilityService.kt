package com.redskul.macrostatshelper.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MacroStatsAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d("AccessibilityService", "MacroStats Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service is primarily for permission purposes
        // It can be used in the future for additional features like app usage tracking
        // For now, we just log events for debugging purposes
        event?.let {
            android.util.Log.v("AccessibilityService", "Event: ${it.eventType} from ${it.packageName}")
        }
    }

    override fun onInterrupt() {
        android.util.Log.d("AccessibilityService", "MacroStats Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("AccessibilityService", "MacroStats Accessibility Service destroyed")
    }
}