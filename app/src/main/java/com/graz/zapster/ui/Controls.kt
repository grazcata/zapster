package com.graz.zapster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun Modifier.key(shape: Shape, container: Color, onClick: () -> Unit) =
    this.clip(shape)
        .background(container)
        .clickable(onClick = onClick)

/** A round icon key. Sized generously — this is used one-handed, at a distance. */
@Composable
fun KeyButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .key(CircleShape, container, onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize ?: (size * 0.45f)),
        )
    }
}

/** A round key with a short text label instead of an icon. */
@Composable
fun TextKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .size(size)
            .key(CircleShape, container, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** A wide pill button, for app and input shortcuts. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier.key(RoundedCornerShape(14.dp), container, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = tint,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
        )
    }
}

/**
 * A vertical two-way rocker (volume, channel) with its name between the halves,
 * mirroring the shape of a physical remote.
 */
@Composable
fun Rocker(
    upIcon: ImageVector,
    downIcon: ImageVector,
    label: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(44.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyButton(icon = upIcon, description = "$label up", onClick = onUp, size = 68.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        KeyButton(icon = downIcon, description = "$label down", onClick = onDown, size = 68.dp)
    }
}

/**
 * Directional pad: four arrows around a central OK key, laid out on a circle so
 * the whole cluster can be worked by thumb without looking at the screen.
 */
@Composable
fun DPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    upIcon: ImageVector,
    downIcon: ImageVector,
    leftIcon: ImageVector,
    rightIcon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    val arrow = size * 0.33f
    val arrowIcon = 48.dp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp)) {
            KeyButton(
                icon = upIcon,
                description = "Up",
                onClick = onUp,
                size = arrow,
                iconSize = arrowIcon,
                container = Color.Transparent,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            KeyButton(
                icon = downIcon,
                description = "Down",
                onClick = onDown,
                size = arrow,
                iconSize = arrowIcon,
                container = Color.Transparent,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            KeyButton(
                icon = leftIcon,
                description = "Left",
                onClick = onLeft,
                size = arrow,
                iconSize = arrowIcon,
                container = Color.Transparent,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            KeyButton(
                icon = rightIcon,
                description = "Right",
                onClick = onRight,
                size = arrow,
                iconSize = arrowIcon,
                container = Color.Transparent,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        TextKey(
            label = "OK",
            onClick = onOk,
            size = size * 0.33f,
            container = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Small section heading above a group of keys. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Evenly spaced horizontal group of keys. */
@Composable
fun KeyRow(
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
