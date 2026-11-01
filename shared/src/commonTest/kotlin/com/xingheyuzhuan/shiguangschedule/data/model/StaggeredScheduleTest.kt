package com.xingheyuzhuan.shiguangschedule.data.model

import com.xingheyuzhuan.shiguangschedule.data.db.main.Course
import com.xingheyuzhuan.shiguangschedule.data.db.main.TimeSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaggeredScheduleTest {

    private val defaultSlots = listOf(
        slot(1, "08:00", "08:45"),
        slot(2, "08:55", "09:40"),
        slot(3, "10:00", "10:45"),
        slot(4, "10:45", "11:30"),
        slot(5, "14:00", "14:45")
    )

    private fun slot(number: Int, start: String, end: String) = TimeSlot(
        timeTableId = "test-table",
        number = number,
        startTime = start,
        endTime = end
    )

    private fun course(
        position: String,
        startSection: Int?,
        endSection: Int?,
        customStartTime: String? = null,
        customEndTime: String? = null
    ) = Course(
        id = "course",
        courseTableId = "test-table",
        name = "测试课程",
        teacher = "",
        position = position,
        day = 1,
        startSection = startSection,
        endSection = endSection,
        isCustomTime = customStartTime != null && customEndTime != null,
        customStartTime = customStartTime,
        customEndTime = customEndTime,
        colorInt = 0
    )

    @Test
    fun buildingKeyIsParsedFromRoomNumberOrChineseLabel() {
        assertEquals("2", StaggeredSchedule.buildingKeyOf("2301"))
        assertEquals("3", StaggeredSchedule.buildingKeyOf("3415"))
        assertEquals("1", StaggeredSchedule.buildingKeyOf("1301"))
        assertEquals("3", StaggeredSchedule.buildingKeyOf("三教学楼(3209)"))
        assertEquals("2", StaggeredSchedule.buildingKeyOf("二教学楼 2305"))
        assertEquals("2", StaggeredSchedule.buildingKeyOf("综合楼2301"))
    }

    @Test
    fun labAndSportsVenuesFallBackToDefault() {
        assertNull(StaggeredSchedule.buildingKeyOf("实验楼2301"))
        assertNull(StaggeredSchedule.buildingKeyOf("体育场"))
        assertNull(StaggeredSchedule.buildingKeyOf("体育馆"))
        assertNull(StaggeredSchedule.buildingKeyOf(""))
    }

    @Test
    fun rulesOnlyApplyWhenThirdAndFourthSlotsAreDefault() {
        assertTrue(StaggeredSchedule.isApplicableForSection(3, defaultSlots))
        assertTrue(StaggeredSchedule.isApplicableForSection(4, defaultSlots))

        val editedSlot = defaultSlots.map { if (it.number == 3) it.copy(startTime = "09:50") else it }
        assertFalse(StaggeredSchedule.isApplicableForSection(3, editedSlot))
        assertTrue(StaggeredSchedule.isApplicableForSection(4, editedSlot))

        assertFalse(StaggeredSchedule.isApplicableForSection(3, defaultSlots.filter { it.number < 3 }))
    }

    @Test
    fun thirdAndFourthPeriodTimesFollowTheBuilding() {
        assertEquals(
            StaggeredSchedule.TimeRange("10:20", "12:00"),
            StaggeredSchedule.resolvedTimes(course("3415", 3, 4), defaultSlots)
        )
        assertEquals(
            StaggeredSchedule.TimeRange("10:00", "11:40"),
            StaggeredSchedule.resolvedTimes(course("2301", 3, 4), defaultSlots)
        )
        assertEquals(
            StaggeredSchedule.TimeRange("10:00", "11:30"),
            StaggeredSchedule.resolvedTimes(course("1301", 3, 4), defaultSlots)
        )
        assertEquals(
            StaggeredSchedule.TimeRange("10:00", "11:30"),
            StaggeredSchedule.resolvedTimes(course("实验楼", 3, 4), defaultSlots)
        )
    }

    @Test
    fun singleFourthPeriodAndOddSectionsAreHandled() {
        assertEquals(
            StaggeredSchedule.TimeRange("10:55", "11:40"),
            StaggeredSchedule.resolvedTimes(course("2301", 4, 4), defaultSlots)
        )
        assertEquals(
            StaggeredSchedule.TimeRange("10:20", "11:05"),
            StaggeredSchedule.resolvedTimes(course("3415", 3, 3), defaultSlots)
        )
        assertEquals(
            StaggeredSchedule.TimeRange("14:00", "14:45"),
            StaggeredSchedule.resolvedTimes(course("2301", 5, 5), defaultSlots)
        )
    }

    @Test
    fun customTimeOverridesBuildingRule() {
        val custom = course("3415", 3, 4, customStartTime = "08:00", customEndTime = "09:00")
        assertEquals(
            StaggeredSchedule.TimeRange("08:00", "09:00"),
            StaggeredSchedule.resolvedTimes(custom, defaultSlots)
        )
        assertTrue(StaggeredSchedule.isPreciseTimeCourse(custom, defaultSlots))
    }

    @Test
    fun preciseTimeFlagOnlyTurnsOnForAffectedCourses() {
        assertTrue(StaggeredSchedule.isPreciseTimeCourse(course("3415", 3, 4), defaultSlots))
        assertTrue(StaggeredSchedule.isPreciseTimeCourse(course("2301", 3, 4), defaultSlots))
        assertFalse(StaggeredSchedule.isPreciseTimeCourse(course("1301", 3, 4), defaultSlots))
        assertFalse(StaggeredSchedule.isPreciseTimeCourse(course("实验楼", 3, 4), defaultSlots))
        assertFalse(StaggeredSchedule.isPreciseTimeCourse(course("2301", 1, 2), defaultSlots))
    }

    @Test
    fun displayRowsSplitThirdAndFourthSections() {
        val rows = StaggeredSchedule.buildDisplayRows(defaultSlots)

        assertEquals(8, rows.size)
        assertEquals(listOf(1, 2, 3, 3, 4, 4, 4, 5), rows.map { it.section })
        assertEquals(listOf("一/二教等", "三教", "一教等", "二教", "三教"), rows.drop(2).take(5).map { it.buildingLabel })
        assertEquals("10:00", rows[2].startTime)
        assertEquals("10:20", rows[3].startTime)
        assertEquals("10:45", rows[4].startTime)
        assertEquals("10:55", rows[5].startTime)
        assertEquals("11:15", rows[6].startTime)
        assertEquals("14:00", rows[7].startTime)
    }

    @Test
    fun displayRowsFallBackToSingleRowWhenScheduleIsCustomized() {
        val customized = defaultSlots.map {
            when (it.number) {
                3 -> it.copy(startTime = "09:50", endTime = "10:35")
                4 -> it.copy(startTime = "10:40", endTime = "11:25")
                else -> it
            }
        }
        val rows = StaggeredSchedule.buildDisplayRows(customized)

        assertEquals(customized.size, rows.size)
        assertTrue(rows.none { it.isStaggered })
        assertTrue(rows.all { it.buildingLabel == null })
    }

    @Test
    fun courseRowsFollowTheBuildingGroup() {
        val rows = StaggeredSchedule.buildDisplayRows(defaultSlots)

        assertEquals(3 to 7, StaggeredSchedule.rowSpanOf(course("3415", 3, 4), rows))
        assertEquals(2 to 6, StaggeredSchedule.rowSpanOf(course("2301", 3, 4), rows))
        assertEquals(2 to 5, StaggeredSchedule.rowSpanOf(course("1301", 3, 4), rows))
        assertEquals(2 to 5, StaggeredSchedule.rowSpanOf(course("实验楼", 3, 4), rows))
        assertEquals(5 to 6, StaggeredSchedule.rowSpanOf(course("2301", 4, 4), rows))
        assertEquals(7 to 8, StaggeredSchedule.rowSpanOf(course("3415", 5, 5), rows))
    }

    @Test
    fun currentRowsContainAllOverlappingVariants() {
        val rows = StaggeredSchedule.buildDisplayRows(defaultSlots)

        assertEquals(setOf(2, 3), StaggeredSchedule.currentRowIndices(rows, 10 * 60 + 30))
        assertEquals(setOf(4, 5, 6), StaggeredSchedule.currentRowIndices(rows, 11 * 60 + 20))
        assertTrue(StaggeredSchedule.currentRowIndices(rows, 12 * 60 + 30).isEmpty())
    }

    @Test
    fun variantsAreListedPerSectionInStartTimeOrder() {
        val third = StaggeredSchedule.variantsForSection(3, defaultSlots)
        assertEquals(listOf("三教"), third.map { it.buildingLabel })
        assertEquals(listOf("10:20"), third.map { it.range.startTime })

        val fourth = StaggeredSchedule.variantsForSection(4, defaultSlots)
        assertEquals(listOf("二教", "三教"), fourth.map { it.buildingLabel })
        assertEquals(listOf("10:55", "11:15"), fourth.map { it.range.startTime })

        assertTrue(StaggeredSchedule.variantsForSection(1, defaultSlots).isEmpty())
        assertTrue(StaggeredSchedule.hasAnyVariant(defaultSlots))

        val otherSchedule = listOf(slot(1, "08:00", "08:45"), slot(3, "09:50", "10:35"), slot(4, "10:40", "11:25"))
        assertFalse(StaggeredSchedule.hasAnyVariant(otherSchedule))
        assertTrue(StaggeredSchedule.variantsForSection(3, otherSchedule).isEmpty())
    }
}
