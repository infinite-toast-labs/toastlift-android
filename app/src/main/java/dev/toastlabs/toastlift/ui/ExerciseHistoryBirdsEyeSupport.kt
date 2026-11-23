package dev.toastlabs.toastlift.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.toastlabs.toastlift.data.ExerciseHistoryEntry
import dev.toastlabs.toastlift.data.ExerciseHistorySet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ExerciseHistoryViewMode(val label: String) {
    BirdsEye("Bird\u2019s-eye"),
    Details("Details"),
}

enum class BirdsEyeSortOrder(val label: String) {
    OldestFirst("Oldest"),
    NewestFirst("Newest"),
}

internal const val BIRDS_EYE_PAGE_SIZE = 5

internal enum class OverloadDeltaKind {
    WeightUp,
    RepsUp,
    Maintained,
    Decreased,
    Baseline,
}

internal data class ExerciseHistoryBirdsEyeRow(
    val sessionNumber: Int,
    val dateLabel: String,
    val weightLabel: String,
    val repsLabel: String,
    val perSetWeightsLabel: String?,
    val deltaLabel: String?,
    val deltaKind: OverloadDeltaKind,
    val hasPersonalRecord: Boolean,
    val isWeightJump: Boolean,
)

internal data class BirdsEyePagination(
    val pages: List<List<ExerciseHistoryBirdsEyeRow>>,
    val defaultPageIndex: Int,
)

internal data class ExerciseHistoryBirdsEyeSummary(
    val latestLabel: String,
    val bestWeightLabel: String,
    val netChangeLabel: String?,
    val netChangePositive: Boolean,
)

private const val WEIGHT_EPSILON = 0.05

internal fun formatBirdsEyeWeightValue(weight: Double): String {
    val rounded = (weight * 10.0).roundToInt() / 10.0
    return if (abs(rounded - rounded.roundToInt().toDouble()) < 0.001) {
        rounded.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

private fun chronologicalWorkingSets(entry: ExerciseHistoryEntry): List<ExerciseHistorySet> =
    entry.workingSets.sortedBy { it.setNumber }

private fun sessionPrimaryWeight(sets: List<ExerciseHistorySet>): Double =
    sets.mapNotNull { it.weight?.takeIf { w -> w > 0.0 } }.maxOrNull() ?: 0.0

private fun sessionTotalReps(sets: List<ExerciseHistorySet>): Int =
    sets.sumOf { it.reps ?: 0 }

private fun formatSessionWeightSummary(sets: List<ExerciseHistorySet>): Pair<String, String?> {
    val weights = sets.map { it.weight?.takeIf { w -> w > 0.0 } ?: 0.0 }
    val positiveWeights = weights.filter { it > 0.0 }
    if (positiveWeights.isEmpty()) {
        return "BW" to null
    }
    val minWeight = weights.minOrNull() ?: 0.0
    val maxWeight = weights.maxOrNull() ?: 0.0
    val isUniform = abs(maxWeight - minWeight) < WEIGHT_EPSILON
    if (isUniform) {
        return "${formatBirdsEyeWeightValue(maxWeight)} lb" to null
    }
    val rangeLabel = if (minWeight <= WEIGHT_EPSILON) {
        "BW\u2013${formatBirdsEyeWeightValue(maxWeight)} lb"
    } else {
        "${formatBirdsEyeWeightValue(minWeight)}\u2013${formatBirdsEyeWeightValue(maxWeight)} lb"
    }
    val perSetLabel = weights.joinToString(" / ") { w ->
        if (w <= WEIGHT_EPSILON) "BW" else formatBirdsEyeWeightValue(w)
    } + " lb"
    return rangeLabel to perSetLabel
}

private fun formatSessionReps(sets: List<ExerciseHistorySet>): String {
    if (sets.isEmpty()) return "--"
    return sets.joinToString(" / ") { it.reps?.toString() ?: "--" }
}

private fun formatBirdsEyeDate(
    occurredAtUtc: String,
    includeYear: Boolean,
    zoneId: ZoneId,
): String = runCatching {
    val pattern = if (includeYear) "MMM d \u0027\u0027yy" else "MMM d"
    Instant.parse(occurredAtUtc)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}.getOrElse {
    occurredAtUtc.substringBefore("T")
}

internal fun buildExerciseHistoryBirdsEyeRows(
    entries: List<ExerciseHistoryEntry>,
    sortOrder: BirdsEyeSortOrder = BirdsEyeSortOrder.OldestFirst,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ExerciseHistoryBirdsEyeRow> {
    if (entries.isEmpty()) return emptyList()

    // ExerciseHistoryDetail.entries is newest-first; reverse for chronological overload comparison.
    val chronological = entries.asReversed()
    val distinctYears = chronological.mapNotNull { entry ->
        runCatching { Instant.parse(entry.workoutOccurredAtUtc).atZone(zoneId).year }.getOrNull()
    }.toSet()
    val includeYear = distinctYears.size > 1

    var previousWeight: Double? = null
    var previousTotalReps: Int? = null

    val rows = chronological.mapIndexed { index, entry ->
        val sets = chronologicalWorkingSets(entry)
        val primaryWeight = sessionPrimaryWeight(sets)
        val totalReps = sessionTotalReps(sets)
        val (weightLabel, perSetWeightsLabel) = formatSessionWeightSummary(sets)
        val repsLabel = formatSessionReps(sets)

        val (deltaKind, deltaLabel) = if (previousWeight == null || previousTotalReps == null) {
            OverloadDeltaKind.Baseline to null
        } else {
            val weightDiff = primaryWeight - previousWeight!!
            val repDiff = totalReps - previousTotalReps!!
            when {
                weightDiff > WEIGHT_EPSILON -> {
                    OverloadDeltaKind.WeightUp to "+${formatBirdsEyeWeightValue(weightDiff)} lb"
                }
                weightDiff < -WEIGHT_EPSILON -> {
                    OverloadDeltaKind.Decreased to "-${formatBirdsEyeWeightValue(abs(weightDiff))} lb"
                }
                repDiff > 0 -> {
                    val unit = if (repDiff == 1) "rep" else "reps"
                    OverloadDeltaKind.RepsUp to "+$repDiff $unit"
                }
                repDiff < 0 -> {
                    val unit = if (repDiff == -1) "rep" else "reps"
                    OverloadDeltaKind.Decreased to "$repDiff $unit"
                }
                else -> OverloadDeltaKind.Maintained to "="
            }
        }

        previousWeight = primaryWeight
        previousTotalReps = totalReps

        ExerciseHistoryBirdsEyeRow(
            sessionNumber = index + 1,
            dateLabel = formatBirdsEyeDate(entry.workoutOccurredAtUtc, includeYear, zoneId),
            weightLabel = weightLabel,
            repsLabel = repsLabel,
            perSetWeightsLabel = perSetWeightsLabel,
            deltaLabel = deltaLabel,
            deltaKind = deltaKind,
            hasPersonalRecord = entry.hasPersonalRecord,
            isWeightJump = deltaKind == OverloadDeltaKind.WeightUp,
        )
    }

    return when (sortOrder) {
        BirdsEyeSortOrder.NewestFirst -> rows.asReversed()
        BirdsEyeSortOrder.OldestFirst -> rows
    }
}

/**
 * Splits rows into pages of at most [pageSize] workouts, anchored to the most recent workouts:
 * - In [BirdsEyeSortOrder.OldestFirst] (default), chunks from the newest end so the last page
 *   holds the most recent up to [pageSize] workouts and earlier pages hold older workouts;
 *   [BirdsEyePagination.defaultPageIndex] points to the last page so swiping back reveals older history.
 * - In [BirdsEyeSortOrder.NewestFirst], chunks from the newest end (front) and opens on page 0.
 */
internal fun paginateBirdsEyeRows(
    rows: List<ExerciseHistoryBirdsEyeRow>,
    sortOrder: BirdsEyeSortOrder,
    pageSize: Int = BIRDS_EYE_PAGE_SIZE,
): BirdsEyePagination {
    require(pageSize > 0) { "pageSize must be positive" }
    if (rows.isEmpty()) {
        return BirdsEyePagination(pages = emptyList(), defaultPageIndex = 0)
    }
    return when (sortOrder) {
        BirdsEyeSortOrder.OldestFirst -> {
            val pages = mutableListOf<List<ExerciseHistoryBirdsEyeRow>>()
            var end = rows.size
            while (end > 0) {
                val start = maxOf(0, end - pageSize)
                pages.add(rows.subList(start, end))
                end = start
            }
            pages.reverse()
            BirdsEyePagination(pages = pages, defaultPageIndex = pages.lastIndex)
        }
        BirdsEyeSortOrder.NewestFirst -> {
            val pages = rows.chunked(pageSize)
            BirdsEyePagination(pages = pages, defaultPageIndex = 0)
        }
    }
}

internal fun buildExerciseHistoryBirdsEyeSummary(
    entries: List<ExerciseHistoryEntry>,
): ExerciseHistoryBirdsEyeSummary? {
    if (entries.isEmpty()) return null
    val newestSets = chronologicalWorkingSets(entries.first())
    val oldestSets = chronologicalWorkingSets(entries.last())

    val newestWeight = sessionPrimaryWeight(newestSets)
    val oldestWeight = sessionPrimaryWeight(oldestSets)
    val newestReps = sessionTotalReps(newestSets)
    val oldestReps = sessionTotalReps(oldestSets)

    val (latestWeightLabel, _) = formatSessionWeightSummary(newestSets)
    val latestRepsCompact = newestSets.joinToString("/") { it.reps?.toString() ?: "-" }
    val latestLabel = "$latestWeightLabel \u00B7 $latestRepsCompact"

    val bestWeight = entries.maxOfOrNull { sessionPrimaryWeight(chronologicalWorkingSets(it)) } ?: 0.0
    val bestWeightLabel = if (bestWeight > WEIGHT_EPSILON) {
        "${formatBirdsEyeWeightValue(bestWeight)} lb"
    } else {
        "BW"
    }

    val (netChangeLabel, netChangePositive) = if (entries.size < 2) {
        null to false
    } else {
        val weightDiff = newestWeight - oldestWeight
        val repDiff = newestReps - oldestReps
        when {
            weightDiff > WEIGHT_EPSILON -> "+${formatBirdsEyeWeightValue(weightDiff)} lb" to true
            weightDiff < -WEIGHT_EPSILON -> "-${formatBirdsEyeWeightValue(abs(weightDiff))} lb" to false
            repDiff > 0 -> "+$repDiff ${if (repDiff == 1) "rep" else "reps"}" to true
            repDiff < 0 -> "$repDiff ${if (repDiff == -1) "rep" else "reps"}" to false
            else -> "Steady" to false
        }
    }

    return ExerciseHistoryBirdsEyeSummary(
        latestLabel = latestLabel,
        bestWeightLabel = bestWeightLabel,
        netChangeLabel = netChangeLabel,
        netChangePositive = netChangePositive,
    )
}

@Composable
internal fun ExerciseHistoryViewModeToggle(
    selected: ExerciseHistoryViewMode,
    enabled: Boolean,
    onSelect: (ExerciseHistoryViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val trackContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f).compositeOver(surface)
    val trackBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(trackContainer)
            .border(BorderStroke(1.dp, trackBorder), RoundedCornerShape(50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseHistoryViewMode.entries.forEach { mode ->
            val isSelected = selected == mode
            val pillColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }.copy(alpha = if (enabled) 1f else 0.48f)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(pillColor)
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(mode) }
                    .semantics {
                        this.selected = isSelected
                        contentDescription = "${mode.label} view"
                    }
                    .heightIn(min = 30.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun ExerciseHistoryBirdsEyeTable(
    entries: List<ExerciseHistoryEntry>,
    sortOrder: BirdsEyeSortOrder,
    onToggleSortOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(entries, sortOrder) {
        buildExerciseHistoryBirdsEyeRows(entries = entries, sortOrder = sortOrder)
    }
    val pagination = remember(rows, sortOrder) {
        paginateBirdsEyeRows(rows = rows, sortOrder = sortOrder)
    }
    val summary = remember(entries) {
        buildExerciseHistoryBirdsEyeSummary(entries = entries)
    }
    val uiColors = LocalUiColors.current
    val statusColors = LocalStatusColors.current

    val pageCount = pagination.pages.size
    val pagerState = rememberPagerState(
        initialPage = pagination.defaultPageIndex.coerceIn(0, maxOf(0, pageCount - 1)),
        pageCount = { maxOf(1, pageCount) },
    )

    LaunchedEffect(entries, sortOrder, pagination.defaultPageIndex) {
        if (pageCount > 0) {
            val target = pagination.defaultPageIndex.coerceIn(0, pageCount - 1)
            if (pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (summary != null && entries.size >= 2) {
            BirdsEyeSummaryStrip(
                summary = summary,
                positiveColor = statusColors.surge,
            )
        }

        if (pagination.pages.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top,
            ) { pageIndex ->
                val pageRows = pagination.pages.getOrElse(pageIndex) { emptyList() }
                BirdsEyePageCard(
                    rows = pageRows,
                    sortOrder = sortOrder,
                    onToggleSortOrder = onToggleSortOrder,
                    uiColors = uiColors,
                    statusColors = statusColors,
                )
            }

            if (pageCount > 1) {
                BirdsEyePageIndicatorDots(
                    pageCount = pageCount,
                    currentPage = pagerState.currentPage,
                )
            }
        }
    }
}

@Composable
private fun BirdsEyePageCard(
    rows: List<ExerciseHistoryBirdsEyeRow>,
    sortOrder: BirdsEyeSortOrder,
    onToggleSortOrder: () -> Unit,
    uiColors: UiColors,
    statusColors: StatusColors,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, uiColors.chromeBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Column header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val sortArrow = if (sortOrder == BirdsEyeSortOrder.NewestFirst) "\u2193" else "\u2191"
                Row(
                    modifier = Modifier
                        .weight(0.95f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClick = onToggleSortOrder)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "WORKOUT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp,
                    )
                    Text(
                        text = sortArrow,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "WEIGHT",
                    modifier = Modifier.weight(1.0f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = "REPS",
                    modifier = Modifier.weight(1.3f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    letterSpacing = 0.6.sp,
                )
            }

            HorizontalDivider(color = uiColors.chromeDivider)

            rows.forEachIndexed { index, row ->
                val rowHighlight = if (row.isWeightJump) {
                    statusColors.surge.copy(alpha = if (LocalToastLiftIsDarkTheme.current) 0.06f else 0.05f)
                } else {
                    Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(rowHighlight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Subtle left accent bar on weight-jump milestones
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(
                                if (row.isWeightJump) statusColors.surge.copy(alpha = 0.85f) else Color.Transparent,
                            ),
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 11.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Workout column: date + #N + optional PR dot
                        Column(
                            modifier = Modifier.weight(0.95f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = row.dateLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (row.hasPersonalRecord) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary),
                                    )
                                }
                            }
                            Text(
                                text = "#${row.sessionNumber}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                maxLines = 1,
                            )
                        }

                        // Weight column (shrink font slightly for wide decimal ranges like "92.5–107.5 lb")
                        val weightFontSize = if (row.weightLabel.length > 9) 12.sp else 14.sp
                        Text(
                            text = row.weightLabel,
                            modifier = Modifier.weight(1.0f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = MonoFamily,
                                fontSize = weightFontSize,
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = if (row.isWeightJump) {
                                statusColors.surge
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false,
                        )

                        // Reps column (+ optional delta & mixed-weight breakdown)
                        Column(
                            modifier = Modifier
                                .weight(1.3f)
                                .padding(start = 8.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = row.repsLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                            if (row.perSetWeightsLabel != null) {
                                Text(
                                    text = row.perSetWeightsLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                    textAlign = TextAlign.End,
                                )
                            }
                            row.deltaLabel?.let { delta ->
                                BirdsEyeDeltaBadge(
                                    label = delta,
                                    kind = row.deltaKind,
                                    positiveColor = statusColors.surge,
                                )
                            }
                        }
                    }
                }

                if (index < rows.lastIndex) {
                    HorizontalDivider(color = uiColors.chromeDivider.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
private fun BirdsEyePageIndicatorDots(
    pageCount: Int,
    currentPage: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isActive) 18.dp else 6.dp,
                label = "birdsEyeDotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                },
                label = "birdsEyeDotColor",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun BirdsEyeSummaryStrip(
    summary: ExerciseHistoryBirdsEyeSummary,
    positiveColor: Color,
) {
    val uiColors = LocalUiColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, uiColors.chromeBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BirdsEyeSummaryMetric(
                label = "LATEST",
                value = summary.latestLabel,
                modifier = Modifier.weight(1.55f),
                alignment = Alignment.Start,
            )
            BirdsEyeSummaryMetric(
                label = "BEST",
                value = summary.bestWeightLabel,
                modifier = Modifier.weight(0.75f),
                alignment = Alignment.CenterHorizontally,
            )
            summary.netChangeLabel?.let { net ->
                BirdsEyeSummaryMetric(
                    label = "PROGRESSION",
                    value = net,
                    valueColor = if (summary.netChangePositive) positiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.95f),
                    alignment = Alignment.End,
                )
            }
        }
    }
}

@Composable
private fun BirdsEyeSummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MonoFamily,
                fontSize = if (value.length > 15) 11.sp else 12.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BirdsEyeDeltaBadge(
    label: String,
    kind: OverloadDeltaKind,
    positiveColor: Color,
) {
    val isPositive = kind == OverloadDeltaKind.WeightUp || kind == OverloadDeltaKind.RepsUp
    val textColor = when {
        isPositive -> positiveColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }
    val backgroundColor = when (kind) {
        OverloadDeltaKind.WeightUp -> positiveColor.copy(alpha = 0.16f)
        OverloadDeltaKind.RepsUp -> positiveColor.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            fontWeight = if (isPositive) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
        )
    }
}
