package com.intalix.expense.tracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.RoundingMode
import java.util.Calendar

/**
 * Full-screen entry page: Expense/Income switch, account + category selectors, a note,
 * a big amount display and a calculator keypad (expressions like 120+45×2 work).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(vm: Vm, existing: Rec?, onDone: () -> Unit) {
    val cats by vm.categories.collectAsState()
    val accs by vm.accounts.collectAsState()
    val allRecs by vm.records.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cur = Prefs.currency(ctx)

    var type by remember { mutableStateOf(existing?.type ?: 2) }
    var expr by remember { mutableStateOf(existing?.let { Money.format(it.amountPaise).replace(",", "") } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var accId by remember { mutableStateOf(existing?.accountId) }
    var catId by remember { mutableStateOf(existing?.categoryId) }
    var time by remember { mutableStateOf(existing?.time ?: System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(0) } // 1 = account, 2 = category
    var confirmDelete by remember { mutableStateOf(false) }

    val list = cats.filter { it.type == type }
    val chosenCat = list.firstOrNull { it.id == catId } ?: list.firstOrNull()
    val chosenAcc = accs.firstOrNull { it.id == accId }
        ?: accs.firstOrNull { it.id == Prefs.defaultAccount(ctx) } ?: accs.firstOrNull()
    val paise = Calc.toPaise(expr)
    val accent = if (type == 1) pal.income else pal.expense
    val canSave = paise != null && paise > 0 && chosenCat != null && chosenAcc != null

    BackHandler(onBack = onDone)

    fun save() {
        if (!canSave) return
        vm.save(
            (existing ?: Rec(accountId = 0, categoryId = 0, type = type, amountPaise = 0, time = time))
                .copy(accountId = chosenAcc!!.id, categoryId = chosenCat!!.id, type = type, amountPaise = paise!!, note = note.trim(), time = time),
        )
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // --- top: Cancel | Expense/Income | Save ---
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction("Cancel", onDone, color = pal.soft)
            Segment(
                listOf(2 to "Expense", 1 to "Income"), type, { type = it; catId = null },
                Modifier.weight(1f).padding(horizontal = 6.dp), activeColor = accent,
            )
            TextAction("Save", ::save, color = accent, enabled = canSave)
        }

        // --- account & category selectors ---
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Selector(chosenAcc?.let { money(cur, vm.balance(it, allRecs)) }, chosenAcc?.name ?: "—", Modifier.weight(1f), onClick = { picker = 1 }) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(pal.sage), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = pal.pine, modifier = Modifier.size(17.dp))
                }
            }
            Selector(null, chosenCat?.name ?: "—", Modifier.weight(1f), onClick = { picker = 2 }) { CatBadge(chosenCat?.name ?: "?", 30) }
        }

        // --- note ---
        Box(
            Modifier.fillMaxWidth().weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).background(pal.note)
                .border(1.5.dp, pal.pine.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(14.dp),
        ) {
            BasicTextField(
                note, { note = it }, Modifier.fillMaxSize(),
                textStyle = TextStyle(color = pal.ink, fontFamily = Quick, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                cursorBrush = SolidColor(pal.pine),
            )
            if (note.isEmpty()) Text("Add a note…", color = pal.soft, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        // --- amount display ---
        AmountBox(cur, expr, accent) { expr = it }

        // --- keypad ---
        Keypad(
            onKey = { k ->
                if (k == "=") {
                    val v = Calc.evaluate(expr)
                    if (v != null && v.signum() > 0 && Calc.hasOperator(expr)) {
                        expr = v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                    }
                } else expr = Calc.press(expr, k)
            },
        )

        // --- date | time ---
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutBox(Modifier.weight(1f), onClick = { pickDate = true }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = pal.pine, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(fmtDay(time), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                }
            }
            OutBox(Modifier.weight(0.8f), onClick = { pickTime = true }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = pal.pine, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(fmtTime(time), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        if (existing != null) {
            TextAction("Delete this record", { confirmDelete = true }, Modifier.align(Alignment.CenterHorizontally), color = pal.expense)
        } else Spacer(Modifier.height(4.dp))
    }

    // --- pickers ---
    if (picker != 0) ModalBottomSheet(onDismissRequest = { picker = 0 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = pal.card, contentColor = pal.ink) {
        if (picker == 1) {
            Caps("Choose account", Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
            LazyColumn(Modifier.padding(bottom = 16.dp)) {
                items(accs, key = { it.id }) { a ->
                    val on = a.id == chosenAcc?.id
                    val bal = vm.balance(a, allRecs)
                    Row(
                        Modifier.fillMaxWidth().clickable { accId = a.id; picker = 0 }.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(if (on) pal.pine else pal.sage), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = if (on) pal.onPine else pal.pine, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(a.name, Modifier.weight(1f), fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        Text(money(cur, bal), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (bal < 0) pal.expense else pal.soft, maxLines = 1)
                    }
                }
            }
        } else {
            Caps("Choose category", Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            LazyVerticalGrid(
                GridCells.Fixed(5), Modifier.heightIn(max = 380.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(list, key = { it.id }) { c ->
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { catId = c.id; picker = 0 }.padding(vertical = 4.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CatBadge(c.name, 42, selected = c.id == chosenCat?.id)
                        Text(c.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete && existing != null) AppDialog(
        "Delete this record?", { confirmDelete = false }, "Delete", { vm.delete(existing); onDone() }, dismiss = "Keep",
    ) { Text("${money(cur, existing.amountPaise)} will be removed for good.", color = pal.soft) }

    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = time)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextAction("OK", {
                    st.selectedDateMillis?.let { utc ->
                        val src = Calendar.getInstance().apply { timeInMillis = time }
                        val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        time = Calendar.getInstance().apply {
                            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), src.get(Calendar.HOUR_OF_DAY), src.get(Calendar.MINUTE), 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    pickDate = false
                })
            },
            dismissButton = { TextAction("Cancel", { pickDate = false }, color = pal.soft) },
        ) { DatePicker(st) }
    }
    if (pickTime) {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val st = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false)
        AppDialog("Pick a time", { pickTime = false }, "OK", {
            time = Calendar.getInstance().apply { timeInMillis = time; set(Calendar.HOUR_OF_DAY, st.hour); set(Calendar.MINUTE, st.minute) }.timeInMillis
            pickTime = false
        }) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(st) } }
    }
}

@Composable
private fun Selector(label: String?, value: String, modifier: Modifier, onClick: () -> Unit, lead: @Composable () -> Unit) {
    OutBox(modifier, onClick = onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            lead()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (label != null) Text(label, color = pal.soft, fontSize = 11.sp, maxLines = 1)
                Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.UnfoldMore, null, tint = pal.soft, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmountBox(cur: String, expr: String, accent: Color, onChange: (String) -> Unit) {
    val hasOp = Calc.hasOperator(expr)
    val v = if (hasOp) Calc.evaluate(expr) else null
    OutBox(Modifier.fillMaxWidth(), stroke = 2.dp, borderColor = accent.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                if (hasOp) Text(
                    if (v == null) "invalid" else "= $cur" + v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
                    color = pal.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                Text(
                    if (hasOp) expr else cur + (expr.ifEmpty { "0" }),
                    color = if (expr.isEmpty()) pal.soft else accent, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClick = { onChange(Calc.press(expr, "⌫")) }, onLongClick = { onChange("") }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Backspace, "Delete digit (hold to clear)", tint = pal.ink) }
        }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    // operator column on the left, digits to the right — like a pocket calculator
    val rows = listOf(
        listOf("÷", "7", "8", "9"),
        listOf("×", "4", "5", "6"),
        listOf("-", "1", "2", "3"),
        listOf("+", ".", "0", "="),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val isOp = k in listOf("÷", "×", "-", "+")
                    val isEq = k == "="
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(shape)
                            .background(if (isEq) pal.pine else if (isOp) pal.sage else pal.card)
                            .then(if (!isOp && !isEq) Modifier.border(1.5.dp, pal.pine.copy(alpha = 0.35f), shape) else Modifier)
                            .clickable { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (k == "-") "−" else k, fontSize = if (isOp || isEq) 26.sp else 24.sp, fontWeight = FontWeight.Bold,
                            color = if (isEq) pal.onPine else if (isOp) pal.pine else pal.ink,
                        )
                    }
                }
            }
        }
    }
}
