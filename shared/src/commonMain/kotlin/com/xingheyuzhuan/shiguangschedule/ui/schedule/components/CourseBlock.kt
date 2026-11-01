package com.xingheyuzhuan.shiguangschedule.ui.schedule.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseWithWeeks
import com.xingheyuzhuan.shiguangschedule.data.db.main.TimeSlot
import com.xingheyuzhuan.shiguangschedule.data.model.StaggeredSchedule
import com.xingheyuzhuan.shiguangschedule.data.model.schedule_style.BorderTypeProto
import com.xingheyuzhuan.shiguangschedule.data.model.schedule_style.ScheduleModeProto
import com.xingheyuzhuan.shiguangschedule.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
fun CourseBlock(
    courseWrapper: CourseWithWeeks,
    isVisualDemoted: Boolean,
    style: ScheduleGridStyleComposed,
    timeSlots: List<TimeSlot>,
    modifier: Modifier = Modifier,
    isFloating: Boolean = false // 标记当前块是否处于长按选中/悬浮状态
) {
    val course = courseWrapper.course
    val isDarkTheme = LocalIsDarkTheme.current

    // 颜色适配
    val colorIndex = course.colorInt.takeIf { it in style.courseColorMaps.indices }
    val courseColorAdapted: Color? = colorIndex?.let { index ->
        val baseColorMap = style.courseColorMaps[index]
        if (isDarkTheme) baseColorMap.dark else baseColorMap.light
    }
    val fallbackColorAdapted: Color = if (isDarkTheme) style.courseColorMaps.first().dark else style.courseColorMaps.first().light

    val currentAlpha = if (isFloating) 0.95f else style.courseBlockAlpha
    val blockColor = (courseColorAdapted ?: fallbackColorAdapted).copy(alpha = currentAlpha)
    val textColor = style.courseTextColor ?: MaterialTheme.colorScheme.onSurface

    // 字体大小
    val s13 = (13 * style.fontScale).sp
    val s10 = (10 * style.fontScale).sp

    // 核心分支逻辑：判断 24小时模式 与 节次模式 的时间文本渲染
    val customStartTime = course.customStartTime
    val customEndTime = course.customEndTime
    val customTimeString = if (customStartTime != null && customEndTime != null) "$customStartTime - $customEndTime" else null
    val isCustomTimeCourse = customTimeString != null

    // 按教室号匹配错峰规则，得到该课程真正上课的起止时间（用于 24 小时模式与上课状态）
    val resolvedTime = StaggeredSchedule.resolvedTimes(course, timeSlots)
    val resolvedTimeString = resolvedTime?.let { "${it.startTime} - ${it.endTime}" }

    val timeTextToShow = if (style.scheduleMode == ScheduleModeProto.TIME_24H_MODE) {
        // 24小时绝对时间轴模式：全部课程都显示起止时间
        resolvedTimeString ?: customTimeString
    } else {
        // 传统节次模式：错峰时间已经由左侧独立时间行展示，课程块内不再重复；
        // 仅手动自定义时间 / 开启“显示开始时间”时显示时间。
        when {
            isCustomTimeCourse -> customTimeString
            style.showStartTime -> StaggeredSchedule.resolvedStartTime(course, timeSlots)
            else -> null
        }
    }

    // 边框样式配置
    val borderColor = if (isFloating) Color(0xFF2196F3) else MaterialTheme.colorScheme.outline
    val borderWidth = if (isFloating) 2.dp else 1.dp
    val borderAlpha = if (isFloating) 1.0f else style.courseBlockAlpha
    val shape = RoundedCornerShape(style.courseBlockCornerRadius)

    // ===== 上课状态高亮：0 普通 / 1 即将开始（20 分钟内）/ 2 正在上课 =====
    var nowMinuteOfDay by remember { mutableIntStateOf(-1) }
    var todayIso by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            nowMinuteOfDay = now.hour * 60 + now.minute
            todayIso = now.dayOfWeek.isoDayNumber
            delay(30_000)
        }
    }

    fun toMinutes(text: String?): Int? {
        val s = text ?: return null
        val parts = s.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    val liveStartText = StaggeredSchedule.resolvedStartTime(course, timeSlots)
    val liveEndText = StaggeredSchedule.resolvedEndTime(course, timeSlots)
    val liveStart = toMinutes(liveStartText)
    val liveEnd = toMinutes(liveEndText)

    val liveStatus = when {
        isVisualDemoted || nowMinuteOfDay < 0 || todayIso < 0 -> 0
        course.day != todayIso -> 0
        liveStart == null || liveEnd == null -> 0
        nowMinuteOfDay >= liveStart && nowMinuteOfDay < liveEnd -> 2
        nowMinuteOfDay < liveStart && liveStart - nowMinuteOfDay <= 20 -> 1
        else -> 0
    }

    val liveAccent = MaterialTheme.colorScheme.primary
    val livePulse by rememberInfiniteTransition(label = "livePulse").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "livePulseAlpha"
    )

    val liveBorderModifier = when (liveStatus) {
        2 -> Modifier.border(2.dp, liveAccent.copy(alpha = livePulse), shape)
        1 -> Modifier.border(1.dp, liveAccent.copy(alpha = 0.55f), shape)
        else -> Modifier
    }

    val borderModifier = when (style.borderType) {
        BorderTypeProto.BORDER_TYPE_SOLID -> {
            Modifier.border(borderWidth, borderColor.copy(alpha = borderAlpha), shape)
        }
        BorderTypeProto.BORDER_TYPE_DASHED -> {
            Modifier.drawBehind {
                val strokeWidth = borderWidth.toPx()
                val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = borderColor.copy(alpha = borderAlpha),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, pathEffect = dashPathEffect)
                )
            }
        }
        else -> {
            if (isFloating) Modifier.border(borderWidth, borderColor, shape) else Modifier
        }
    }

    val horizontalAlignment = if (style.textAlignCenterHorizontal) Alignment.CenterHorizontally else Alignment.Start
    val verticalArrangement = if (style.textAlignCenterVertical) Arrangement.Center else Arrangement.Top
    val textAlign = if (style.textAlignCenterHorizontal) TextAlign.Center else TextAlign.Start

    // 选中捏起时，增加三维物理阴影
    val floatingShadowModifier = if (isFloating) {
        Modifier.shadow(elevation = 8.dp, shape = shape, clip = false)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(floatingShadowModifier)
            .fillMaxSize()
            .then(borderModifier)
            .then(liveBorderModifier)
            .clip(shape)
            .background(color = blockColor)
    ) {
        // 课程文字内容容器
        Column(
            modifier = Modifier.fillMaxSize().padding(style.courseBlockInnerPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement
        ) {
            if (liveStatus > 0) {
                Text(
                    text = if (liveStatus == 2) "● 上课中" else "○ 即将上课",
                    fontSize = s10,
                    fontWeight = FontWeight.Bold,
                    color = if (liveStatus == 2) liveAccent else liveAccent.copy(alpha = 0.75f),
                    textAlign = textAlign,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(lineHeight = 1.em)
                )
            }

            if (timeTextToShow != null) {
                Text(
                    text = timeTextToShow,
                    fontSize = s10,
                    color = textColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = textAlign,
                    style = TextStyle(lineHeight = 1.em)
                )
            }

            Text(
                text = course.name,
                fontSize = s13,
                fontWeight = FontWeight.Bold,
                color = textColor,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(lineHeight = 1.2.em)
            )

            if (!style.hideTeacher) {
                val teacher = course.teacher
                if (teacher.isNotBlank()) {
                    Text(text = teacher, fontSize = s10, color = textColor, textAlign = textAlign, overflow = TextOverflow.Ellipsis, style = TextStyle(lineHeight = 1.em))
                }
            }

            if (!style.hideLocation) {
                val position = course.position
                if (position.isNotBlank()) {
                    val prefix = if (style.removeLocationAt) "" else "@"
                    Text(text = "$prefix$position", fontSize = s10, color = textColor, textAlign = textAlign, overflow = TextOverflow.Ellipsis, style = TextStyle(lineHeight = 1.em))
                }
            }
        }

        // 当单课不是当前周时，进行干净的全局遮罩染色与虚化斜线绘制
        if (isVisualDemoted && !isFloating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = (if (isDarkTheme) Color.Black else Color.White).copy(alpha = 0.618f))
                    .drawBehind {
                        val stripeWidth = 5.dp.toPx()
                        val stripeColor = (if (isDarkTheme) Color.White else Color.Black).copy(alpha = 0.06f)
                        val brush = Brush.linearGradient(
                            0.0f to stripeColor, 0.45f to stripeColor,
                            0.55f to Color.Transparent, 1.0f to Color.Transparent,
                            start = Offset(0f, 0f), end = Offset(stripeWidth, stripeWidth), tileMode = TileMode.Repeated
                        )
                        drawRect(brush = brush)
                    }
            )
        }
    }
}