package com.walkieclaudie.app

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Sees hardware keys system-wide while the screen is on (volume long-press test). Never consumes them. */
class KeyAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() = KeyLog.add("a11y", "service connected")
    override fun onKeyEvent(event: KeyEvent): Boolean {
        KeyLog.key("a11y", event)
        return false
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
}
