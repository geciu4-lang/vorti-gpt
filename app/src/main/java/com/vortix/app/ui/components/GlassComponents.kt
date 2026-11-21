package com.vortix.app.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vortix.app.ui.theme.VortixGlassColors
import com.vortix.app.ui.theme.VortixOnSurfaceVariant
import java.util.Locale

object VortixGlassShapes {
    val Card = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    val Inner = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val Pill = androidx.compose.foundation.shape.RoundedCornerShape(50.dp)
}

/** Translucent charcoal surface with a top sheen and a 1dp diagonal specular edge. */
fun Modifier.glassSurface(
    shape: Shape = VortixGlassShapes.Card,
    fill: Color = VortixGlassColors.GlassFill,
    bright: Color = VortixGlassColors.SpecularBright,
    dim: Color = VortixGlassColors.SpecularDim,
): Modifier = this
    .clip(shape)
    .background(fill)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)))
    .border(1.dp, Brush.linearGradient(listOf(bright, dim)), shape)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier.fillMaxWidth().glassSurface().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    content = content,
)

@Composable
fun GlassInner(
    modifier: Modifier = Modifier,
    fill: Color = VortixGlassColors.InnerFill,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier.glassSurface(
        shape = VortixGlassShapes.Inner,
        fill = fill,
        bright = Color.White.copy(alpha = 0.10f),
        dim = Color.White.copy(alpha = 0.03f),
    ).padding(12.dp),
    content = content,
)

@Composable
fun LabelText(text: String, modifier: Modifier = Modifier) =
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.66.sp,
        color = VortixGlassColors.TextMuted,
    )

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(
        text = text,
        modifier = modifier,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = VortixGlassColors.TextPrimary,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    )

@Composable
fun MetricText(text: String, modifier: Modifier = Modifier) =
    Text(
        text = text,
        modifier = modifier,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        color = VortixGlassColors.TextPrimary,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
    )

/** Obsidian canvas with coral upper-right and emerald lower-left ambient light. */
@Composable
fun AmbientMeshBackground(
    scrollProvider: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val useBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier.fillMaxSize().background(VortixGlassColors.Obsidian)) {
        Canvas(
            Modifier.fillMaxSize().then(
                if (useBlur) Modifier.blur(48.dp, BlurredEdgeTreatment.Unbounded) else Modifier
            )
        ) {
            val scroll = scrollProvider()
            val radius = size.maxDimension * 0.55f
            val upperRight = Offset(size.width, size.height * 0.04f - scroll * 0.12f)
            val lowerLeft = Offset(0f, size.height * 0.96f - scroll * 0.07f)
            val alpha = if (useBlur) 0.30f else 0.26f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to VortixGlassColors.Coral.copy(alpha = alpha),
                    0.45f to VortixGlassColors.Coral.copy(alpha = alpha * 0.45f),
                    1f to Color.Transparent,
                    center = upperRight,
                    radius = radius,
                ),
                radius = radius,
                center = upperRight,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to VortixGlassColors.Emerald.copy(alpha = alpha * 0.85f),
                    0.45f to VortixGlassColors.Emerald.copy(alpha = alpha * 0.38f),
                    1f to Color.Transparent,
                    center = lowerLeft,
                    radius = radius,
                ),
                radius = radius,
                center = lowerLeft,
            )
        }
        content()
    }
}

enum class VortixStatus(val label: String) {
    Normal("NORMAL"),
    Warning("UYARI"),
    Measuring("ÖLÇÜLÜYOR"),
}

data class HeaderUi(
    val thermalSummary: String,
    val status: VortixStatus,
    val alertReason: String? = null,
)

@Composable
fun GlassBadge(status: VortixStatus, modifier: Modifier = Modifier) {
    val foreground = when (status) {
        VortixStatus.Normal -> VortixGlassColors.EmeraldSoft
        VortixStatus.Warning -> VortixGlassColors.Coral
        VortixStatus.Measuring -> VortixGlassColors.TextMuted
    }
    val glow = when (status) {
        VortixStatus.Normal -> VortixGlassColors.Emerald
        VortixStatus.Warning -> VortixGlassColors.Coral
        VortixStatus.Measuring -> VortixGlassColors.TextMuted
    }
    Text(
        text = "● ${status.label}",
        modifier = modifier
            .wrapContentWidth()
            .widthIn(min = 88.dp, max = 104.dp)
            .shadow(14.dp, VortixGlassShapes.Pill, ambientColor = glow, spotColor = glow)
            .glassSurface(VortixGlassShapes.Pill, fill = glow.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        color = foreground,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun VortixHeader(header: HeaderUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(40.dp)
                        .shadow(16.dp, VortixGlassShapes.Inner, ambientColor = VortixGlassColors.Emerald, spotColor = VortixGlassColors.Emerald)
                        .glassSurface(VortixGlassShapes.Inner, fill = VortixGlassColors.Emerald.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("ϟ", color = VortixGlassColors.EmeraldSoft, fontSize = 26.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                }
                Column(
                    modifier = Modifier.weight(1f).widthIn(min = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = "VORTIX",
                        color = VortixGlassColors.TextPrimary,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = header.thermalSummary,
                        color = VortixGlassColors.TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 0.25.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            GlassBadge(status = header.status)
        }
        if (!header.alertReason.isNullOrBlank()) {
            Text(
                text = header.alertReason,
                modifier = Modifier.padding(start = 50.dp, end = 4.dp),
                color = VortixGlassColors.Coral,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class VortixTab(val label: String) {
    Overview("Özet"),
    Controls("Kontrol"),
    Insights("Radar"),
}

@Composable
fun GlassDock(
    selected: VortixTab,
    onSelect: (VortixTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding()
            .fillMaxWidth()
            .height(60.dp)
            .shadow(24.dp, VortixGlassShapes.Pill, ambientColor = Color.Black, spotColor = Color.Black)
            .glassSurface(
                VortixGlassShapes.Pill,
                fill = VortixGlassColors.DockFill,
                bright = Color.White.copy(alpha = 0.20f),
                dim = Color.White.copy(alpha = 0.05f),
            )
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VortixTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                label = "vortix-tab-background",
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clip(VortixGlassShapes.Pill)
                    .background(background)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) VortixGlassColors.TextPrimary else VortixGlassColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold
                    else androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val fill = if (accent) VortixGlassColors.Emerald.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.07f)
    Box(
        modifier = modifier.heightIn(min = 44.dp)
            .glassSurface(
                VortixGlassShapes.Pill,
                fill = fill,
                bright = Color.White.copy(alpha = if (accent) 0.20f else 0.14f),
                dim = Color.White.copy(alpha = 0.04f),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> VortixGlassColors.TextMuted
                accent -> VortixGlassColors.EmeraldSoft
                else -> VortixGlassColors.TextPrimary
            },
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GlassSwitchVisual(checked: Boolean) {
    val track by animateColorAsState(
        targetValue = if (checked) VortixGlassColors.Emerald.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
        label = "glass-switch-track",
    )
    val x by animateDpAsState(targetValue = if (checked) 21.dp else 3.dp, label = "glass-switch-thumb")
    Box(Modifier.size(44.dp, 26.dp).clip(VortixGlassShapes.Pill).background(track)) {
        Box(Modifier.offset(x = x, y = 3.dp).size(20.dp).clip(CircleShape).background(VortixGlassColors.TextPrimary))
    }
}

@Composable
fun GlassToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    GlassInner(
        modifier = modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = VortixGlassColors.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                if (subtitle != null) LabelText(subtitle)
            }
            GlassSwitchVisual(checked)
        }
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill = if (selected) VortixGlassColors.Emerald.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f)
    Box(
        modifier = modifier.heightIn(min = 44.dp)
            .glassSurface(
                VortixGlassShapes.Inner,
                fill = fill,
                bright = Color.White.copy(alpha = if (selected) 0.20f else 0.10f),
                dim = Color.White.copy(alpha = 0.03f),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) VortixGlassColors.EmeraldSoft else VortixGlassColors.TextPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier) =
    Text(
        text = text,
        modifier = modifier.glassSurface(
            VortixGlassShapes.Pill,
            fill = Color.White.copy(alpha = 0.06f),
            bright = Color.White.copy(alpha = 0.12f),
            dim = Color.White.copy(alpha = 0.03f),
        ).padding(horizontal = 10.dp, vertical = 6.dp),
        color = VortixOnSurfaceVariant,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        maxLines = 1,
        softWrap = false,
    )

@Composable
fun GlassProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = VortixGlassColors.Emerald) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(VortixGlassShapes.Pill).background(Color.White.copy(alpha = 0.08f))) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                .shadow(8.dp, VortixGlassShapes.Pill, ambientColor = color, spotColor = color)
                .clip(VortixGlassShapes.Pill).background(color),
        )
    }
}

@Composable
fun TelemetryTile(label: String, value: String, modifier: Modifier = Modifier) =
    GlassInner(modifier, fill = VortixGlassColors.GlassFill) {
        LabelText(label)
        Spacer(Modifier.height(2.dp))
        MetricText(value)
    }

@Composable
fun ThermalRing(
    tempC: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    maxC: Float = 60f,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke * 1.5f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val sweep = 360f * (tempC / maxC).coerceIn(0f, 1f)
            drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(VortixGlassColors.Amber.copy(alpha = 0.25f), -90f, sweep, false, topLeft, arcSize,
                style = Stroke(stroke * 2.4f, cap = StrokeCap.Round))
            drawArc(VortixGlassColors.Amber, -90f, sweep, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            text = String.format(Locale.US, "%.1f°C", tempC),
            color = VortixGlassColors.TextPrimary,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun GlowLineChart(values: List<Float>, modifier: Modifier = Modifier, color: Color = VortixGlassColors.Emerald) {
    Canvas(modifier.fillMaxWidth().height(100.dp)) {
        val finiteValues = values.filter(Float::isFinite)
        if (finiteValues.size < 2) return@Canvas
        val low = finiteValues.minOrNull() ?: return@Canvas
        val high = finiteValues.maxOrNull() ?: return@Canvas
        val span = (high - low).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (finiteValues.size - 1)
        fun y(value: Float) = size.height * 0.88f - (value - low) / span * size.height * 0.76f
        val line = Path().apply {
            moveTo(0f, y(finiteValues.first()))
            for (index in 1 until finiteValues.size) {
                val x0 = (index - 1) * stepX
                val x1 = index * stepX
                val midpoint = (x0 + x1) / 2f
                cubicTo(midpoint, y(finiteValues[index - 1]), midpoint, y(finiteValues[index]), x1, y(finiteValues[index]))
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(line, color.copy(alpha = 0.25f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
