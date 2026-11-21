package com.intalix.expense.tracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

// ------------------------------------------------------------------ palette ("paper & ink")

@Immutable
class Pal(
    val dark: Boolean,
    val bg: Color,        // paper
    val card: Color,      // raised paper
    val ink: Color,       // main text
    val soft: Color,      // secondary text
    val line: Color,      // hairlines
    val pine: Color,      // brand / outlines / primary action
    val onPine: Color,
    val sage: Color,      // quiet filled tiles
    val income: Color,
    val expense: Color,
    val gold: Color,      // floating add button
    val note: Color,      // note field tint
)

val LightPal = Pal(
    false, bg = Color(0xFFF7F1E3), card = Color(0xFFFFFCF3), ink = Color(0xFF0F3D32), soft = Color(0xFF6C867A),
    line = Color(0xFFD9D4BF), pine = Color(0xFF14604C), onPine = Color(0xFFFFF8E6), sage = Color(0xFFDCE7D6),
    income = Color(0xFF2B7A4B), expense = Color(0xFFC4472B), gold = Color(0xFFF2B632), note = Color(0xFFFBEFC4),
)
val DarkPal = Pal(
    true, bg = Color(0xFF10201B), card = Color(0xFF18302A), ink = Color(0xFFF1EAD6), soft = Color(0xFF9DB5A8),
    line = Color(0xFF2C4A40), pine = Color(0xFF6FD1A6), onPine = Color(0xFF0C2A20), sage = Color(0xFF24443B),
    income = Color(0xFF62C98B), expense = Color(0xFFF0806A), gold = Color(0xFFF2B632), note = Color(0xFF33402B),
)

val LocalPal = staticCompositionLocalOf { LightPal }
val pal: Pal @Composable @ReadOnlyComposable get() = LocalPal.current

// ------------------------------------------------------------------ type

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Quick = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.quicksand, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)
val BrushFont = FontFamily(Font(R.font.brush))

private val base = Typography()
private fun TextStyle.q() = copy(fontFamily = Quick)
private val AppTypography = Typography(
    displayLarge = base.displayLarge.q(), displayMedium = base.displayMedium.q(), displaySmall = base.displaySmall.q(),
    headlineLarge = base.headlineLarge.q(), headlineMedium = base.headlineMedium.q(), headlineSmall = base.headlineSmall.q(),
    titleLarge = base.titleLarge.q(), titleMedium = base.titleMedium.q(), titleSmall = base.titleSmall.q(),
    bodyLarge = base.bodyLarge.q(), bodyMedium = base.bodyMedium.q(), bodySmall = base.bodySmall.q(),
    labelLarge = base.labelLarge.q(), labelMedium = base.labelMedium.q(), labelSmall = base.labelSmall.q(),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.pine, onPrimary = p.onPine, secondary = p.sage, onSecondary = p.ink, background = p.bg, onBackground = p.ink,
            surface = p.card, onSurface = p.ink, surfaceVariant = p.sage, onSurfaceVariant = p.soft, outline = p.line, outlineVariant = p.line,
            primaryContainer = p.sage, onPrimaryContainer = p.ink, secondaryContainer = p.sage, onSecondaryContainer = p.ink,
            surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.sage, surfaceContainerLow = p.card,
        )
    } else {
        lightColorScheme(
            primary = p.pine, onPrimary = p.onPine, secondary = p.sage, onSecondary = p.ink, background = p.bg, onBackground = p.ink,
            surface = p.card, onSurface = p.ink, surfaceVariant = p.sage, onSurfaceVariant = p.soft, outline = p.line, outlineVariant = p.line,
            primaryContainer = p.sage, onPrimaryContainer = p.ink, secondaryContainer = p.sage, onSecondaryContainer = p.ink,
            surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.sage, surfaceContainerLow = p.card,
        )
    }
    CompositionLocalProvider(LocalPal provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            Surface(Modifier.fillMaxSize(), color = p.bg, contentColor = p.ink) {
                Box(Modifier.systemBarsPadding()) { content() }
            }
        }
    }
}

// ------------------------------------------------------------------ formatting

fun fmtDay(t: Long): String = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(t))
fun fmtTime(t: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))
fun fmtMonth(m: MonthKey): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(m.start()))

/** "-₹35", "₹1,200", "+₹350" — the sign always comes before the currency symbol. */
fun money(cur: String, paise: Long, plus: Boolean = false): String =
    (if (paise < 0) "-" else if (plus) "+" else "") + cur + Money.format(Math.abs(paise))

// ------------------------------------------------------------------ categories

fun catIcon(name: String): ImageVector = when (name.lowercase()) {
    "food" -> Icons.Default.Restaurant; "car" -> Icons.Default.DirectionsCar; "transportation" -> Icons.Default.DirectionsBus
    "shopping" -> Icons.Default.ShoppingCart; "bills" -> Icons.Default.Receipt; "home" -> Icons.Default.Home
    "health" -> Icons.Default.LocalHospital; "entertainment" -> Icons.Default.Movie; "clothing" -> Icons.Default.Checkroom
    "education" -> Icons.Default.School; "electronics" -> Icons.Default.Devices; "insurance" -> Icons.Default.Shield
    "sport" -> Icons.Default.FitnessCenter; "tax" -> Icons.Default.AccountBalance; "telephone" -> Icons.Default.Phone
    "baby" -> Icons.Default.ChildCare; "beauty" -> Icons.Default.Spa; "social" -> Icons.Default.Groups
    "salary" -> Icons.Default.Payments; "awards" -> Icons.Default.EmojiEvents; "coupons" -> Icons.Default.LocalOffer
    "grants" -> Icons.Default.CardGiftcard; "lottery" -> Icons.Default.Casino; "refunds" -> Icons.AutoMirrored.Filled.Undo
    "rental" -> Icons.Default.HomeWork; "sale" -> Icons.Default.Sell
    else -> Icons.Default.Category
}

private val catPalette = listOf(0xFF14604C, 0xFFC4472B, 0xFFD18F12, 0xFF3E6FA3, 0xFF8A4F7D, 0xFF2F8F83, 0xFFB8456B, 0xFF6B7F3A, 0xFF5F7385)
fun colorFor(s: String): Color = Color(catPalette[Math.floorMod(s.hashCode(), catPalette.size)])

/** Two-tone badge: soft tinted disc, thin ring and a glyph in the same hue. */
@Composable
fun CatBadge(name: String, size: Int = 42, selected: Boolean = false) {
    val base = colorFor(name)
    val c = if (pal.dark) lerp(base, Color.White, 0.35f) else base
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c.copy(alpha = if (selected) 0.30f else 0.16f))
            .border(if (selected) 2.dp else 1.dp, c.copy(alpha = if (selected) 1f else 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(catIcon(name), null, tint = c, modifier = Modifier.size((size * 0.5).dp)) }
}

// ------------------------------------------------------------------ building blocks

fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

/** Small spaced-out uppercase label. */
@Composable
fun Caps(text: String, modifier: Modifier = Modifier, color: Color = pal.soft) =
    Text(text.uppercase(), modifier, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, maxLines = 1)

@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(pal.line))

/** Outlined paper box (the signature element). */
@Composable
fun OutBox(
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, stroke: Dp = 1.5.dp, fill: Color = pal.card,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp), borderColor: Color = pal.pine.copy(alpha = 0.55f),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.clip(shape).background(fill).border(BorderStroke(stroke, borderColor), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, filled: Boolean = true, color: Color = pal.pine) {
    val shape = RoundedCornerShape(50)
    val bg = if (filled) (if (enabled) color else pal.line) else Color.Transparent
    val fg = if (filled) (if (enabled) pal.onPine else pal.soft) else (if (enabled) color else pal.soft)
    Box(
        modifier.clip(shape).background(bg).then(if (!filled) Modifier.border(1.5.dp, if (enabled) color else pal.line, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1) }
}

@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = pal.pine, enabled: Boolean = true) =
    Text(
        label.uppercase(), modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        color = if (enabled) color else pal.soft, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp,
    )

@Composable
fun RoundIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Int = 0) {
    Box(modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = pal.ink)
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp).defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                .clip(CircleShape).background(pal.expense),
            contentAlignment = Alignment.Center,
        ) { Text("$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

/** Two-or-more option pill selector. */
@Composable
fun Segment(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, activeColor: Color = pal.pine) {
    val shape = RoundedCornerShape(50)
    Row(modifier.clip(shape).border(1.5.dp, activeColor.copy(alpha = 0.6f), shape).padding(3.dp)) {
        options.forEach { (id, label) ->
            val on = id == selected
            Box(
                Modifier.weight(1f).clip(shape).background(if (on) activeColor else Color.Transparent).clickable { onSelect(id) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) pal.onPine else pal.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape).background(if (selected) pal.pine else Color.Transparent)
            .border(1.5.dp, if (selected) pal.pine else pal.line, shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text(label, color = if (selected) pal.onPine else pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(items: List<Pair<Long, String>>, selected: Long?, onPick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, name) -> Chip(name, id == selected) { onPick(id) } }
    }
}

@Composable
fun AppField(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false, error: Boolean = false, singleLine: Boolean = true,
) {
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value, onChange, modifier, singleLine = singleLine,
        textStyle = TextStyle(color = pal.ink, fontFamily = Quick, fontWeight = FontWeight.Medium, fontSize = 17.sp),
        cursorBrush = SolidColor(pal.pine),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().clip(shape).background(pal.card).border(1.5.dp, if (error) pal.expense else pal.pine.copy(alpha = 0.5f), shape).padding(14.dp, 12.dp)) {
                if (value.isEmpty()) Text(hint, color = pal.soft, fontSize = 17.sp)
                inner()
            }
        },
    )
}

@Composable
fun AppDialog(
    title: String, onDismiss: () -> Unit, confirm: String?, onConfirm: () -> Unit = {}, confirmEnabled: Boolean = true,
    dismiss: String? = "Cancel", extra: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.clip(shape).background(pal.card).border(1.5.dp, pal.pine.copy(alpha = 0.4f), shape).padding(20.dp)) {
            Text(title, fontFamily = BrushFont, fontSize = 26.sp, color = pal.ink)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (extra != null) { extra(); Spacer(Modifier.weight(1f)) }
                if (dismiss != null) TextAction(dismiss, onDismiss, color = pal.soft)
                if (confirm != null) { Spacer(Modifier.width(6.dp)); PillButton(confirm, onConfirm, enabled = confirmEnabled) }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, sub: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!v) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(sub, color = pal.soft, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            v, onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = pal.onPine, checkedTrackColor = pal.pine, checkedBorderColor = pal.pine,
                uncheckedThumbColor = pal.soft, uncheckedTrackColor = pal.bg, uncheckedBorderColor = pal.soft,
            ),
        )
    }
}

/** Heading for a full-screen page with a back arrow. */
@Composable
fun PageTop(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    BackHandler(onBack = onBack)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Text(title, Modifier.weight(1f).padding(start = 6.dp), fontFamily = BrushFont, fontSize = 28.sp, maxLines = 1)
        actions()
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(72.dp), tint = pal.line)
        Spacer(Modifier.height(10.dp))
        Text(text, color = pal.soft, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun MonthBar(vm: Vm) {
    val m = vm.month.collectAsState().value
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.Default.ChevronLeft, "Previous month", { vm.month.value = m.plus(-1) })
        Text(fmtMonth(m), Modifier.weight(1f), fontFamily = BrushFont, fontSize = 28.sp, textAlign = TextAlign.Center, maxLines = 1)
        RoundIconButton(Icons.Default.ChevronRight, "Next month", { vm.month.value = m.plus(1) })
    }
}

/** Compact dropdown: a small pill showing the current choice; tapping opens a menu of [options]. */
@Composable
fun <T> PickDropdown(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(shape).border(1.5.dp, pal.line, shape).clickable { open = true }.padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(selected), Modifier.weight(1f), color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, null, tint = pal.soft, modifier = Modifier.size(22.dp))
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = pal.card) {
            options.forEach { o ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label(o), color = pal.ink, fontWeight = if (o == selected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp) },
                    onClick = { open = false; onPick(o) },
                )
            }
        }
    }
}
