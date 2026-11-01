package com.xingheyuzhuan.shiguangschedule.data.model

import com.xingheyuzhuan.shiguangschedule.data.db.main.Course
import com.xingheyuzhuan.shiguangschedule.data.db.main.TimeSlot

/**
 * 湖南财政经济学院主校区「第 3、4 节错峰上课」规则。
 *
 * 依据《湖南财政经济学院主校区上课作息时间》：
 * - 第 3 节：一、二教学楼 / 实验楼 / 体育课教学场地 10:00-10:45；三教学楼 10:20-11:05
 * - 第 4 节：一教学楼 / 实验楼 / 体育课教学场地 10:45-11:30；二教学楼 10:55-11:40；三教学楼 11:15-12:00
 *
 * 教学楼由教室号的第一个数字判定（2301 → 二教，3415 → 三教）；
 * 实验楼、体育场等没有教室号（或带有“实验 / 体育”等字样）的地点使用默认作息。
 *
 * 本规则只在作息表的第 3、4 节与默认值完全一致时生效，
 * 避免用户自定义了其他作息方案后仍被这里固定写死的时间错误覆盖。
 */
object StaggeredSchedule {

    data class TimeRange(
        val startTime: String,
        val endTime: String
    )

    data class SlotRule(
        val section: Int,
        val buildingKey: String,
        val buildingLabel: String,
        val range: TimeRange
    )

    /**
     * 课表格子中的一行。
     *
     * 第 3、4 节存在错峰时，同一个逻辑节次会拆成多行：
     * 第 3 节拆成默认行 + 三教行，第 4 节拆成默认行 + 二教行 + 三教行。
     * 没有错峰信息的普通节次仍然只占一行。
     */
    data class DisplayRow(
        val section: Int,
        val alias: String?,
        val buildingGroup: String?,
        val buildingLabel: String?,
        val startTime: String,
        val endTime: String,
        val isStaggered: Boolean
    )

    /** 规则所基于的默认第 3、4 节时间，只有作息表与它一致时才启用错峰规则。 */
    private val baseSectionTimes = mapOf(
        3 to TimeRange("10:00", "10:45"),
        4 to TimeRange("10:45", "11:30")
    )

    /** 各教学楼在不同节次上的特殊时间。没有出现在这里的一律使用默认作息。 */
    private val rules = listOf(
        SlotRule(section = 3, buildingKey = "3", buildingLabel = "三教", range = TimeRange("10:20", "11:05")),
        SlotRule(section = 4, buildingKey = "2", buildingLabel = "二教", range = TimeRange("10:55", "11:40")),
        SlotRule(section = 4, buildingKey = "3", buildingLabel = "三教", range = TimeRange("11:15", "12:00"))
    )

    private val buildingByChinese = mapOf("一" to "1", "二" to "2", "三" to "3")

    /** 这些地点即使带有数字，也按默认作息处理（实验楼、体育场地等）。 */
    private val defaultOnlyKeywords = listOf("实验", "体育", "场地", "操场", "馆", "中心")

    private val roomNumberRegex = Regex("""(?<!\d)([1-9])\d{3}(?!\d)""")
    private val chineseBuildingRegex = Regex("""([一二三])教""")

    /**
     * 某节次是否适合套用错峰规则：作息表里必须存在该节次，且起止时间仍是默认值。
     */
    fun isApplicableForSection(section: Int, timeSlots: List<TimeSlot>): Boolean {
        val base = baseSectionTimes[section] ?: return false
        val slot = timeSlots.firstOrNull { it.number == section } ?: return false
        return slot.startTime == base.startTime && slot.endTime == base.endTime
    }

    /** 当前作息表是否存在任何可展示的错峰时间。 */
    fun hasAnyVariant(timeSlots: List<TimeSlot>): Boolean =
        rules.any { isApplicableForSection(it.section, timeSlots) }

    /**
     * 从上课地点中解析教学楼序号：
     * - 优先识别“一教 / 二教 / 三教 / 三教学楼”等文字；
     * - 否则取第一个四位教室号的首位数字（2301 → 2）；
     * - 实验楼、体育场等没有教室号或带排除关键字的地点返回 null（使用默认作息）。
     */
    fun buildingKeyOf(position: String): String? {
        val text = position.trim()
        if (text.isEmpty()) return null
        if (defaultOnlyKeywords.any { text.contains(it) }) return null

        chineseBuildingRegex.find(text)?.let { match ->
            return buildingByChinese[match.groupValues[1]]
        }
        return roomNumberRegex.find(text)?.groupValues?.get(1)
    }

    /** 教学楼序号对应的短名称。 */
    fun buildingLabelOf(buildingKey: String?): String? = when (buildingKey) {
        "1" -> "一教"
        "2" -> "二教"
        "3" -> "三教"
        else -> null
    }

    /** 课程地点所属的教学楼组；没有数字 / 实验楼 / 体育场地返回 null（默认作息）。 */
    fun buildingGroupOf(position: String): String? = buildingKeyOf(position)

    /**
     * 构建课表实际显示的格子行。
     * 没有错峰作息时，一行对应一个 TimeSlot；有错峰时，第 3、4 节按教学楼拆成多行。
     */
    fun buildDisplayRows(timeSlots: List<TimeSlot>): List<DisplayRow> {
        val sorted = timeSlots.sortedBy { it.number }
        val result = ArrayList<DisplayRow>(sorted.size + 4)

        sorted.forEach { slot ->
            if (slot.number == 3 || slot.number == 4) {
                val rules = variantsForSection(slot.number, timeSlots)
                if (rules.isNotEmpty()) {
                    result.add(
                        DisplayRow(
                            section = slot.number,
                            alias = slot.alias,
                            buildingGroup = null,
                            buildingLabel = defaultBuildingLabelOf(slot.number),
                            startTime = slot.startTime,
                            endTime = slot.endTime,
                            isStaggered = false
                        )
                    )
                    rules.forEach { rule ->
                        result.add(
                            DisplayRow(
                                section = slot.number,
                                alias = slot.alias,
                                buildingGroup = rule.buildingKey,
                                buildingLabel = rule.buildingLabel,
                                startTime = rule.range.startTime,
                                endTime = rule.range.endTime,
                                isStaggered = true
                            )
                        )
                    }
                    return@forEach
                }
            }

            result.add(
                DisplayRow(
                    section = slot.number,
                    alias = slot.alias,
                    buildingGroup = null,
                    buildingLabel = null,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    isStaggered = false
                )
            )
        }
        return result
    }

    private fun defaultBuildingLabelOf(section: Int): String? = when (section) {
        3 -> "一/二教等"
        4 -> "一教等"
        else -> null
    }

    /** 在显示行中查找某个节次、某个教学楼组对应的行号；找不到时回退到该节次的默认行。 */
    fun rowIndexFor(section: Int, buildingGroup: String?, rows: List<DisplayRow>): Int {
        if (rows.isEmpty()) return -1
        val sameSection = rows.indices.filter { rows[it].section == section }
        if (sameSection.isEmpty()) return -1

        if (buildingGroup != null) {
            sameSection.firstOrNull { rows[it].buildingGroup == buildingGroup }?.let { return it }
        }
        return sameSection.firstOrNull { rows[it].buildingGroup == null }
            ?: sameSection.first()
    }

    /** 课程在显示行中的起止行（end 为开区间）。无法根据节次定位时返回 null。 */
    fun rowSpanOf(course: Course, rows: List<DisplayRow>): Pair<Int, Int>? {
        val startSection = course.startSection ?: return null
        val endSection = course.endSection ?: return null
        val group = buildingGroupOf(course.position)
        val startRow = rowIndexFor(startSection, group, rows)
        val endRow = rowIndexFor(endSection, group, rows)
        if (startRow < 0 || endRow < 0) return null
        return startRow to (endRow + 1)
    }

    /** 显示行对应的逻辑节次。 */
    fun sectionAtRow(rowIndex: Int, rows: List<DisplayRow>): Int? {
        return rows.getOrNull(rowIndex)?.section
    }

    /**
     * 把物理时间映射到显示行的浮点坐标（1.0 表示第一行顶部）。
     * 用于自定义时间课程在节次模式下的定位。
     */
    fun timeToRowScale(timeText: String, rows: List<DisplayRow>): Float {
        if (rows.isEmpty()) return 1f
        val target = minutesOf(timeText) ?: return 1f

        rows.forEachIndexed { index, row ->
            val start = minutesOf(row.startTime) ?: return@forEachIndexed
            val end = minutesOf(row.endTime) ?: return@forEachIndexed
            if (target in start until end) {
                val duration = (end - start).coerceAtLeast(1)
                return index + 1f + (target - start).toFloat() / duration
            }
        }

        val firstStart = minutesOf(rows.first().startTime)
        if (firstStart != null && target < firstStart) return 1f

        // 落在两个行之间的空档（例如午休）时，贴到下一行的顶部
        val nextRowIndex = rows.indexOfFirst { row ->
            val start = minutesOf(row.startTime)
            start != null && start > target
        }
        return if (nextRowIndex >= 0) nextRowIndex + 1f else rows.size + 1f
    }

    /** 当前时间命中的显示行（可能同时命中默认行和三教行）。 */
    fun currentRowIndices(rows: List<DisplayRow>, nowMinutes: Int): Set<Int> {
        return rows.indices.filterTo(mutableSetOf()) { index ->
            val row = rows[index]
            val start = minutesOf(row.startTime) ?: return@filterTo false
            val end = minutesOf(row.endTime) ?: return@filterTo false
            nowMinutes in start until end
        }
    }

    fun minutesOf(timeText: String): Int? {
        val parts = timeText.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    /**
     * 第 [section] 节需要额外展示的错峰时间（不含默认时间本身）。
     * 返回结果按开始时间升序排列。
     */
    fun variantsForSection(section: Int, timeSlots: List<TimeSlot>): List<SlotRule> {
        if (!isApplicableForSection(section, timeSlots)) return emptyList()
        return rules
            .filter { it.section == section }
            .sortedBy { it.range.startTime }
    }

    /** 第 [section] 节的默认时间（如果作息表里存在该节次）。 */
    fun defaultTimeRangeOf(section: Int, timeSlots: List<TimeSlot>): TimeRange? {
        val slot = timeSlots.firstOrNull { it.number == section } ?: return null
        return TimeRange(slot.startTime, slot.endTime)
    }

    /** 课程实际起止时间（Course 重载）。 */
    fun resolvedTimes(course: Course, timeSlots: List<TimeSlot>): TimeRange? {
        return resolvedTimesFor(
            position = course.position,
            startSection = course.startSection,
            endSection = course.endSection,
            timeSlots = timeSlots,
            customStartTime = course.customStartTime,
            customEndTime = course.customEndTime
        )
    }

    fun resolvedStartTime(course: Course, timeSlots: List<TimeSlot>): String? {
        return resolvedStartTimeFor(
            position = course.position,
            startSection = course.startSection,
            timeSlots = timeSlots,
            customStartTime = course.customStartTime
        )
    }

    fun resolvedEndTime(course: Course, timeSlots: List<TimeSlot>): String? {
        return resolvedEndTimeFor(
            position = course.position,
            endSection = course.endSection,
            timeSlots = timeSlots,
            customEndTime = course.customEndTime
        )
    }

    /**
     * 课程实际起止时间：手动自定义时间优先，其次是教室号匹配到的错峰时间，最后回退到作息表。
     * 供编辑页预览等没有完整 Course 实体的场景使用。
     */
    fun resolvedTimesFor(
        position: String,
        startSection: Int?,
        endSection: Int?,
        timeSlots: List<TimeSlot>,
        customStartTime: String? = null,
        customEndTime: String? = null
    ): TimeRange? {
        val start = resolvedStartTimeFor(position, startSection, timeSlots, customStartTime) ?: return null
        val end = resolvedEndTimeFor(position, endSection, timeSlots, customEndTime) ?: return null
        return TimeRange(start, end)
    }

    private fun resolvedStartTimeFor(
        position: String,
        startSection: Int?,
        timeSlots: List<TimeSlot>,
        customStartTime: String?
    ): String? {
        customStartTime?.takeIf { it.isNotBlank() }?.let { return it }
        val section = startSection ?: return null
        val baseStart = timeSlots.firstOrNull { it.number == section }?.startTime ?: return null
        return ruleFor(position, section, timeSlots)?.range?.startTime ?: baseStart
    }

    private fun resolvedEndTimeFor(
        position: String,
        endSection: Int?,
        timeSlots: List<TimeSlot>,
        customEndTime: String?
    ): String? {
        customEndTime?.takeIf { it.isNotBlank() }?.let { return it }
        val section = endSection ?: return null
        val baseEnd = timeSlots.firstOrNull { it.number == section }?.endTime ?: return null
        return ruleFor(position, section, timeSlots)?.range?.endTime ?: baseEnd
    }

    /**
     * 指定地点/节次是否因为教室号而使用了与默认作息不同的时间。
     * 用于课程详情等需要展示精准起止时间的地方。
     */
    fun isPreciseTimeFor(
        position: String,
        startSection: Int?,
        endSection: Int?,
        timeSlots: List<TimeSlot>,
        customStartTime: String? = null,
        customEndTime: String? = null
    ): Boolean {
        val resolved = resolvedTimesFor(
            position = position,
            startSection = startSection,
            endSection = endSection,
            timeSlots = timeSlots,
            customStartTime = customStartTime,
            customEndTime = customEndTime
        ) ?: return false
        val baseStart = startSection?.let { section ->
            timeSlots.firstOrNull { it.number == section }?.startTime
        }
        val baseEnd = endSection?.let { section ->
            timeSlots.firstOrNull { it.number == section }?.endTime
        }
        return resolved.startTime != baseStart || resolved.endTime != baseEnd
    }

    fun isPreciseTimeCourse(course: Course, timeSlots: List<TimeSlot>): Boolean {
        return isPreciseTimeFor(
            position = course.position,
            startSection = course.startSection,
            endSection = course.endSection,
            timeSlots = timeSlots,
            customStartTime = course.customStartTime,
            customEndTime = course.customEndTime
        )
    }

    private fun ruleFor(position: String, section: Int, timeSlots: List<TimeSlot>): SlotRule? {
        if (!isApplicableForSection(section, timeSlots)) return null
        val buildingKey = buildingKeyOf(position) ?: return null
        return rules.firstOrNull { it.section == section && it.buildingKey == buildingKey }
    }
}
