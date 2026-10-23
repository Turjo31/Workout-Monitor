package com.kuet.gymtest.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuet.gymtest.Exercise
import com.kuet.gymtest.Tab
import com.kuet.gymtest.ui.theme.*
import dev.chrisbanes.haze.hazeSource

/** One solid warm backdrop shared by every non-camera screen. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    GlassBackdropProvider {
        val backdrop = LocalGlassBackdrop.current
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.matchParentSize().then(
                if (backdrop != null) Modifier.hazeSource(backdrop, key = GLASS_BACKGROUND) else Modifier
            )) {
                drawRect(Ivory)
            }
            content()
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Space.Lg),
    shape: Shape = WorkoutShapes.Card,
    fill: Color = GlassSurface,
    border: Color = GlassBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    // Keep native elevation at zero. Blur is applied to the captured backdrop,
    // and foreground controls are drawn afterwards at full sharpness.
    val frost = Brush.linearGradient(
        listOf(
            fill.copy(alpha = if (backdrop == null) (fill.alpha + .12f).coerceAtMost(1f) else .05f),
            fill.copy(alpha = if (backdrop == null) fill.alpha else .015f),
            fill.copy(alpha = if (backdrop == null) fill.alpha * .85f else .025f)
        )
    )
    val rim = Brush.linearGradient(
        listOf(border, border.copy(alpha = border.alpha * 0.45f))
    )
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, rim),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier.glassBackdrop(fill).background(frost).then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier).padding(padding),
            content = content
        )
    }
}

@Composable
fun WorkoutHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.Xl, vertical = Space.Lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Spacer(Modifier.width(Space.Sm))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(Space.Xs))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search exercises"
) {
    val focus = LocalFocusManager.current
    GlassCard(
        modifier = modifier,
        padding = PaddingValues(horizontal = Space.Lg),
        shape = WorkoutShapes.Search,
        fill = GlassSurface.copy(alpha = .80f)
    ) {
        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = MutedInk)
            Spacer(Modifier.width(Space.Md))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(vertical = Space.Md)
                    .semantics { contentDescription = "Search exercises" },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, color = MutedInk,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                }
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, "Clear search", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun WorkoutPrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 54.dp),
        enabled = enabled,
        shape = WorkoutShapes.Control,
        contentPadding = PaddingValues(horizontal = Space.Xl, vertical = Space.Md),
        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen, contentColor = Ink)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(Space.Sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun WorkoutFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = CircleShape,
        border = BorderStroke(1.dp, if (selected) Leaf else GlassBorder),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = GlassSurface,
            selectedContainerColor = ActionGreen,
            selectedLabelColor = Ink
        )
    )
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun ExerciseIcon(exercise: Exercise, modifier: Modifier = Modifier) {
    val icon = when {
        exercise.category == "Cardio" -> Icons.AutoMirrored.Filled.DirectionsRun
        exercise.muscleGroup == "Core" -> Icons.Default.SelfImprovement
        exercise.muscleGroup in listOf("Legs", "Glutes", "Hamstrings") -> Icons.Default.SportsGymnastics
        else -> Icons.Default.FitnessCenter
    }
    Box(
        modifier.size(62.dp).background(
            Brush.linearGradient(listOf(GlassSurface, ActionGreen.copy(alpha = .38f))),
            WorkoutShapes.Exercise
        ),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Forest, modifier = Modifier.size(30.dp)) }
}

@Composable
fun GlassExerciseRow(
    exercise: Exercise, onClick: () -> Unit, modifier: Modifier = Modifier,
    completed: Boolean = false, trailing: (@Composable () -> Unit)? = null
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(), onClick = onClick,
        shape = WorkoutShapes.Exercise, padding = PaddingValues(Space.Md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Md)) {
            ExerciseIcon(exercise)
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(exercise.muscleGroup, style = MaterialTheme.typography.bodySmall, color = MutedInk)
                Text("${exercise.defaultSets} sets · ${exercise.defaultReps} reps",
                    style = MaterialTheme.typography.bodySmall, color = MutedInk)
            }
            if (trailing != null) trailing()
            else Icon(
                if (completed) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                if (completed) "Completed today" else "Open exercise", tint = if (completed) Forest else MutedInk
            )
        }
    }
}

@Composable
fun GlassStatCard(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    GlassCard(modifier, padding = PaddingValues(Space.Md), shape = WorkoutShapes.Stat) {
        Icon(icon, null, tint = Forest, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(Space.Sm))
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, color = MutedInk, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun WeeklyActivity(days: List<Long>, counts: List<Int>, modifier: Modifier = Modifier) {
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
        days.forEachIndexed { index, _ ->
            val height by animateFloatAsState(counts[index].toFloat() / maxCount, label = "Activity")
            Column(Modifier.weight(1f).semantics {
                contentDescription = "${listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[index]}: ${counts[index]} workouts"
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(72.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth(.75f).height((12 + height * 60).dp)
                        .background(if (counts[index] > 0) Leaf else Color.White.copy(alpha = .5f), WorkoutShapes.Stat))
                }
                Spacer(Modifier.height(Space.Sm))
                Text(labels[index], style = MaterialTheme.typography.bodySmall, color = MutedInk)
            }
        }
    }
}

@Composable
fun WorkoutBottomNavigation(tab: Tab, onNavigate: (Tab) -> Unit) {
    Box(Modifier.navigationBarsPadding().padding(horizontal = Space.Lg, vertical = Space.Sm)) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(), padding = PaddingValues(Space.Xs),
            shape = WorkoutShapes.Navigation, fill = GlassSurface.copy(alpha = .82f)
        ) {
            NavigationBar(
                containerColor = Color.Transparent, tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Tab.entries.forEach { item ->
                    val selectedColor by animateColorAsState(
                        if (tab == item) Peach else Color.Transparent, label = "Navigation"
                    )
                    NavigationBarItem(
                        selected = tab == item, onClick = { onNavigate(item) },
                        icon = { Icon(item.icon, null, Modifier.size(22.dp)) },
                        label = { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = selectedColor, selectedIconColor = Ink, selectedTextColor = Ink,
                            unselectedIconColor = MutedInk, unselectedTextColor = MutedInk
                        )
                    )
                }
            }
        }
    }
}
