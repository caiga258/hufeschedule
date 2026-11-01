package com.xingheyuzhuan.shiguangschedule.ui.schedule.util

import com.xingheyuzhuan.shiguangschedule.data.db.main.Course
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseWeek
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseWithWeeks
import com.xingheyuzhuan.shiguangschedule.data.db.main.TimeSlot
import com.xingheyuzhuan.shiguangschedule.data.model.schedule_style.ScheduleModeProto
import kotlin.test.Test
import kotlin.test.assertEquals

class CourseMergeUtilsStaggerTest {

    private val slots = listOf(
        TimeSlot("table", 1, "08:00", "08:45"),
        TimeSlot("table", 2, "08:55", "09:40"),
        TimeSlot("table", 3, "10:00", "10:45"),
        TimeSlot("table", 4, "10:45", "11:30"),
        TimeSlot("table", 5, "14:00", "14:45")
    )

    private fun course(position: String, start: Int, end: Int): CourseWithWeeks {
        val course = Course(
            id = "id-$position",
            courseTableId = "table",
            name = "课程",
            teacher = "",
            position = position,
            day = 1,
            startSection = start,
            endSection = end,
            isCustomTime = false,
            customStartTime = null,
            customEndTime = null,
            colorInt = 0
        )
        return CourseWithWeeks(course, listOf(CourseWeek(course.id, 1)))
    }

    @Test
    fun coursesLandOnTheirBuildingRows() {
        val blocks = CourseMergeUtils.mergeCourses(
            courses = listOf(
                course("3415", 3, 4),
                course("2301", 3, 4),
                course("1301", 3, 4),
                course("实验楼", 3, 4)
            ),
            timeSlots = slots,
            currentWeek = 1,
            mode = ScheduleModeProto.SECTION_MODE
        )

        val byPosition = blocks.associateBy { it.courses.first().course.position }
        // 显示行顺序：1,2,3默认,3三教,4默认,4二教,4三教,5
        assertEquals(3f, byPosition.getValue("3415").startSection)
        assertEquals(7f, byPosition.getValue("3415").endSection)
        assertEquals(2f, byPosition.getValue("2301").startSection)
        assertEquals(6f, byPosition.getValue("2301").endSection)
        assertEquals(2f, byPosition.getValue("1301").startSection)
        assertEquals(5f, byPosition.getValue("1301").endSection)
        assertEquals(2f, byPosition.getValue("实验楼").startSection)
        assertEquals(5f, byPosition.getValue("实验楼").endSection)
    }
}
