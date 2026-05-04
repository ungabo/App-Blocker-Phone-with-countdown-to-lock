package com.gabe.focusblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.gabe.focusblocker.util.DomainUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppBlockAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var lastBlockedPackage: String? = null
    private var lastBlockedDomain: String? = null
    private var lastBlockedAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        if (packageName == "com.android.systemui") return

        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && now - lastBlockedAt < 1_500L) {
            return
        }

        val app = application as FocusBlockerApplication
        serviceScope.launch {
            val decision = app.container.blockingRepository.evaluatePackage(packageName)
            if (decision.blocked) {
                lastBlockedPackage = packageName
                lastBlockedAt = System.currentTimeMillis()

                startActivity(blockedIntent(packageName, null, decision))
                return@launch
            }

            val browserDomain = extractBrowserDomain(packageName) ?: return@launch
            if (browserDomain == lastBlockedDomain && now - lastBlockedAt < 1_500L) {
                return@launch
            }
            val domainDecision = app.container.blockingRepository.evaluateDomain(browserDomain)
            if (!domainDecision.blocked) return@launch

            lastBlockedPackage = packageName
            lastBlockedDomain = browserDomain
            lastBlockedAt = System.currentTimeMillis()
            redirectBrowserToSafePage(packageName)
            startActivity(blockedIntent(packageName, browserDomain, domainDecision))
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun blockedIntent(
        packageName: String,
        domain: String?,
        decision: com.gabe.focusblocker.engine.BlockingDecision
    ): Intent {
        return Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_BLOCKED_PACKAGE, packageName)
            if (domain != null) putExtra(BlockedActivity.EXTRA_BLOCKED_DOMAIN, domain)
            putExtra(BlockedActivity.EXTRA_RULE_SET_NAMES, decision.ruleSetNames.joinToString())
            putExtra(BlockedActivity.EXTRA_REMAINING_MS, decision.remainingMs ?: -1L)
            putExtra(BlockedActivity.EXTRA_MODE, decision.mode?.name.orEmpty())
        }
    }

    private fun extractBrowserDomain(packageName: String): String? {
        if (packageName !in browserPackages) return null
        val root = rootInActiveWindow ?: return null
        return findAddressBarTexts(root)
            .flatMap { DomainUtils.extractDomainCandidates(it) }
            .firstOrNull()
    }

    private fun redirectBrowserToSafePage(packageName: String) {
        if (packageName !in browserPackages) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun findAddressBarTexts(node: AccessibilityNodeInfo): List<String> {
        val results = mutableListOf<String>()
        collectAddressBarTexts(node, results)
        return results
    }

    private fun collectAddressBarTexts(node: AccessibilityNodeInfo, results: MutableList<String>) {
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val isAddressBar = addressBarIdHints.any { viewId.contains(it) } ||
            (node.isFocused && node.isEditable)

        if (isAddressBar) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(results::add)
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(results::add)
        }

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                collectAddressBarTexts(child, results)
            }
        }
    }

    private companion object {
        val browserPackages = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.brave.browser",
            "com.microsoft.emmx",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.sec.android.app.sbrowser"
        )

        val addressBarIdHints = setOf(
            "url_bar",
            "location_bar",
            "address_bar",
            "search_box_text",
            "toolbar_url",
            "toolbar_edit_text"
        )
    }
}
