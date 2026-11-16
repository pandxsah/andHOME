package app.andhome.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-style grouped settings building blocks shared by Customize and Control Center. */

internal val Accent = Color(0xFF0A84FF)

@Composable
internal fun SettingsGroup(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        if (title != null) Text(title.uppercase(), Modifier.padding(start = 16.dp, bottom = 7.dp),
            fontSize = 12.sp, letterSpacing = .6.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .82f)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
        if (footer != null) Text(footer, Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
    }
}

@Composable
internal fun IconBadge(icon: ImageVector, tint: Color, size: Int = 30) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .26f).dp)).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * .62f).dp), tint = Color.White)
    }
}

@Composable
internal fun SettingsRow(label: String, icon: ImageVector? = null, tint: Color = Accent, value: String? = null,
    divider: Boolean = true, onClick: (() -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { IconBadge(icon, tint); Spacer(Modifier.width(14.dp)) }
            Text(label, Modifier.weight(1f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) Text(value, Modifier.padding(start = 8.dp).widthIn(max = 170.dp), fontSize = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            if (trailing != null) trailing()
            else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .35f))
        }
        if (divider) HorizontalDivider(Modifier.padding(start = if (icon != null) 60.dp else 16.dp),
            thickness = .6.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
    }
}

@Composable
internal fun SettingsSwitch(label: String, checked: Boolean, icon: ImageVector? = null, tint: Color = Accent,
    divider: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingsRow(label, icon, tint, divider = divider, onClick = { onChange(!checked) }) {
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White,
            checkedBorderColor = Color.Transparent))
    }
}

/** iOS-style segmented control. */
@Composable
internal fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)).padding(2.dp)) {
        options.forEach { option ->
            val on = option == selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .background(if (on) MaterialTheme.colorScheme.onSurface.copy(alpha = .22f) else Color.Transparent)
                .clickable { onSelect(option) }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text(label(option), fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun SegmentedRow(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = .6.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
}

/** Round toggle used in Control Center tiles. */
@Composable
internal fun RoundToggle(icon: ImageVector, on: Boolean, onColor: Color, description: String, onClick: () -> Unit) {
    Box(Modifier.size(58.dp).clip(CircleShape).background(if (on) onColor else Color.White.copy(alpha = .16f))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(28.dp), tint = Color.White)
    }
}
