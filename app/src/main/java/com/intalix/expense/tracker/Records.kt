package com.intalix.expense.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@Composable
fun RecordsScreen(vm: Vm, onEdit: (Rec) -> Unit, onReview: () -> Unit) {
    val all by vm.records.collectAsState()
    val cats by vm.categories.collectAsState()
    val accs by vm.accounts.collectAsState()
    val pending by vm.pending.collectAsState()
    val m by vm.month.collectAsState()
    val rows = remember(all, m) { vm.monthRecords(all, m) }
    val income = rows.filter { it.type == 1 }.sumOf { it.amountPaise }
    val expense = rows.filter { it.type == 2 }.sumOf { it.amountPaise }
    val catMap = cats.associateBy { it.id }; val accMap = accs.associateBy { it.id }
    val cur = Prefs.currency(androidx.compose.ui.platform.LocalContext.current)

    Column(Modifier.fillMaxSize()) {
        MonthBar(vm)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Summary("Income", money(cur, income), pal.income, Modifier.weight(1f))
            Summary("Expense", money(cur, expense), pal.expense, Modifier.weight(1f))
            Summary("Balance", money(cur, income - expense), pal.soft, Modifier.weight(1f))
        }
        if (pending.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(pal.note)
                    .clickable(onClick = onReview).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.NotificationsActive, null, tint = pal.pine)
                Spacer(Modifier.width(12.dp))
                Text("${pending.size} detected payment${if (pending.size == 1) "" else "s"} to review", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color(0xFF3C3200).let { if (pal.dark) pal.ink else it })
                Icon(Icons.Default.ChevronRight, null, tint = pal.pine)
            }
        }
        if (rows.isEmpty()) {
            EmptyState(Icons.Default.Description, "Nothing recorded this month.\nTap + to add your first entry.", Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically))
        } else LazyColumn(Modifier.fillMaxSize()) {
            val byDay = rows.groupBy { dayStart(it.time) }.toSortedMap(compareByDescending { it })
            byDay.forEach { (day, list) ->
                item(key = "h$day") {
                    val net = list.sumOf { if (it.type == 1) it.amountPaise else -it.amountPaise }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Caps(fmtDay(day)); Caps(money(cur, net))
                    }
                    Hairline(Modifier.padding(horizontal = 20.dp))
                }
                items(list, key = { it.id }) { r ->
                    val c = catMap[r.categoryId]
                    Row(
                        Modifier.fillMaxWidth().clickable { onEdit(r) }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CatBadge(c?.name ?: "?")
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c?.name ?: "Unknown", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                            Text(
                                listOfNotNull(r.note.ifBlank { null }, accMap[r.accountId]?.name, fmtTime(r.time)).joinToString(" · "),
                                color = pal.soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (if (r.type == 1) "+" else "-") + cur + Money.format(r.amountPaise),
                            color = if (r.type == 1) pal.income else pal.expense, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Deliberately quiet: small caption with the value below it, no box. Long amounts shrink instead of wrapping. */
@Composable
private fun Summary(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = pal.soft, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(
            value, color = color.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
