package com.xingheyuzhuan.shiguangschedule.data.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 湖财的学期默认设置。
 *
 * 湖财秋季学期统一在 **9 月 5 日** 开学，所以新安装或新建课表时直接把开学日期填成
 * 「最近一次 9 月 5 日」，用户不必先去设置页手动设置一遍：
 * - 8 月及以后取今年 9 月 5 日（暑假期间首页会显示距开学还有几天）；
 * - 1—7 月取去年 9 月 5 日（此时正处于同一学年的春季学期）。
 *
 * 用户仍可在「我的 → 设置开学日期」里改成别的日期，值以用户保存的为准。
 */
object DefaultSemester {

    /** 开学月份：9 月。 */
    private val startMonth: Month = Month.SEPTEMBER

    /** 开学日期：5 日。 */
    private const val START_DAY = 5

    /**
     * 默认开学日期，格式与数据库/导出一致：`yyyy-MM-dd`。
     */
    fun defaultStartDate(): String {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        val year = if (today.month >= Month.AUGUST) today.year else today.year - 1
        return LocalDate(year, startMonth, START_DAY).toString()
    }
}
