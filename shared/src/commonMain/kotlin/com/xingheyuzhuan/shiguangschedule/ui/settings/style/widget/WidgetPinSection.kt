package com.xingheyuzhuan.shiguangschedule.ui.settings.style.widget

import androidx.compose.runtime.Composable

/**
 * 平台专属的「添加到桌面」区块声明。
 *
 * Android 上通过 RequestPinAppWidget 直接请求桌面固定挂件；
 * 桌面端与 iOS 端没有等价能力，actual 实现为空。
 */
@Composable
expect fun PlatformWidgetPinSection()
