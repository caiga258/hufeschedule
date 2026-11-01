package com.xingheyuzhuan.shiguangschedule

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 一级页面（今日课表 / 课表 / 我的）的横向分页容器。
 *
 * 页面顺序取自 [MainTabDestinations]，和底部导航栏从左到右的顺序完全一致，
 * 所以：
 * - 手指从右往左滑 → 切到右边的页面，新页面从右侧滑入；
 * - 手指从左往右滑 → 切到左边的页面，新页面从左侧滑入；
 * - 点击导航栏同样是让这个容器滚动到目标页，切到左边的页就是新页面从左侧滑入，
 *   不会再出现「不管往哪切都是同一个方向」的问题。
 *
 * 手指拖动过程中页面跟手移动，松手后按速度和位置决定停在当前页还是下一页。
 */
@Composable
fun MainTabPager(
    pagerState: PagerState,
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    onNavHideFractionChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        // key 必须是能塞进 Bundle 的类型（String/Int 等），页面顺序固定所以直接用下标
        key = { index -> "main_tab_$index" },
        // 预组合相邻页面，滑动/点击切换时目标页已经准备好，不会先闪一下空白
        beyondViewportPageCount = 1
    ) { index ->
        ScreenContent(
            targetDest = MainTabDestinations[index],
            onNavigate = onNavigate,
            onBack = onBack,
            onNavHideFractionChanged = onNavHideFractionChanged
        )
    }
}
