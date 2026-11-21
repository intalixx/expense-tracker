package com.intalix.expense.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One row of the list: a payee plus what the records say about them. */
private class PayeeRow(val p: Payee, val count: Int, val totalPaise: Long)

/**
 * Settings -> People & payees. Every person / shop money was paid to or received from, with a "always this category"
 * rule. New payments from that payee are filed in the chosen category (and can skip the review list).
 */
@Composable
fun PayeesScreen(vm: Vm, onBack: () -> Unit) {
    val payees by vm.payees.collectAsState()
    val records by vm.records.collectAsState()
    val cats by vm.categories.collectAsState()
    val cur = Prefs.currency(LocalContext.current)
    var type by remember { mutableStateOf(2) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0L) } // 0 all, 1 no category, 2 has category
    var edit by remember { mutableStateOf<PayeeRow?>(null) }
    LaunchedEffect(Unit) { vm.backfillPayees() }

    // count + total per payee, from the records themselves (one pass)
    val stats = remember(records) {
        val m = HashMap<Pair<String, Int>, Pair<Int, Long>>()
        for (r in records) Payees.fromNote(r.note)?.let { n ->
            val k = merchantKey(n) to r.type
            val (c, t) = m[k] ?: (0 to 0L)
            m[k] = (c + 1) to (t + r.amountPaise)
        }
        m
    }
    val rows = payees.filter { it.type == type }
        .filter { query.isBlank() || it.name.contains(query.trim(), true) || it.key.contains(merchantKey(query)) }
        .filter { filter == 0L || (filter == 1L) == (it.categoryId == null || cats.none { c -> c.id == it.categoryId }) }
        .map { p -> stats[p.key to p.type].let { PayeeRow(p, it?.first ?: 0, it?.second ?: 0L) } }

    Column(Modifier.fillMaxSize()) {
        PageTop("People & payees", onBack)
        Segment(listOf(2 to "Paid to", 1 to "Received from"), type, { type = it }, Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 6.dp),
            activeColor = if (type == 1) pal.income else pal.expense)
        Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { AppField(query, { query = it }, "Search name or UPI id") }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            ChipRow(listOf(0L to "All", 1L to "No category", 2L to "Has category"), filter) { filter = it }
        }
        if (rows.isEmpty()) {
            EmptyState(
                Icons.Default.Group,
                if (payees.none { it.type == type }) "Nobody yet.\nPeople and shops show up here after their first payment is detected."
                else "No match.",
                Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically),
            )
        } else {
            Text("${rows.size} ${if (rows.size == 1) "payee" else "payees"}. Tap one to choose where its payments go.",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = pal.soft, fontSize = 13.sp)
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.p.key + "|" + it.p.type }) { row ->
                    val cat = cats.firstOrNull { it.id == row.p.categoryId }
                    Column(Modifier.fillMaxWidth().clickable { edit = row }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (row.count == 0) "Seen ${fmtDay(row.p.lastTime)}" else
                                        "${row.count} ${if (row.count == 1) "payment" else "payments"} · ${money(cur, row.totalPaise)}" +
                                            if (row.p.lastTime > 0) " · ${fmtDay(row.p.lastTime)}" else "",
                                    color = pal.soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            if (cat == null) Text("No category", color = pal.soft, fontSize = 13.sp)
                            else Column(horizontalAlignment = Alignment.End) {
                                Text(cat.name, color = pal.pine, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                if (row.p.auto) Text("auto-add", color = pal.soft, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp)); Hairline()
                    }
                }
            }
        }
    }

    edit?.let { row ->
        val p = row.p
        var catId by remember(p.key, p.type) { mutableStateOf(p.categoryId?.takeIf { id -> cats.any { it.id == id } }) }
        var auto by remember(p.key, p.type) { mutableStateOf(p.auto) }
        var others by remember(p.key, p.type) { mutableStateOf(false) }
        val past = remember(records, cats, p.key, p.type) { Payees.past(records, cats, p.key, p.type) }
        val list = cats.filter { it.type == p.type }
        val moveable = past.all.count { it.categoryId != catId }
        AppDialog(
            p.name, { edit = null }, "Save", {
                vm.setPayeeRule(p, catId, auto, others); edit = null
            },
            extra = if (p.categoryId != null) ({ TextAction("Remove rule", { vm.setPayeeRule(p, null, false, false); edit = null }, color = pal.expense) }) else null,
        ) {
            Text(
                if (p.type == 2) "Every payment to this payee goes to:" else "Every payment from this payee goes to:",
                color = pal.soft, fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            PickDropdown<Long?>(
                listOf<Long?>(null) + list.map { it.id }, catId,
                { id -> list.firstOrNull { it.id == id }?.name ?: "No category (no rule)" },
                Modifier.fillMaxWidth(),
            ) { catId = it }
            if (catId != null) {
                Spacer(Modifier.height(10.dp))
                ToggleLine("Add automatically, no review", auto) { auto = it }
                val nOther = past.inOther.count { it.categoryId != catId }
                if (nOther > 0) Text("$nOther past ${if (nOther == 1) "entry" else "entries"} now in “Other” will move here too.", color = pal.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                val nChosen = moveable - nOther
                if (nChosen > 0) ToggleLine("Also move $nChosen past ${if (nChosen == 1) "entry" else "entries"} already in another category", others) { others = it }
            } else Text("Pick a category above. Choosing “No category” and saving removes the rule.", color = pal.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ToggleLine(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        androidx.compose.material3.Switch(
            on, onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = pal.onPine, checkedTrackColor = pal.pine, checkedBorderColor = pal.pine,
                uncheckedThumbColor = pal.soft, uncheckedTrackColor = pal.bg, uncheckedBorderColor = pal.soft,
            ),
        )
    }
}
