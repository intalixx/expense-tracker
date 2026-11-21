package com.intalix.expense.tracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: Vm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { Root(vm) } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Records("Records", Icons.Default.ReceiptLong),
    Analysis("Analysis", Icons.Default.PieChart),
    Budgets("Budgets", Icons.Default.Savings),
    Accounts("Accounts", Icons.Default.AccountBalanceWallet),
    Categories("Categories", Icons.Default.Category),
}

@Composable
private fun Root(vm: Vm) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var locked by remember { mutableStateOf(Prefs.lock(ctx) != null) }
    // Re-lock whenever the app returns from background.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP && Prefs.lock(ctx) != null) locked = true }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    if (locked) { LockScreen(vm) { locked = false }; return }

    var tab by remember { mutableStateOf(Tab.Records) }
    var showReview by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSkipped by remember { mutableStateOf(false) }
    var showPayees by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    val pending by vm.pending.collectAsState()
    val msg by vm.message.collectAsState()
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { if (msg != null) { toast = msg; vm.message.value = null } }
    LaunchedEffect(toast) { if (toast != null) { delay(2800); toast = null } }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) {}
    LaunchedEffect(Unit) {
        val need = buildList {
            add(Manifest.permission.RECEIVE_SMS); add(Manifest.permission.READ_SMS)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
    }

    // Notification access is a special setting (not a normal permission), so an app update never asks for it: ask once, explain why.
    var askListener by remember {
        mutableStateOf(
            Prefs.notifOn(ctx) && !Prefs.listenerAsked(ctx) &&
                !androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName),
        )
    }
    if (askListener) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askListener = false; Prefs.setListenerAsked(ctx) },
            title = { Text("Read payment app notifications?") },
            text = { Text("This lets the app add payments from their notifications, including the note the sender typed, even when the bank SMS is late or never comes. Payments already recorded from the SMS are never added twice. Turn on \"Expense Tracker\" on the next screen.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askListener = false; Prefs.setListenerAsked(ctx)
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Open settings") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { askListener = false; Prefs.setListenerAsked(ctx) }) { Text("Not now") }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        when {
            showReview -> ReviewScreen(vm) { showReview = false }
            showSkipped -> SkippedScreen(vm) { showSkipped = false }
            showPayees -> PayeesScreen(vm) { showPayees = false }
            showSettings -> SettingsScreen(vm, { showSettings = false }, { showSkipped = true }, { showPayees = true })
            adding -> EntryScreen(vm, null) { adding = false }
            editing != null -> EntryScreen(vm, editing) { editing = null }
            else -> Column(Modifier.fillMaxSize()) {
                // --- top strip: wordmark + review bell + settings ---
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Expense Tracker", Modifier.weight(1f), fontFamily = BrushFont, fontSize = 30.sp, color = pal.pine, maxLines = 1)
                    RoundIconButton(Icons.Default.NotificationsNone, "Detected payments", { showReview = true }, badge = pending.size)
                    RoundIconButton(Icons.Default.Tune, "Settings", { showSettings = true })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        Tab.Records -> RecordsScreen(vm, onEdit = { editing = it }, onReview = { showReview = true })
                        Tab.Analysis -> StatsScreen(vm)
                        Tab.Budgets -> BudgetsScreen(vm)
                        Tab.Accounts -> AccountsScreen(vm)
                        Tab.Categories -> CategoriesScreen(vm)
                    }
                    if (tab == Tab.Records) Box(
                        Modifier.align(Alignment.BottomEnd).padding(18.dp).size(62.dp).clip(CircleShape).background(pal.gold)
                            .border(2.dp, pal.pine, CircleShape).clickable { adding = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Add, "Add record", tint = pal.pine, modifier = Modifier.size(34.dp)) }
                }
                TabBar(tab) { tab = it }
            }
        }
        AnimatedVisibility(toast != null, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp, start = 24.dp, end = 24.dp)) {
            Text(
                toast ?: "", color = pal.onPine, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(pal.pine).padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun TabBar(current: Tab, onPick: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(pal.card)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Tab.entries.forEach { t ->
                val on = t == current
                Column(
                    Modifier.weight(1f).clickable { onPick(t) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (on) pal.pine else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) { Icon(t.icon, t.label, tint = if (on) pal.onPine else pal.soft, modifier = Modifier.size(22.dp)) }
                    Text(t.label, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) pal.pine else pal.soft, maxLines = 1)
                }
            }
        }
    }
}
