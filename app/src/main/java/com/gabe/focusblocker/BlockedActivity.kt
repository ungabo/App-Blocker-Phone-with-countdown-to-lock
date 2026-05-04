package com.gabe.focusblocker

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabe.focusblocker.data.entity.RuleMode
import com.gabe.focusblocker.ui.theme.FocusBlockerTheme
import com.gabe.focusblocker.util.TimeUtils

class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        val blockedDomain = intent.getStringExtra(EXTRA_BLOCKED_DOMAIN).orEmpty()
        val ruleSetNames = intent.getStringExtra(EXTRA_RULE_SET_NAMES).orEmpty()
        val remainingMs = intent.getLongExtra(EXTRA_REMAINING_MS, -1L).takeIf { it >= 0L }
        val mode = intent.getStringExtra(EXTRA_MODE)?.takeIf { it.isNotBlank() }?.let(RuleMode::valueOf)
        val isWebsiteBlock = blockedDomain.isNotBlank()

        setContent {
            FocusBlockerTheme {
                BackHandler { goHome() }
                Surface {
                    BlockedScreen(
                        blockedTitle = if (isWebsiteBlock) blockedDomain else resolveAppLabel(packageName),
                        detail = if (isWebsiteBlock) "Website in ${resolveAppLabel(packageName)}" else packageName,
                        blockedMessage = if (isWebsiteBlock) {
                            "This website is blocked right now."
                        } else {
                            "This app is blocked right now."
                        },
                        ruleSetNames = ruleSetNames.ifBlank { "Active focus rules" },
                        mode = mode,
                        remainingLabel = TimeUtils.formatRemainingTime(remainingMs),
                        onReturnHome = ::goHome,
                        onOpenFocusBlocker = {
                            startActivity(
                                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun resolveAppLabel(blockedPackage: String): String {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(blockedPackage, PackageManager.GET_META_DATA)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: Exception) {
            blockedPackage
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_BLOCKED_DOMAIN = "blocked_domain"
        const val EXTRA_RULE_SET_NAMES = "rule_set_names"
        const val EXTRA_REMAINING_MS = "remaining_ms"
        const val EXTRA_MODE = "mode"
    }
}

@Composable
private fun BlockedScreen(
    blockedTitle: String,
    detail: String,
    blockedMessage: String,
    ruleSetNames: String,
    mode: RuleMode?,
    remainingLabel: String,
    onReturnHome: () -> Unit,
    onOpenFocusBlocker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Focus session active", style = MaterialTheme.typography.titleLarge)
                Text(
                    blockedTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    blockedMessage,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "${mode?.name?.replace('_', ' ') ?: "BLOCKED"} - $remainingLabel",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Triggered by: $ruleSetNames",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onReturnHome, modifier = Modifier.fillMaxWidth()) {
                    Text("Return Home")
                }
                Button(onClick = onOpenFocusBlocker, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Focus Blocker")
                }
            }
        }
    }
}
