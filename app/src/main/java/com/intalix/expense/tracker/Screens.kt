package com.intalix.expense.tracker

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.intalix.expense.tracker.parse.Banks
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Page heading used by the tab screens: handwritten title on the left, optional action on the right. */
@Composable
private fun Heading(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontFamily = BrushFont, fontSize = 28.sp, maxLines = 1)
        action?.invoke()
    }
}

@Composable
private fun AddPill(label: String, onClick: () -> Unit) =
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(pal.pine).clickable(onClick = onClick).padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = pal.onPine, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp)); Text(label, color = pal.onPine, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }

// ---------- Analysis ----------
private enum class StatView(val label: String, val type: Int, val flow: Boolean) {
    ExpenseOverview("Expense overview", 2, false), IncomeOverview("Income overview", 1, false),
    ExpenseFlow("Expense flow", 2, true), IncomeFlow("Income flow", 1, true),
}

@Composable
fun StatsScreen(vm: Vm) {
    val all by vm.records.collectAsState(); val cats by vm.categories.collectAsState()
    var period by remember { mutableStateOf(Period.Month) }
    var anchor by remember { mutableStateOf(System.currentTimeMillis()) }
    var view by remember { mutableStateOf(StatView.ExpenseOverview) }
    val cur = Prefs.currency(LocalContext.current)
    val span = remember(period, anchor) { Periods.span(period, anchor) }
    val rows = remember(all, span, view) { all.filter { it.type == view.type && it.time >= span.start && it.time < span.end } }
    val total = rows.sumOf { it.amountPaise }
    val accent = if (view.type == 1) pal.income else pal.expense
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Default.ChevronLeft, "Previous", { anchor = Periods.shift(period, anchor, -1) })
            Text(Periods.label(period, span), Modifier.weight(1f), fontFamily = BrushFont, fontSize = 25.sp, textAlign = TextAlign.Center, maxLines = 1)
            RoundIconButton(Icons.Default.ChevronRight, "Next", { anchor = Periods.shift(period, anchor, 1) })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PickDropdown(Period.entries, period, { it.label }, Modifier.weight(1f)) { period = it }
            PickDropdown(StatView.entries, view, { it.label }, Modifier.weight(1.3f)) { view = it }
        }
        when {
            rows.isEmpty() -> EmptyState(Icons.Default.DonutLarge, "Nothing to analyse for this period yet.")
            view.flow -> FlowChart(rows, span, period, accent, total, cur)
            else -> Overview(rows, cats, total, accent, view.type == 1, cur)
        }
    }
}

@Composable
private fun Overview(rows: List<Rec>, cats: List<Category>, total: Long, accent: Color, income: Boolean, cur: String) {
    val names = cats.associate { it.id to it.name }
    val slices = rows.groupBy { it.categoryId }.map { (id, l) -> (names[id] ?: "Unknown") to l.sumOf { it.amountPaise } }.sortedByDescending { it.second }
    val track = pal.line
    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(210.dp)) {
            val w = 34.dp.toPx()
            val box = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = box, style = Stroke(w))
            var start = -90f
            slices.forEach { (name, v) ->
                val sweep = 360f * v / total
                drawArc(colorFor(name), start + 1f, (sweep - 2f).coerceAtLeast(0.5f), false, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = box, style = Stroke(w))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Caps(if (income) "Total income" else "Total spent")
            Text(money(cur, total), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
    slices.forEach { (name, v) ->
        val frac = v.toFloat() / total
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CatBadge(name, 38)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("%.1f%%".format(100.0 * frac), color = pal.soft, fontSize = 13.sp)
                }
                Text(money(cur, v), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(pal.line)) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(colorFor(name)))
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** Bar chart of the selected type over time (hours / days / weeks / months depending on the period). */
@Composable
private fun FlowChart(rows: List<Rec>, span: Span, period: Period, accent: Color, total: Long, cur: String) {
    val buckets = remember(period, span) { Periods.buckets(period, span) }
    val sums = remember(rows, buckets) { buckets.map { b -> rows.filter { it.time >= b.start && it.time < b.end }.sumOf { it.amountPaise } } }
    val peak = sums.indices.maxByOrNull { sums[it] } ?: 0
    var picked by remember(period, span, rows) { mutableStateOf(-1) }
    val shown = if (picked in sums.indices) picked else peak
    val max = (sums.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val now = System.currentTimeMillis()
    val elapsed = buckets.count { it.start <= now }.coerceIn(1, buckets.size)

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Caps(if (picked in sums.indices) buckets[shown].full else "Highest · " + buckets[shown].full)
        Text(money(cur, sums[shown]), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(184.dp)) {
            buckets.forEachIndexed { i, b ->
                Column(Modifier.weight(1f).fillMaxHeight().clickable { picked = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1.5.dp), contentAlignment = Alignment.BottomCenter) {
                        val frac = sums[i].toFloat() / max
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(if (sums[i] == 0L) 0.015f else frac.coerceAtLeast(0.03f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (sums[i] == 0L) pal.line else accent.copy(alpha = if (i == shown) 1f else 0.55f)),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        b.label, Modifier.heightIn(min = 16.dp).wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
                        color = if (i == shown) pal.ink else pal.soft, fontSize = 10.sp, maxLines = 1, softWrap = false,
                        fontWeight = if (i == shown) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Hairline()
        Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            FlowStat("Total", money(cur, total), Modifier.weight(1f))
            FlowStat("Avg / ${Periods.unit(period)}", money(cur, total / elapsed), Modifier.weight(1f))
            FlowStat("Records", "${rows.size}", Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun FlowStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Caps(label)
        Spacer(Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
    }
}


// ---------- Accounts ----------
@Composable
fun AccountsScreen(vm: Vm) {
    val accs by vm.accounts.collectAsState(); val all by vm.records.collectAsState()
    val cur = Prefs.currency(LocalContext.current)
    var edit by remember { mutableStateOf<Account?>(null) }
    var add by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Heading("Accounts") { AddPill("New") { add = true } }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(pal.pine).padding(20.dp),
        ) {
            Caps("Total balance", color = pal.onPine.copy(alpha = 0.75f))
            Text(money(cur, accs.sumOf { vm.balance(it, all) }), color = pal.onPine, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(accs, key = { it.id }) { a ->
                val bal = vm.balance(a, all)
                OutBox(Modifier.fillMaxWidth(), onClick = { edit = a }, borderColor = pal.line) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(pal.sage), contentAlignment = Alignment.Center) {
                            Icon(if (a.icon == "card") Icons.Default.CreditCard else if (a.icon == "savings") Icons.Default.Savings else Icons.Default.AccountBalanceWallet, null, tint = pal.pine)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(a.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(money(cur, bal), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (bal < 0) pal.expense else pal.ink)
                    }
                }
            }
        }
    }
    if (add) AccountDialog(null, { add = false }, vm)
    edit?.let { AccountDialog(it, { edit = null }, vm, canDelete = accs.size > 1) }
}

@Composable
private fun AccountDialog(a: Account?, onDone: () -> Unit, vm: Vm, canDelete: Boolean = false) {
    var name by remember { mutableStateOf(a?.name ?: "") }
    var initial by remember { mutableStateOf(a?.let { Money.format(it.initialPaise).replace(",", "") } ?: "0") }
    var confirmDelete by remember { mutableStateOf(false) }
    AppDialog(
        if (a == null) "New account" else "Edit account", onDone, "Save",
        onConfirm = { vm.saveAccount((a ?: Account(name = "")).copy(name = name.trim(), initialPaise = Money.parse(initial)!!)); onDone() },
        confirmEnabled = name.isNotBlank() && Money.parse(initial) != null,
        extra = if (a != null && canDelete) ({ TextAction("Delete", { confirmDelete = true }, color = pal.expense) }) else null,
    ) {
        Caps("Name"); Spacer(Modifier.height(4.dp))
        AppField(name, { name = it }, "e.g. Savings")
        Spacer(Modifier.height(12.dp))
        Caps("Opening balance"); Spacer(Modifier.height(4.dp))
        AppField(initial, { initial = it.filter { c -> c.isDigit() || c == '.' } }, "0", keyboard = KeyboardType.Decimal)
        if (a != null) BankLinks(a, vm)
    }
    if (confirmDelete && a != null) AppDialog(
        "Delete ${a.name}?", { confirmDelete = false }, "Delete", { vm.deleteAccount(a); onDone() }, dismiss = "Keep",
    ) { Text("Every record in this account is deleted too. This cannot be undone.", color = pal.soft) }
}

/** Which bank accounts (bank + last 4 digits) land in this account. Bank messages and app notifications use these links. */
@Composable
private fun ColumnScope.BankLinks(a: Account, vm: Vm) {
    val links by vm.links.collectAsState()
    val mine = links.filter { it.accountId == a.id }
    val banks = Banks.all
    var bank by remember { mutableStateOf(banks.firstOrNull()) }
    var digits by remember { mutableStateOf("") }
    Spacer(Modifier.height(16.dp))
    Caps("Bank accounts that go here")
    Spacer(Modifier.height(6.dp))
    if (mine.isEmpty()) Text("None yet. The first payment you confirm from a bank links it automatically.", color = pal.soft, fontSize = 13.sp)
    for (l in mine) {
        val name = l.key.substringBefore(':', "").let { k -> banks.firstOrNull { it.name.lowercase() == k }?.name ?: k.replaceFirstChar { c -> c.uppercase() } }
        val last4 = l.key.substringAfter(':')
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (l.key.contains(':')) "$name XX$last4" else "XX$last4", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextAction("Remove", { vm.removeLink(l.key) }, color = pal.expense)
        }
    }
    if (banks.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickDropdown(banks, bank ?: banks.first(), { it.name }, Modifier.weight(1f)) { bank = it }
            AppField(digits, { digits = it.filter(Char::isDigit).take(4) }, "Last 4", Modifier.width(96.dp), keyboard = KeyboardType.Number)
            TextAction("Add", { bank?.let { vm.addLink(it.name, digits, a.id) }; digits = "" }, enabled = digits.length == 4)
        }
    }
}

// ---------- Review ----------
@Composable
fun ReviewScreen(vm: Vm, onBack: () -> Unit) {
    val items by vm.pending.collectAsState(); val cats by vm.categories.collectAsState(); val accs by vm.accounts.collectAsState()
    val cur = Prefs.currency(LocalContext.current)
    Column(Modifier.fillMaxSize()) {
        PageTop("Detected payments", onBack)
        if (items.isEmpty()) EmptyState(Icons.Default.TaskAlt, "All caught up.\nNew payments you need to confirm will show up here.", Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { p ->
                var catId by remember(p.id) { mutableStateOf(p.suggestedCategoryId) }
                var accId by remember(p.id) { mutableStateOf(p.suggestedAccountId ?: accs.firstOrNull()?.id) }
                var remember by remember(p.id) { mutableStateOf(true) }
                val list = cats.filter { it.type == p.direction }
                val chosen = list.firstOrNull { it.id == catId } ?: list.firstOrNull()
                val accent = if (p.direction == 1) pal.income else pal.expense
                OutBox(Modifier.fillMaxWidth(), borderColor = pal.line) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Caps(if (p.direction == 2) "Paid" else "Received", color = accent)
                                Text(p.counterparty ?: "Unknown party", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("${fmtDay(p.time)} · ${fmtTime(p.time)}", color = pal.soft, fontSize = 13.sp)
                            }
                            Text(money(cur, p.amountPaise), color = accent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                        Text(p.raw, color = pal.soft, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 4,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(pal.note.copy(alpha = 0.6f)).padding(10.dp))
                        Text("Why it's here: ${p.reason}", color = pal.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Caps("Category"); ChipRow(list.map { it.id to it.name }, chosen?.id) { catId = it }
                        Caps("Account"); ChipRow(accs.map { it.id to it.name }, accId) { accId = it }
                        Row(Modifier.clickable { remember = !remember }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (remember) pal.pine else Color.Transparent)
                                    .then(Modifier.background(Color.Transparent)),
                                contentAlignment = Alignment.Center,
                            ) { if (remember) Icon(Icons.Default.Check, null, tint = pal.onPine, modifier = Modifier.size(16.dp)) else Icon(Icons.Default.CheckBoxOutlineBlank, null, tint = pal.pine) }
                            Spacer(Modifier.width(10.dp)); Text("Remember this payee next time", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Ignore", { vm.dismiss(p) }, filled = false, color = pal.soft)
                            PillButton("Add record", { vm.accept(p, accId!!, chosen!!.id, remember, false) }, Modifier.weight(1f), enabled = chosen != null && accId != null)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Categories ----------
@Composable
fun CategoriesScreen(vm: Vm) {
    val cats by vm.categories.collectAsState()
    var edit by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(2) }
    Column(Modifier.fillMaxSize()) {
        Heading("Categories") { AddPill("New") { adding = true } }
        Segment(listOf(2 to "Expense", 1 to "Income"), type, { type = it }, Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 6.dp),
            activeColor = if (type == 1) pal.income else pal.expense)
        LazyColumn(Modifier.fillMaxSize()) {
            items(cats.filter { it.type == type }, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().clickable { edit = c }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatBadge(c.name, 42)
                    Spacer(Modifier.width(14.dp))
                    Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Icon(Icons.Default.Edit, null, tint = pal.soft, modifier = Modifier.size(18.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    val target = edit
    if (target != null || adding) {
        var name by remember(target) { mutableStateOf(target?.name ?: "") }
        val close = { edit = null; adding = false }
        val fallback = target?.let { t -> cats.firstOrNull { it.id != t.id && it.type == t.type } }
        AppDialog(
            if (target == null) "New category" else "Edit category", close, "Save",
            onConfirm = { vm.saveCategory((target ?: Category(name = "", type = type)).copy(name = name.trim())); close() },
            confirmEnabled = name.isNotBlank(),
            extra = if (target != null && fallback != null) ({ TextAction("Delete", { vm.deleteCategory(target, fallback.id); close() }, color = pal.expense) }) else null,
        ) {
            AppField(name, { name = it }, "Category name")
            if (target == null) Text("Adds to ${if (type == 1) "Income" else "Expense"}.", color = pal.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            else if (fallback != null) Text("Deleting moves its records to “${fallback.name}”.", color = pal.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------- Budgets ----------
@Composable
fun BudgetsScreen(vm: Vm) {
    val budgets by vm.budgets.collectAsState(); val cats by vm.categories.collectAsState(); val all by vm.records.collectAsState()
    val m by vm.month.collectAsState(); val cur = Prefs.currency(LocalContext.current)
    val spent = vm.monthRecords(all, m).filter { it.type == 2 }
    var dialog by remember { mutableStateOf<Budget?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        MonthBar(vm)
        Heading("Budgets") { AddPill("New") { adding = true } }
        if (budgets.isEmpty()) EmptyState(Icons.Default.Savings, "No budgets yet.\nSet a monthly limit to keep spending in check.", Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(budgets, key = { it.id }) { b ->
                val used = if (b.categoryId == 0L) spent.sumOf { it.amountPaise } else spent.filter { it.categoryId == b.categoryId }.sumOf { it.amountPaise }
                val name = if (b.categoryId == 0L) "Overall" else cats.firstOrNull { it.id == b.categoryId }?.name ?: "Unknown"
                val over = used > b.limitPaise
                OutBox(Modifier.fillMaxWidth(), onClick = { dialog = b }, borderColor = pal.line) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (b.categoryId != 0L) { CatBadge(name, 34); Spacer(Modifier.width(10.dp)) }
                            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(if (over) "Over by ${money(cur, used - b.limitPaise)}" else "${money(cur, b.limitPaise - used)} left", color = if (over) pal.expense else pal.income, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(pal.line)) {
                            Box(Modifier.fillMaxWidth((used.toFloat() / b.limitPaise).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(if (over) pal.expense else pal.pine))
                        }
                        Text("${money(cur, used)} of ${money(cur, b.limitPaise)}", color = pal.soft, fontSize = 13.sp)
                    }
                }
            }
        }
    }
    if (adding || dialog != null) {
        val b = dialog
        var limit by remember(b) { mutableStateOf(b?.let { Money.format(it.limitPaise).replace(",", "") } ?: "") }
        var cat by remember(b) { mutableStateOf(b?.categoryId ?: 0L) }
        val close = { adding = false; dialog = null }
        AppDialog(
            "Monthly budget", close, "Save",
            onConfirm = { vm.saveBudget((b ?: Budget(limitPaise = 1)).copy(categoryId = cat, limitPaise = Money.parse(limit)!!)); close() },
            confirmEnabled = (Money.parse(limit) ?: 0) > 0,
            extra = if (b != null) ({ TextAction("Delete", { vm.deleteBudget(b); close() }, color = pal.expense) }) else null,
        ) {
            Caps("Limit"); Spacer(Modifier.height(4.dp))
            AppField(limit, { limit = it.filter { c -> c.isDigit() || c == '.' } }, "0", keyboard = KeyboardType.Decimal)
            Spacer(Modifier.height(12.dp))
            Caps("Applies to"); Spacer(Modifier.height(6.dp))
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                ChipRow(listOf(0L to "Overall") + cats.filter { it.type == 2 }.map { it.id to it.name }, cat) { cat = it }
            }
        }
    }
}

// ---------- Skipped messages ----------
@Composable
fun SkippedScreen(vm: Vm, onBack: () -> Unit) {
    val list by vm.skipped.collectAsState()
    var days by remember { mutableStateOf(0L) }
    var onlyLikely by remember { mutableStateOf(true) }
    LaunchedEffect(days) { vm.loadSkipped(days.toInt()) }
    Column(Modifier.fillMaxSize()) {
        PageTop("Skipped messages", onBack)
        Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            ChipRow(listOf(30L to "30 days", 60L to "60 days", 90L to "90 days", 0L to "All time"), days) { days = it }
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            ChipRow(listOf(1L to "Looks like money", 0L to "All with an amount"), if (onlyLikely) 1L else 0L) { onlyLikely = it == 1L }
        }
        val shown = list?.filter { !onlyLikely || it.likely }
        when {
            shown == null -> Text("Reading messages…", Modifier.padding(20.dp), color = pal.soft)
            shown.isEmpty() -> EmptyState(Icons.Default.TaskAlt, "Nothing skipped in this range.", Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically))
            else -> {
                Text("${shown.size} message(s) the app did not record. Newest first.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = pal.soft, fontSize = 13.sp)
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { "${it.time}|${it.sender}|${it.body.hashCode()}" }) { m ->
                        OutBox(Modifier.fillMaxWidth(), borderColor = pal.line) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${m.sender} · ${fmtDay(m.time)} ${fmtTime(m.time)}", color = pal.soft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(m.body, fontSize = 13.sp, lineHeight = 17.sp)
                                Text("Why: ${m.reason}", color = pal.expense, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Settings ----------
@Composable
fun SettingsScreen(vm: Vm, onBack: () -> Unit, onSkipped: () -> Unit = {}, onPayees: () -> Unit = {}) {
    val ctx = LocalContext.current
    val accs by vm.accounts.collectAsState()
    var auto by remember { mutableStateOf(Prefs.autoAdd(ctx)) }
    var smsOn by remember { mutableStateOf(Prefs.smsOn(ctx)) }
    var notifOn by remember { mutableStateOf(Prefs.notifOn(ctx)) }
    var defAcc by remember { mutableStateOf(Prefs.defaultAccount(ctx)) }
    var pinDialog by remember { mutableStateOf(false) }
    var scanDays by remember { mutableStateOf(90L) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(vm::exportTo) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmImport = it }
    val smsGranted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val listenerOn = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    Column(Modifier.fillMaxSize()) {
        PageTop("Settings", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Section("Automatic entries")
            SwitchRow("Read payment SMS", "Debited / credited messages from banks", smsOn) { smsOn = it; Prefs.setSmsOn(ctx, it) }
            if (!smsGranted) Text("SMS permission is off — open app settings → Permissions → SMS.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = pal.expense, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            SwitchRow("Add confident payments automatically", "Off = every detected payment waits in the review list", auto) { auto = it; Prefs.setAutoAdd(ctx, it) }
            SwitchRow("Watch payment apps", "Adds payments with your note from their notifications; matched with the bank SMS so nothing counts twice", notifOn) { notifOn = it; Prefs.setNotifOn(ctx, it) }
            if (!listenerOn) TextAction("Allow notification access", { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, Modifier.padding(horizontal = 10.dp))
            Caps("Default account for new payments", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
            Box(Modifier.padding(horizontal = 20.dp)) { ChipRow(accs.map { it.id to it.name }, defAcc.takeIf { id -> accs.any { it.id == id } } ?: accs.firstOrNull()?.id) { defAcc = it; Prefs.setDefaultAccount(ctx, it) } }
            Caps("Scan existing messages", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
            Box(Modifier.padding(horizontal = 20.dp)) {
                ChipRow(listOf(30L to "30 days", 60L to "60 days", 90L to "90 days", 0L to "All time"), scanDays) { scanDays = it }
            }
            TextAction("People & payees → category", onPayees, Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            Text("Pick a category for a person or shop once; every payment to or from them is filed there from then on.", Modifier.padding(horizontal = 20.dp), color = pal.soft, fontSize = 13.sp)
            TextAction("Auto-categorise existing records", { vm.recategorize() }, Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            TextAction("Scan now", { vm.scanInbox(scanDays.toInt()) }, Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            TextAction("View skipped messages", onSkipped, Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            Text("Reads bank messages already in your inbox. Payments already recorded are skipped, so it is safe to re-run.", Modifier.padding(horizontal = 20.dp), color = pal.soft, fontSize = 13.sp)

            Section("Backup")
            TextAction("Export backup", { export.launch("ExpenseTrackerBackup_${java.text.SimpleDateFormat("dd_MM_yy_Hmm", java.util.Locale.US).format(java.util.Date())}.mbak") }, Modifier.padding(horizontal = 10.dp))
            TextAction("Import backup (replaces all data)", { import.launch(arrayOf("*/*")) }, Modifier.padding(horizontal = 10.dp))

            Section("Security")
            TextAction(if (Prefs.lock(ctx) == null) "Set a 4-digit passcode" else "Change / remove passcode", { pinDialog = true }, Modifier.padding(horizontal = 10.dp))

            Section("Privacy")
            Text("This app has no internet permission. Everything stays on this phone.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = pal.soft, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(32.dp))
        }
    }
    if (pinDialog) PinSetupDialog(vm) { pinDialog = false }
    confirmImport?.let { uri ->
        AppDialog("Replace all data?", { confirmImport = null }, "Replace", { vm.importFrom(uri); confirmImport = null }) {
            Text("Importing deletes the current accounts, categories and records and replaces them with the backup.", color = pal.soft)
        }
    }
}

@Composable
private fun Section(t: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp)) {
        Text(t, fontFamily = BrushFont, fontSize = 22.sp, color = pal.pine)
        Spacer(Modifier.height(4.dp)); Hairline()
    }
}

@Composable
private fun PinSetupDialog(vm: Vm, onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AppDialog(
        "Passcode", onDone, "Save", { vm.setLock(pin); onDone() }, confirmEnabled = pin.length == 4,
        extra = { TextAction("Remove", { vm.setLock(null); onDone() }, color = pal.expense) },
    ) {
        AppField(pin, { pin = it.filter(Char::isDigit).take(4) }, "4 digits", keyboard = KeyboardType.NumberPassword, password = true)
    }
}

@Composable
fun LockScreen(vm: Vm, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    fun press(k: String) {
        bad = false
        pin = if (k == "⌫") pin.dropLast(1) else (pin + k).take(4)
        if (pin.length == 4) { if (vm.checkLock(pin)) onUnlock() else { bad = true; pin = "" } }
    }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = pal.pine)
        Spacer(Modifier.height(8.dp))
        Text("Expense Tracker", fontFamily = BrushFont, fontSize = 34.sp, color = pal.pine)
        Text(if (bad) "Wrong passcode, try again" else "Enter your passcode", color = if (bad) pal.expense else pal.soft, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i -> Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < pin.length) pal.pine else pal.line)) }
        }
        Spacer(Modifier.height(30.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(70.dp).clip(CircleShape).background(if (k.isEmpty()) Color.Transparent else pal.card)
                            .then(if (k.isEmpty()) Modifier else Modifier.clickable { press(k) }),
                        contentAlignment = Alignment.Center,
                    ) { if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = pal.ink) else Text(k, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
