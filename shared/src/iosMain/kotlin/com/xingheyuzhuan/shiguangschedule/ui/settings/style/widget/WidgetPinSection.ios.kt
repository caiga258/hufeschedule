package com.xingheyuzhuan.shiguangschedule.ui.settings.style.widget

import androidx.compose.runtime.Composable

/**
 * iOS 挂件需由独立的 WidgetKit Extension 提供，无法从主 App 一键固定，
 * 因此这里保持空实现。
 */
@Composable
actual fun PlatformWidgetPinSection() {
    // No-op：iOS 端暂不提供一键添加挂件
}
