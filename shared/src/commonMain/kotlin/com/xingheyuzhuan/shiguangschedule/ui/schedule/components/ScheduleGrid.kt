package com.xingheyuzhuan.shiguangschedule.ui.schedule.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xingheyuzhuan.shiguangschedule.data.model.StaggeredSchedule
import com.xingheyuzhuan.shiguangschedule.data.model.schedule_style.ScheduleModeProto
import com.xingheyuzhuan.shiguangschedule.ui.schedule.model.MergedCourseBlock
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringArrayResource
import hufeschedule.shared.generated.resources.Res
import hufeschedule.shared.generated.resources.week_days_short_names
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@Composable
fun ScheduleGrid(
    state: ScheduleGridState,
    viewState: ScheduleGridViewState,
    actions: ScheduleGridActions,
    style: ScheduleGridStyleComposed,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        val density = LocalDensity.current

        LaunchedEffect(viewState.mergedCourses) {
            state.resetAllStates()
            actions.onHoldStateChanged(false)
        }

        val pageTextColor = style.pageTextColor ?: MaterialTheme.colorScheme.onSurface
        val pageSubTextColor = pageTextColor.copy(alpha = 0.7f)
        val weekDays = stringArrayResource(Res.array.week_days_short_names).toList()
        val reorderedWeekDays = rearrangeDays(weekDays, viewState.firstDayOfWeek)
        val displayDays = if (viewState.showWeekends) reorderedWeekDays else reorderedWeekDays.take(5)

        val displayDaysCount = displayDays.size
        val is24HourMode = style.scheduleMode == ScheduleModeProto.TIME_24H_MODE
        val maxGridSections = if (is24HourMode) 24 else viewState.displayRows.size

        // 存在错峰行时给左侧时间列留出足够宽度，避免“一/二教等”等标签被裁切
        val hasStaggerRows = !is24HourMode && viewState.displayRows.any { it.isStaggered }
        val effectiveTimeColumnWidth = if (hasStaggerRows) maxOf(style.timeColumnWidth, 56.dp) else style.timeColumnWidth

        val totalGridHeight = style.sectionHeight * maxGridSections
        val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        val strokeWidthPx = 1f

        val singleSchedulables = remember(viewState.mergedCourses, viewState.firstDayOfWeek, viewState.showWeekends) {
            calculateSingleSchedulables(viewState.mergedCourses, viewState.firstDayOfWeek, viewState.showWeekends)
        }
        val sectionHeightPx = with(density) { style.sectionHeight.toPx() }

        // 显示行 -> 逻辑节次：交错行只影响展示，保存课程时仍使用原来的节次编号
        fun logicalSectionAtRow(rowIndex: Int): Int {
            val rows = viewState.displayRows
            if (is24HourMode || rows.isEmpty()) return rowIndex + 1
            return rows.getOrNull(rowIndex)?.section ?: (rowIndex + 1)
        }

        // 拖动整块：按目标行所在节次移动，保持课程原本跨越的节次数
        fun actionSpanForMove(block: MergedCourseBlock, targetStartOffset: Float): Pair<Float, Float> {
            val rows = viewState.displayRows
            val fallback = targetStartOffset to (targetStartOffset + 1f)
            if (is24HourMode || rows.isEmpty()) return fallback

            val course = block.courses.firstOrNull()?.course ?: return fallback
            val originalStart = course.startSection ?: return fallback
            val originalEnd = course.endSection ?: return fallback
            val maxSection = maxOf(viewState.timeSlots.size, 1)
            val duration = (originalEnd - originalStart).coerceAtLeast(0)

            val startRow = targetStartOffset.roundToInt().coerceIn(0, rows.size - 1)
            val newStart = rows[startRow].section.coerceIn(1, maxSection)
            val newEnd = (newStart + duration).coerceIn(newStart, maxSection)
            return (newStart - 1).toFloat() to newEnd.toFloat()
        }

        // 拉伸上下边界：上边界换算为开始节次，下边界换算为结束节次
        fun actionSpanForResize(
            block: MergedCourseBlock,
            newStartOffset: Float,
            newEndOffset: Float,
            previousStartOffset: Float,
            previousEndOffset: Float
        ): Pair<Float, Float> {
            val rows = viewState.displayRows
            val fallback = newStartOffset to newEndOffset
            if (is24HourMode || rows.isEmpty()) return fallback

            val course = block.courses.firstOrNull()?.course ?: return fallback
            val maxSection = maxOf(viewState.timeSlots.size, 1)
            var startSection = course.startSection ?: 1
            var endSection = course.endSection ?: 1

            if (newStartOffset.roundToInt() != previousStartOffset.roundToInt()) {
                val startRow = newStartOffset.roundToInt().coerceIn(0, rows.size - 1)
                startSection = rows[startRow].section.coerceIn(1, maxSection)
            }
            if (newEndOffset.roundToInt() != previousEndOffset.roundToInt()) {
                val endRow = (newEndOffset.roundToInt() - 1).coerceIn(0, rows.size - 1)
                endSection = rows[endRow].section.coerceIn(1, maxSection)
            }
            if (startSection > endSection) {
                val temp = startSection
                startSection = endSection
                endSection = temp
            }
            return (startSection - 1).toFloat() to endSection.toFloat()
        }

        var activeDragHour by remember { mutableStateOf<Int?>(null) }
        var activeDragMinuteStr by remember { mutableStateOf<String?>(null) }

        if (is24HourMode && state.expandedItem != null) {
            val currentTargetSection = when {
                state.isTopHandleDragging -> {
                    val minGap = 0.25f
                    val deltaSection = state.topHandleDragOffsetY / sectionHeightPx
                    var proposedStart = state.expandedItem!!.startSection + deltaSection
                    proposedStart = (proposedStart / 0.25f).roundToInt() * 0.25f
                    proposedStart.coerceIn(0f, state.expandedItem!!.endSection - minGap)
                }
                state.isBottomHandleDragging -> {
                    val minGap = 0.25f
                    val deltaSection = state.bottomHandleDragOffsetY / sectionHeightPx
                    var proposedEnd = state.expandedItem!!.endSection + deltaSection
                    proposedEnd = (proposedEnd / 0.25f).roundToInt() * 0.25f
                    proposedEnd.coerceIn(state.expandedItem!!.startSection + minGap, maxGridSections.toFloat())
                }
                state.activeMoveIntent != null -> {
                    val duration = state.activeMoveIntent!!.duration
                    var targetStart = state.activeMoveIntent!!.initialStartSection + (state.bodyDragOffsetY / sectionHeightPx)
                    targetStart = (targetStart / 0.25f).roundToInt() * 0.25f
                    targetStart.coerceIn(0f, maxGridSections - duration)
                }
                else -> null
            }

            if (currentTargetSection != null) {
                val hour = kotlin.math.floor(currentTargetSection).toInt().coerceIn(0, maxGridSections - 1)
                val minuteFraction = currentTargetSection - kotlin.math.floor(currentTargetSection)
                val minute = (minuteFraction * 60).roundToInt() % 60

                activeDragHour = hour
                activeDragMinuteStr = minute.toString().padStart(2, '0')
            } else {
                activeDragHour = null
                activeDragMinuteStr = null
            }
        } else {
            activeDragHour = null
            activeDragMinuteStr = null
        }

        // 自动边缘滚动监测线程
        LaunchedEffect(state.isEditingActive) {
            if (state.isEditingActive) {
                while (true) {
                    val item = state.expandedItem ?: break
                    val threshold = with(density) { 40.dp.toPx() }
                    val scrollSpeed = with(density) { 8.dp.toPx() }

                    var relativeTop: Float? = null
                    var relativeBottom: Float? = null

                    if (state.activeMoveIntent != null) {
                        val currentTopInGrid = item.startSection * sectionHeightPx + state.bodyDragOffsetY
                        val currentBottomInGrid = item.endSection * sectionHeightPx + state.bodyDragOffsetY
                        relativeTop = currentTopInGrid - state.gridScrollState.value
                        relativeBottom = currentBottomInGrid - state.gridScrollState.value
                    } else if (state.isTopHandleDragging) {
                        val currentTopInGrid = item.startSection * sectionHeightPx + state.topHandleDragOffsetY
                        relativeTop = currentTopInGrid - state.gridScrollState.value
                    } else if (state.isBottomHandleDragging) {
                        val currentBottomInGrid = item.endSection * sectionHeightPx + state.bottomHandleDragOffsetY
                        relativeBottom = currentBottomInGrid - state.gridScrollState.value
                    }

                    var scrollAmount = 0f
                    if (relativeTop != null && relativeTop < threshold) {
                        if (state.gridScrollState.value > 0) {
                            scrollAmount = -scrollSpeed
                            if (state.gridScrollState.value + scrollAmount < 0) scrollAmount = -state.gridScrollState.value.toFloat()
                        }
                    } else if (relativeBottom != null && state.viewportHeightPx > 0f && relativeBottom > state.viewportHeightPx - threshold) {
                        val maxScroll = state.gridScrollState.maxValue
                        if (state.gridScrollState.value < maxScroll) {
                            scrollAmount = scrollSpeed
                            if (state.gridScrollState.value + scrollAmount > maxScroll) scrollAmount = (maxScroll - state.gridScrollState.value).toFloat()
                        }
                    }

                    if (scrollAmount != 0f) {
                        state.gridScrollState.scrollBy(scrollAmount)
                        if (state.activeMoveIntent != null) {
                            state.bodyDragOffsetY += scrollAmount
                        } else if (state.isTopHandleDragging) {
                            state.topHandleDragOffsetY += scrollAmount
                        } else if (state.isBottomHandleDragging) {
                            state.bottomHandleDragOffsetY += scrollAmount
                        }
                    }
                    delay(16.milliseconds)
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            DayHeader(
                style = style,
                displayDays = displayDays,
                dates = viewState.dates,
                currentYear = viewState.currentYear,
                currentWeek = viewState.currentWeek,
                todayIndex = viewState.todayIndex,
                lineColor = gridLineColor,
                textColor = pageTextColor,
                subTextColor = pageSubTextColor,
                strokeWidthPx = strokeWidthPx,
                timeColumnWidth = effectiveTimeColumnWidth
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { state.viewportHeightPx = it.height.toFloat() }
                    .verticalScroll(state = state.gridScrollState, enabled = state.expandedItem == null)
            ) {
                TimeColumn(
                    style = style, displayRows = viewState.displayRows, maxGridSections = maxGridSections,
                    is24HourMode = is24HourMode, onTimeSlotClicked = { actions.onTimeSlotClicked() },
                    modifier = Modifier.height(totalGridHeight), lineColor = gridLineColor,
                    currentRowIndices = viewState.currentRowIndices, textColor = pageTextColor,
                    subTextColor = pageSubTextColor, strokeWidthPx = strokeWidthPx,
                    activeDragHour = activeDragHour,
                    activeDragMinuteStr = activeDragMinuteStr,
                    timeColumnWidth = effectiveTimeColumnWidth
                )

                Layout(
                    content = {
                        singleSchedulables.forEach { item ->
                            val isExpanded = state.expandedItem != null && state.expandedItem?.parentBlock === item.parentBlock

                            Box(
                                modifier = Modifier
                                    .padding(style.courseBlockOuterPadding)
                                    .zIndex(if (isExpanded) 2f else 0f)
                                    .then(
                                        if (!isExpanded) {
                                            Modifier.pointerInput(item) {
                                                detectTapGestures(
                                                    onTap = { actions.onCourseBlockClicked(item.parentBlock) },
                                                    onLongPress = {
                                                        state.expandedItem = item
                                                        actions.onHoldStateChanged(true)
                                                    }
                                                )
                                            }
                                        } else {
                                            Modifier.pointerInput(item) {
                                                detectTapGestures(onTap = { state.expandedItem = null; actions.onHoldStateChanged(false) })
                                            }
                                        }
                                    )
                            ) {
                                CourseBlock(
                                    courseWrapper = item.courseWrapper,
                                    isVisualDemoted = item.parentBlock.isVisualDemoted,
                                    style = style,
                                    timeSlots = viewState.timeSlots,
                                    isFloating = isExpanded,
                                    modifier = if (isExpanded) {
                                        if (!item.parentBlock.isVisualDemoted) {
                                            Modifier.pointerInput(item, state.gridWidthPx) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        state.activeMoveIntent = CourseMoveIntent(item.parentBlock, item.parentBlock.day, item.startSection, item.endSection - item.startSection)
                                                        state.bodyDragOffsetX = 0f; state.bodyDragOffsetY = 0f
                                                    },
                                                    onDragEnd = {
                                                        val intent = state.activeMoveIntent
                                                        if (intent != null && state.gridWidthPx > 0f) {
                                                            val cellWidth = state.gridWidthPx / displayDaysCount

                                                            val initialX = item.columnIndex * cellWidth + (item.subColumnIndex * (cellWidth / item.subColumnCount))

                                                            val currentAbsoluteX = initialX + state.bodyDragOffsetX
                                                            val blockWidth = cellWidth / item.subColumnCount

                                                            val strictThresholdPx = with(density) { (-6.18).dp.toPx() }

                                                            val touchLeftEdge = currentAbsoluteX <= strictThresholdPx
                                                            val touchRightEdge = (currentAbsoluteX + blockWidth) >= (state.gridWidthPx - strictThresholdPx)

                                                            if (touchLeftEdge || touchRightEdge) {
                                                                actions.onInitiateFloatingMode(intent.parentBlock)
                                                                state.resetAllStates()
                                                                actions.onHoldStateChanged(false)
                                                            } else {
                                                                val deltaCols = (state.bodyDragOffsetX / cellWidth).roundToInt()
                                                                val targetDisplayIdx = (item.columnIndex + deltaCols).coerceIn(0, displayDaysCount - 1)
                                                                val targetDay = mapDisplayIndexToDay(targetDisplayIdx, viewState.firstDayOfWeek)
                                                                var targetStart = intent.initialStartSection + (state.bodyDragOffsetY / sectionHeightPx)
                                                                targetStart = if (is24HourMode) (targetStart / 0.25f).roundToInt() * 0.25f else targetStart.roundToInt().toFloat()
                                                                targetStart = targetStart.coerceIn(0f, maxGridSections - intent.duration)
                                                                val targetEnd = targetStart + intent.duration

                                                                val targetX = targetDisplayIdx * cellWidth
                                                                state.bodyDragOffsetX = targetX - initialX
                                                                state.bodyDragOffsetY = (targetStart - intent.initialStartSection) * sectionHeightPx

                                                                val (actionStart, actionEnd) = actionSpanForMove(intent.parentBlock, targetStart)
                                                                actions.onCourseMovedWithinGrid(intent.parentBlock, targetDay, actionStart, actionEnd)
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        state.resetAllStates()
                                                        actions.onHoldStateChanged(false)
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        state.bodyDragOffsetX += dragAmount.x
                                                        state.bodyDragOffsetY += dragAmount.y
                                                    }
                                                )
                                            }
                                        } else {
                                            Modifier
                                        }
                                    } else Modifier
                                )

                                if (isExpanded && state.activeMoveIntent == null && !item.parentBlock.isVisualDemoted) {
                                    CourseEditHandles(
                                        onDragStart = { isTop ->
                                            if (isTop) {
                                                state.isTopHandleDragging = true
                                                state.isBottomHandleDragging = false
                                                state.topHandleDragOffsetY = 0f
                                            } else {
                                                state.isTopHandleDragging = false
                                                state.isBottomHandleDragging = true
                                                state.bottomHandleDragOffsetY = 0f
                                            }
                                        },
                                        onDragging = { deltaY ->
                                            if (state.isTopHandleDragging) {
                                                val minGap = if (is24HourMode) 0.25f else 1f
                                                val maxAllowedDragY = ((item.endSection - item.startSection) - minGap) * sectionHeightPx

                                                val tentativeOffsetY = state.topHandleDragOffsetY + deltaY
                                                if (is24HourMode) {
                                                    val proposedStart = item.startSection + (tentativeOffsetY / sectionHeightPx)
                                                    state.topHandleDragOffsetY = if (proposedStart > item.endSection - minGap) {
                                                        maxAllowedDragY
                                                    } else {
                                                        tentativeOffsetY
                                                    }
                                                } else {
                                                    state.topHandleDragOffsetY = tentativeOffsetY.coerceAtMost(maxAllowedDragY)
                                                }
                                            } else if (state.isBottomHandleDragging) {
                                                val minGap = if (is24HourMode) 0.25f else 1f
                                                val maxAllowedDragUpY = -(((item.endSection - item.startSection) - minGap) * sectionHeightPx)

                                                val tentativeOffsetY = state.bottomHandleDragOffsetY + deltaY
                                                if (is24HourMode) {
                                                    val proposedEnd = item.endSection + (tentativeOffsetY / sectionHeightPx)
                                                    state.bottomHandleDragOffsetY = if (proposedEnd < item.startSection + minGap) {
                                                        maxAllowedDragUpY
                                                    } else {
                                                        tentativeOffsetY
                                                    }
                                                } else {
                                                    state.bottomHandleDragOffsetY = tentativeOffsetY.coerceAtLeast(maxAllowedDragUpY)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            val currentItem = state.expandedItem
                                            if (currentItem != null) {
                                                val minGap = if (is24HourMode) 0.25f else 1f
                                                var finalStart = currentItem.startSection
                                                var finalEnd = currentItem.endSection

                                                if (state.isTopHandleDragging) {
                                                    val deltaSection = state.topHandleDragOffsetY / sectionHeightPx
                                                    var proposedStart = currentItem.startSection + deltaSection
                                                    proposedStart = if (is24HourMode) (proposedStart / 0.25f).roundToInt() * 0.25f else proposedStart.roundToInt().toFloat()
                                                    finalStart = proposedStart.coerceIn(0f, finalEnd - minGap)
                                                    state.topHandleDragOffsetY = (finalStart - currentItem.startSection) * sectionHeightPx
                                                } else if (state.isBottomHandleDragging) {
                                                    val deltaSection = state.bottomHandleDragOffsetY / sectionHeightPx
                                                    var proposedEnd = currentItem.endSection + deltaSection
                                                    proposedEnd = if (is24HourMode) (proposedEnd / 0.25f).roundToInt() * 0.25f else proposedEnd.roundToInt().toFloat()
                                                    finalEnd = proposedEnd.coerceIn(finalStart + minGap, maxGridSections.toFloat())
                                                    state.bottomHandleDragOffsetY = (finalEnd - currentItem.endSection) * sectionHeightPx
                                                }
                                                if (finalStart != currentItem.startSection || finalEnd != currentItem.endSection) {
                                                    val (actionStart, actionEnd) = actionSpanForResize(
                                                        currentItem.parentBlock,
                                                        finalStart,
                                                        finalEnd,
                                                        currentItem.startSection,
                                                        currentItem.endSection
                                                    )
                                                    actions.onCourseTimeAdjusted(currentItem.parentBlock, actionStart, actionEnd)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .height(totalGridHeight).weight(1f)
                        .onSizeChanged { state.gridWidthPx = it.width.toFloat() }
                        .drawBehind {
                            if (style.hideGridLines) return@drawBehind
                            val cellWidth = size.width / displayDaysCount
                            for (i in 1..displayDaysCount) {
                                val x = i * cellWidth
                                drawLine(gridLineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokeWidthPx)
                            }
                            for (i in 1..maxGridSections) {
                                val y = i * sectionHeightPx
                                drawLine(gridLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = strokeWidthPx)
                            }
                        }
                        .pointerInput(displayDaysCount, sectionHeightPx, viewState.firstDayOfWeek, maxGridSections, is24HourMode, state.expandedItem) {
                            detectTapGestures { offset ->
                                if (state.expandedItem != null) {
                                    state.expandedItem = null
                                    actions.onHoldStateChanged(false)
                                    return@detectTapGestures
                                }
                                val dayIdx = (offset.x / (size.width / displayDaysCount)).toInt().coerceIn(0, displayDaysCount - 1)
                                val secIdx = (offset.y / sectionHeightPx).toInt().coerceIn(0, maxGridSections - 1)
                                actions.onGridCellClicked(
                                    mapDisplayIndexToDay(dayIdx, viewState.firstDayOfWeek),
                                    if (is24HourMode) secIdx else logicalSectionAtRow(secIdx)
                                )
                            }
                        }
                ) { measurables, constraints ->
                    val cellWidth = constraints.maxWidth / displayDaysCount
                    val minGapPx = if (is24HourMode) 0f else with(density) { 30.dp.toPx() }

                    val placeables = measurables.mapIndexed { index, measurable ->
                        val item = singleSchedulables[index]
                        val isExpanded = state.expandedItem != null && state.expandedItem?.parentBlock === item.parentBlock

                        val originalHeightPx = ((item.endSection - item.startSection) * sectionHeightPx).toInt()

                        var calculatedHeightPx = originalHeightPx
                        if (isExpanded) {
                            if (state.isTopHandleDragging || state.topHandleDragOffsetY != 0f) {
                                calculatedHeightPx = (originalHeightPx - state.topHandleDragOffsetY.roundToInt()).coerceAtLeast(minGapPx.toInt())
                            } else if (state.isBottomHandleDragging || state.bottomHandleDragOffsetY != 0f) {
                                calculatedHeightPx = (originalHeightPx + state.bottomHandleDragOffsetY.roundToInt()).coerceAtLeast(minGapPx.toInt())
                            }
                        }

                        measurable.measure(
                            Constraints.fixed(
                                (cellWidth / if (isExpanded) 1 else item.subColumnCount),
                                calculatedHeightPx
                            )
                        )
                    }

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeables.forEachIndexed { index, placeable ->
                            val item = singleSchedulables[index]
                            val isExpanded = state.expandedItem != null && state.expandedItem?.parentBlock === item.parentBlock
                            val isMoving = state.activeMoveIntent != null && state.activeMoveIntent?.parentBlock === item.parentBlock

                            val originalX = item.columnIndex * cellWidth + (if (isExpanded) 0 else item.subColumnIndex * (cellWidth / item.subColumnCount))
                            val originalY = (item.startSection * sectionHeightPx).toInt()

                            var xPosition = originalX
                            var yPosition = originalY

                            if (isMoving) {
                                val intent = state.activeMoveIntent
                                if (intent != null) {
                                    val intentOriginalX = item.columnIndex * cellWidth
                                    val intentOriginalY = (intent.initialStartSection * sectionHeightPx).toInt()
                                    xPosition = (intentOriginalX + state.bodyDragOffsetX).toInt()
                                    yPosition = (intentOriginalY + state.bodyDragOffsetY).toInt()
                                }
                            } else if (isExpanded) {
                                if (state.isTopHandleDragging || state.topHandleDragOffsetY != 0f) {
                                    val originalHeightPx = ((item.endSection - item.startSection) * sectionHeightPx).toInt()
                                    yPosition = if (originalHeightPx - state.topHandleDragOffsetY >= minGapPx) {
                                        (originalY + state.topHandleDragOffsetY).toInt()
                                    } else {
                                        (originalY + (originalHeightPx - minGapPx)).toInt()
                                    }
                                }
                            }

                            placeable.placeRelative(xPosition, yPosition)
                        }
                    }
                }
            }
        }
    }
}