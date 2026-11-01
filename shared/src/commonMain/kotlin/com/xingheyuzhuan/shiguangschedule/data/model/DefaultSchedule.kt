package com.xingheyuzhuan.shiguangschedule.data.model

import com.xingheyuzhuan.shiguangschedule.data.db.main.TimeSlot

/**
 * 软件内置的默认作息。
 *
 * 当前使用《湖南财政经济学院主校区上课作息时间》的 12 节安排。
 * 第 3、4 节中不同教学楼的时间差异由 [StaggeredSchedule] 在显示、提醒、小组件中动态换算，
 * 这里只保存全校统一的基准时间。
 */
object DefaultSchedule {

    /** 与 TimeSlot 解耦的模板，方便数据库迁移和新建课表时复用。 */
    data class SlotTemplate(
        val number: Int,
        val startTime: String,
        val endTime: String
    )

    /** 新建课表 / 首次启动时使用的 12 节默认作息。 */
    val slots: List<SlotTemplate> = listOf(
        SlotTemplate(1, "08:00", "08:45"),
        SlotTemplate(2, "08:55", "09:40"),
        SlotTemplate(3, "10:00", "10:45"),
        SlotTemplate(4, "10:45", "11:30"),
        SlotTemplate(5, "14:00", "14:45"),
        SlotTemplate(6, "14:55", "15:40"),
        SlotTemplate(7, "15:50", "16:35"),
        SlotTemplate(8, "16:45", "17:30"),
        SlotTemplate(9, "18:30", "19:15"),
        SlotTemplate(10, "19:25", "20:10"),
        SlotTemplate(11, "20:20", "21:05"),
        SlotTemplate(12, "21:15", "22:00")
    )

    /**
     * 旧版本的 13 节默认作息。
     * 数据库迁移时只把“与这份数据完全一致”的作息表更新为新默认值，
     * 用户自己改过的作息不会被覆盖。
     */
    val legacyThirteenSlots: List<SlotTemplate> = listOf(
        SlotTemplate(1, "08:00", "08:45"),
        SlotTemplate(2, "08:50", "09:35"),
        SlotTemplate(3, "09:50", "10:35"),
        SlotTemplate(4, "10:40", "11:25"),
        SlotTemplate(5, "11:30", "12:15"),
        SlotTemplate(6, "14:00", "14:45"),
        SlotTemplate(7, "14:50", "15:35"),
        SlotTemplate(8, "15:45", "16:30"),
        SlotTemplate(9, "16:35", "17:20"),
        SlotTemplate(10, "18:30", "19:15"),
        SlotTemplate(11, "19:20", "20:05"),
        SlotTemplate(12, "20:10", "20:55"),
        SlotTemplate(13, "21:10", "21:55")
    )

    fun toTimeSlots(tableId: String): List<TimeSlot> {
        return slots.map { slot ->
            TimeSlot(
                timeTableId = tableId,
                number = slot.number,
                startTime = slot.startTime,
                endTime = slot.endTime
            )
        }
    }
}
