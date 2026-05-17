@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.gabe.focusblocker.ui

import android.content.Intent
import android.app.Activity
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gabe.focusblocker.AppBlockAccessibilityService
import com.gabe.focusblocker.data.entity.RuleMode
import com.gabe.focusblocker.data.entity.SessionSource
import com.gabe.focusblocker.engine.PinCheckResult
import com.gabe.focusblocker.repository.InstalledAppInfo
import com.gabe.focusblocker.util.PermissionUtils
import com.gabe.focusblocker.util.TimeUtils
import com.gabe.focusblocker.vpn.DomainBlockVpnService
import kotlin.random.Random

private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Dashboard("dashboard", "Dashboard", Icons.Filled.Dashboard),
    RuleSets("rules", "Rule Sets", Icons.Filled.List),
    Permissions("permissions", "Permissions", Icons.Filled.Shield),
    Settings("settings", "Settings", Icons.Filled.Settings)
}

private sealed class PendingPinAction {
    data class EndSession(val sessionId: Long) : PendingPinAction()
    data object EndAllSessions : PendingPinAction()
}

private sealed class PendingChallengeAction {
    data class EndSession(val sessionId: Long) : PendingChallengeAction()
    data object EndAllSessions : PendingChallengeAction()
}

@Composable
fun FocusBlockerApp(viewModel: FocusBlockerViewModel) {
    val navController = rememberNavController()
    val ruleSets by viewModel.ruleSets.collectAsState()
    val activeSessions by viewModel.activeSessions.collectAsState()
    val scheduledLocks by viewModel.scheduledLocks.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val draft by viewModel.editorDraft.collectAsState()
    var pendingPinAction by remember { mutableStateOf<PendingPinAction?>(null) }
    var pendingChallengeAction by remember { mutableStateOf<PendingChallengeAction?>(null) }

    val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route
    val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()
    val showTopLevelScaffold = currentDestination in topLevelRoutes

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            currentDestination == "dashboard" -> "Focus Blocker"
                            currentDestination == "rules" -> "Rule Sets"
                            currentDestination == "permissions" -> "Permissions"
                            currentDestination == "settings" -> "Settings"
                            currentDestination == "apps" -> "Choose Apps"
                            currentDestination?.startsWith("editor/") == true -> "Edit Rule Set"
                            else -> "Focus Blocker"
                        }
                    )
                },
                navigationIcon = {
                    if (!showTopLevelScaffold) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (showTopLevelScaffold) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(TopLevelDestination.Dashboard.route) {
                DashboardScreen(
                    ruleSets = ruleSets,
                    activeSessions = activeSessions,
                    scheduledLocks = scheduledLocks,
                    onScheduleLock = { ruleSetId, delayMinutes, durationMinutes ->
                        viewModel.scheduleLock(ruleSetId, delayMinutes, durationMinutes)
                    },
                    onEndSession = {
                        pendingChallengeAction = PendingChallengeAction.EndSession(it)
                    },
                    onEndAllSessions = {
                        pendingChallengeAction = PendingChallengeAction.EndAllSessions
                    },
                    onSetScheduledEnabled = viewModel::setScheduledLockEnabled,
                    onDeleteScheduledLock = viewModel::deleteScheduledLock,
                    onClearScheduledLocks = viewModel::clearScheduledLocks,
                    onRescheduleLock = { lockId, delayMinutes, durationMinutes ->
                        viewModel.rescheduleLock(lockId, delayMinutes, durationMinutes)
                    },
                    onEditRuleSet = {
                        viewModel.clearEditor()
                        navController.navigate("editor/$it")
                    }
                )
            }

            composable(TopLevelDestination.RuleSets.route) {
                RuleSetsScreen(
                    ruleSets = ruleSets,
                    onAddRuleSet = {
                        viewModel.clearEditor()
                        navController.navigate("editor/-1")
                    },
                    onEditRuleSet = {
                        viewModel.clearEditor()
                        navController.navigate("editor/$it")
                    },
                    onDuplicateRuleSet = viewModel::duplicateRuleSet,
                    onDeleteRuleSet = viewModel::deleteRuleSet,
                    onStartRuleSet = { viewModel.startRuleSet(it, SessionSource.APP) },
                    onSetWidgetStarred = viewModel::setWidgetStarred
                )
            }

            composable(TopLevelDestination.Permissions.route) {
                PermissionsScreen()
            }

            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(
                    blockSettingsApp = settings.blockSettingsApp,
                    pinEnabled = settings.pinEnabled,
                    onToggleBlockSettings = viewModel::setBlockSettingsApp,
                    onSetPin = viewModel::setPin,
                    onClearPin = viewModel::clearPin
                )
            }

            composable(
                route = "editor/{ruleSetId}",
                arguments = listOf(navArgument("ruleSetId") { type = NavType.LongType })
            ) { backStackEntry ->
                val ruleSetId = backStackEntry.arguments?.getLong("ruleSetId") ?: -1L
                LaunchedEffect(ruleSetId) {
                    viewModel.prepareEditor(ruleSetId.takeIf { it >= 0L })
                }

                RuleSetEditorScreen(
                    draft = draft,
                    onNameChanged = viewModel::updateDraftName,
                    onModeChanged = viewModel::updateDraftMode,
                    onDefaultDurationChanged = viewModel::updateDraftDefaultDuration,
                    onShowInWidgetChanged = viewModel::updateDraftShowInWidget,
                    onAddDomain = viewModel::addDraftDomain,
                    onRemoveDomain = viewModel::removeDraftDomain,
                    onOpenApps = {
                        viewModel.loadInstalledApps()
                        navController.navigate("apps")
                    },
                    onSave = {
                        viewModel.saveDraft {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable("apps") {
                LaunchedEffect(Unit) {
                    viewModel.loadInstalledApps()
                }
                InstalledAppsScreen(
                    apps = installedApps,
                    selectedPackages = draft?.selectedPackages ?: emptySet(),
                    onTogglePackage = viewModel::toggleDraftPackage
                )
            }
        }
    }

    pendingPinAction?.let { action ->
        PinRequiredDialog(
            onDismiss = { pendingPinAction = null },
            onVerify = { pin, onResult ->
                viewModel.verifyPin(pin) { result ->
                    if (result is PinCheckResult.Success || result is PinCheckResult.Disabled) {
                        when (action) {
                            is PendingPinAction.EndSession -> viewModel.endSession(action.sessionId)
                            PendingPinAction.EndAllSessions -> viewModel.endAllSessions()
                        }
                        pendingPinAction = null
                    }
                    onResult(result)
                }
            }
        )
    }

    pendingChallengeAction?.let { action ->
        ChallengeRequiredDialog(
            onDismiss = { pendingChallengeAction = null },
            onVerified = {
                when (action) {
                    is PendingChallengeAction.EndSession -> viewModel.endSession(action.sessionId)
                    PendingChallengeAction.EndAllSessions -> viewModel.endAllSessions()
                }
                pendingChallengeAction = null
            }
        )
    }
}

@Composable
private fun DashboardScreen(
    ruleSets: List<RuleSetCardUi>,
    activeSessions: List<ActiveSessionUi>,
    scheduledLocks: List<ScheduledLockUi>,
    onScheduleLock: (Long, Int, Int) -> Unit,
    onEndSession: (Long) -> Unit,
    onEndAllSessions: () -> Unit,
    onSetScheduledEnabled: (Long, Boolean) -> Unit,
    onDeleteScheduledLock: (Long) -> Unit,
    onClearScheduledLocks: () -> Unit,
    onRescheduleLock: (Long, Int, Int) -> Unit,
    onEditRuleSet: (Long) -> Unit
) {
    val delayPresets = listOf(0, 5, 10, 20, 30)
    val durationPresets = listOf(5, 15, 30, 60, 90)
    var selectedRuleSetId by remember(ruleSets) { mutableStateOf(ruleSets.firstOrNull()?.id) }
    var delayText by remember { mutableStateOf("10") }
    var durationText by remember { mutableStateOf("60") }
    val delayMinutes = delayText.toIntOrNull()
    val durationMinutes = durationText.toIntOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            ElevatedCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Start Lock Countdown", style = MaterialTheme.typography.titleLarge)

                    Text("Rule set", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ruleSets.take(3).forEach { ruleSet ->
                            FilterChip(
                                selected = selectedRuleSetId == ruleSet.id,
                                onClick = { selectedRuleSetId = ruleSet.id },
                                label = { Text(ruleSet.name.shortSetName()) }
                            )
                        }
                    }

                    Text("Starts in", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        delayPresets.forEach { minutes ->
                            FilterChip(
                                selected = delayText == minutes.toString(),
                                onClick = { delayText = minutes.toString() },
                                label = { Text(if (minutes == 0) "Now" else "${minutes}m") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = delayText,
                        onValueChange = { delayText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Minutes until lock") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Text("Lock duration", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        durationPresets.forEach { minutes ->
                            FilterChip(
                                selected = durationText == minutes.toString(),
                                onClick = { durationText = minutes.toString() },
                                label = { Text("${minutes}m") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Minutes locked") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val ruleSetId = selectedRuleSetId
                            if (ruleSetId != null && delayMinutes != null && durationMinutes != null) {
                                onScheduleLock(ruleSetId, delayMinutes, durationMinutes)
                            }
                        },
                        enabled = selectedRuleSetId != null &&
                            delayMinutes != null &&
                            durationMinutes != null &&
                            durationMinutes > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Countdown")
                    }
                }
            }
        }

        item {
            SectionTitle("Countdowns")
        }

        if (scheduledLocks.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No countdowns running",
                    body = "Choose a rule set, start delay, and lock duration above."
                )
            }
        } else {
            item {
                OutlinedButton(onClick = onClearScheduledLocks, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear All Countdowns")
                }
            }

            items(scheduledLocks, key = { it.id }) { lock ->
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lock.ruleSetName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Starts in ${lock.startsInLabel} - locks for ${lock.durationMinutes}m",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = lock.enabled,
                                onCheckedChange = { onSetScheduledEnabled(lock.id, it) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (delayMinutes != null && durationMinutes != null) {
                                        onRescheduleLock(lock.id, delayMinutes, durationMinutes)
                                    }
                                },
                                enabled = delayMinutes != null && durationMinutes != null && durationMinutes > 0
                            ) {
                                Text("Apply Current Time")
                            }
                            TextButton(onClick = { onDeleteScheduledLock(lock.id) }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Active Locks")
        }

        if (activeSessions.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No apps locked",
                    body = "A countdown becomes an active lock when it reaches zero."
                )
            }
        } else {
            items(activeSessions, key = { it.id }) { session ->
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.ruleSetName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${session.mode.label()} - ${session.remainingLabel}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            TextButton(onClick = { onEndSession(session.id) }) {
                                Text("End")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = onEndAllSessions, modifier = Modifier.fillMaxWidth()) {
                    Text("End All Sessions")
                }
            }
        }
    }
}

@Composable
private fun RuleSetsScreen(
    ruleSets: List<RuleSetCardUi>,
    onAddRuleSet: () -> Unit,
    onEditRuleSet: (Long) -> Unit,
    onDuplicateRuleSet: (Long) -> Unit,
    onDeleteRuleSet: (Long) -> Unit,
    onStartRuleSet: (Long) -> Unit,
    onSetWidgetStarred: (Long, Boolean) -> Unit
) {
    val starredCount = ruleSets.count { it.showInWidget }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Button(onClick = onAddRuleSet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Rule Set")
            }
            Text(
                "$starredCount/3 widget sets starred",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(ruleSets, key = { it.id }) { ruleSet ->
            ElevatedCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ruleSet.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${ruleSet.mode.label()} - ${ruleSet.defaultDurationLabel}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(
                            onClick = { onSetWidgetStarred(ruleSet.id, !ruleSet.showInWidget) }
                        ) {
                            Icon(
                                imageVector = if (ruleSet.showInWidget) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Filled.StarBorder
                                },
                                contentDescription = if (ruleSet.showInWidget) {
                                    "Remove widget star"
                                } else {
                                    "Star for widget"
                                }
                            )
                        }
                        AssistChip(onClick = {}, label = {
                            Text("${ruleSet.appCount} apps - ${ruleSet.domainCount} domains")
                        })
                    }
                    if (ruleSet.showInWidget) {
                        Text(
                            "Starred for widget",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onStartRuleSet(ruleSet.id) }) {
                            Text("Start")
                        }
                        OutlinedButton(onClick = { onEditRuleSet(ruleSet.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit")
                        }
                        OutlinedButton(onClick = { onDuplicateRuleSet(ruleSet.id) }) {
                            Text("Copy")
                        }
                        if (!ruleSet.isSystemPreset) {
                            OutlinedButton(onClick = { onDeleteRuleSet(ruleSet.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleSetEditorScreen(
    draft: RuleSetEditorDraft?,
    onNameChanged: (String) -> Unit,
    onModeChanged: (RuleMode) -> Unit,
    onDefaultDurationChanged: (String) -> Unit,
    onShowInWidgetChanged: (Boolean) -> Unit,
    onAddDomain: (String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    onOpenApps: () -> Unit,
    onSave: () -> Unit
) {
    if (draft == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading rule set...")
        }
        return
    }

    var newDomain by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Rule basics", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChanged,
                    label = { Text("Rule set name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.mode == RuleMode.BLOCK_LIST,
                        onClick = { onModeChanged(RuleMode.BLOCK_LIST) },
                        label = { Text("Block list") }
                    )
                    FilterChip(
                        selected = draft.mode == RuleMode.ALLOW_ONLY,
                        onClick = { onModeChanged(RuleMode.ALLOW_ONLY) },
                        label = { Text("Allow only") }
                    )
                }

                OutlinedTextField(
                    value = draft.defaultDurationMinutesText,
                    onValueChange = onDefaultDurationChanged,
                    label = { Text("Default duration (minutes)") },
                    placeholder = { Text("Blank = until stopped") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Star for widget")
                        Text(
                            "Up to 3 starred sets appear as widget Run buttons.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = draft.showInWidget, onCheckedChange = onShowInWidgetChanged)
                }
            }
        }

        ElevatedCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Apps", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${draft.selectedPackages.size} selected apps will be ${if (draft.mode == RuleMode.ALLOW_ONLY) "allowed" else "blocked"}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "System apps are ignored by blocking rules and stay allowed for safety.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose Apps")
                }
            }
        }

        ElevatedCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Domains", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Domains are stored now and ready for the later VPN blocking phase. Path-level blocking is intentionally out of scope.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it },
                        label = { Text("Add domain") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(onClick = {
                        onAddDomain(newDomain)
                        newDomain = ""
                    }) {
                        Text("Add")
                    }
                }

                if (draft.domains.isEmpty()) {
                    Text("No domains added yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    draft.domains.forEach { domain ->
                        Card {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(domain, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onRemoveDomain(domain) }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("Save Rule Set")
        }
    }
}

@Composable
private fun InstalledAppsScreen(
    apps: List<InstalledAppInfo>,
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var includeSystemApps by remember { mutableStateOf(false) }

    val filteredApps = remember(apps, search, includeSystemApps, selectedPackages) {
        apps
            .asSequence()
            .filter { includeSystemApps || !it.isSystemApp }
            .filter {
                val query = search.trim().lowercase()
                query.isBlank() ||
                    it.label.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query)
            }
            .toList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search apps") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${selectedPackages.size} apps selected")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show system apps")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = includeSystemApps,
                        onCheckedChange = { includeSystemApps = it }
                    )
                }
            }
            Text(
                "System apps are always allowed by the blocker, even in allow-only mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                val isSelected = app.packageName in selectedPackages
                val canToggle = !app.isSystemApp || isSelected
                ElevatedCard(onClick = {
                    if (canToggle) onTogglePackage(app.packageName)
                }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            tonalElevation = 4.dp
                        ) {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.Medium)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (app.isSystemApp) {
                                Text(
                                    "System app - always allowed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                if (canToggle) onTogglePackage(app.packageName)
                            },
                            enabled = canToggle
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DomainBlockVpnService.start(context)
            refreshKey++
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessibilityEnabled = remember(refreshKey) {
        PermissionUtils.isAccessibilityServiceEnabled(context, AppBlockAccessibilityService::class.java)
    }
    val notificationsEnabled = remember(refreshKey) {
        PermissionUtils.isNotificationPermissionGranted(context)
    }
    val vpnRunning = remember(refreshKey) {
        DomainBlockVpnService.isRunning
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PermissionCard(
            title = "Accessibility service",
            enabled = accessibilityEnabled,
            description = "Needed for detecting when a blocked app comes to the foreground and immediately showing the block screen.",
            buttonLabel = "Open Accessibility Settings",
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )

        ElevatedCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How to enable app blocking", style = MaterialTheme.typography.titleMedium)
                Text(
                    """
                    1. Tap Open Accessibility Settings above.
                    2. Find Focus Blocker. On some phones it is under Installed apps or Downloaded apps.
                    3. Tap Focus Blocker.
                    4. Turn on Use Focus Blocker.
                    5. Confirm Android's permission warning.
                    6. Return here and verify Accessibility service says Enabled.

                    If app blocking stops after updating the APK, turn this service off and back on once.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        PermissionCard(
            title = "Notifications",
            enabled = notificationsEnabled,
            description = "Recommended for future session reminders and persistent focus-state notifications.",
            buttonLabel = "Open Notification Settings",
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )

        PermissionCard(
            title = "Website VPN filter",
            enabled = vpnRunning,
            description = "Runs a local DNS VPN filter for domains in active rule sets. Private DNS or browser secure DNS can bypass this on some devices.",
            buttonLabel = if (vpnRunning) "Stop Website Filter" else "Start Website Filter",
            onClick = {
                if (vpnRunning) {
                    DomainBlockVpnService.stop(context)
                    refreshKey++
                } else {
                    val prepareIntent = VpnService.prepare(context)
                    if (prepareIntent == null) {
                        DomainBlockVpnService.start(context)
                        refreshKey++
                    } else {
                        vpnPermissionLauncher.launch(prepareIntent)
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    blockSettingsApp: Boolean,
    pinEnabled: Boolean,
    onToggleBlockSettings: (Boolean) -> Unit,
    onSetPin: (String, () -> Unit) -> Unit,
    onClearPin: (() -> Unit) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Self-control friction", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow blocking the Settings app")
                        Text(
                            "Leave this off unless you really want extra friction. It increases the risk of locking yourself out of permissions changes.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = blockSettingsApp, onCheckedChange = onToggleBlockSettings)
                }
            }
        }

        ElevatedCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PIN controls", style = MaterialTheme.typography.titleMedium)
                Text(if (pinEnabled) "PIN is enabled." else "PIN is off.")
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                    label = { Text("New PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(8) },
                    label = { Text("Confirm PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                pinMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            when {
                                newPin.length < 4 -> pinMessage = "Use at least 4 digits."
                                newPin != confirmPin -> pinMessage = "PIN entries do not match."
                                else -> onSetPin(newPin) {
                                    newPin = ""
                                    confirmPin = ""
                                    pinMessage = "PIN saved."
                                }
                            }
                        }
                    ) {
                        Text(if (pinEnabled) "Change PIN" else "Enable PIN")
                    }
                    if (pinEnabled) {
                        OutlinedButton(
                            onClick = {
                                onClearPin {
                                    newPin = ""
                                    confirmPin = ""
                                    pinMessage = "PIN disabled."
                                }
                            }
                        ) {
                            Text("Disable")
                        }
                    }
                }
            }
        }

        ElevatedCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Privacy stance", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Installed apps, sessions, and rule sets stay on-device. No accounts, no cloud sync, and no analytics are wired into this build.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PinRequiredDialog(
    onDismiss: () -> Unit,
    onVerify: (String, (PinCheckResult) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onVerify(pin) { result ->
                        message = when (result) {
                            PinCheckResult.Success,
                            PinCheckResult.Disabled -> null
                            is PinCheckResult.Failure ->
                                "Wrong PIN. ${result.attemptsRemaining} attempts left."
                            is PinCheckResult.Locked ->
                                "Try again in ${TimeUtils.formatRemainingTime(result.remainingMs)}."
                        }
                    }
                }
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChallengeRequiredDialog(
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    val challenge = remember { randomChallenge() }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type Unlock String") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(challenge, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(12) },
                    label = { Text("Unlock string") },
                    singleLine = true
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (input == challenge) {
                        onVerified()
                    } else {
                        message = "Does not match."
                    }
                }
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PermissionCard(
    title: String,
    enabled: Boolean,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                StatusDot(enabled = enabled)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun StatusDot(enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
        )
        Text(if (enabled) "Enabled" else "Needs attention")
    }
}

private fun RuleMode.label(): String = when (this) {
    RuleMode.BLOCK_LIST -> "Block List"
    RuleMode.ALLOW_ONLY -> "Allow Only"
}

private fun String.shortSetName(): String = when {
    contains("Set A", ignoreCase = true) -> "Set A"
    contains("Set B", ignoreCase = true) -> "Set B"
    contains("Set C", ignoreCase = true) -> "Set C"
    else -> take(12)
}

private fun randomChallenge(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return buildString {
        repeat(12) {
            append(alphabet[Random.nextInt(alphabet.length)])
        }
    }
}
