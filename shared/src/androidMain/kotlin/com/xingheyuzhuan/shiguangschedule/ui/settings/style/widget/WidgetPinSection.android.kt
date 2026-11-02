package com.xingheyuzhuan.shiguangschedule.ui.settings.style.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import hufeschedule.shared.generated.resources.Res
import hufeschedule.shared.generated.resources.action_cancel
import hufeschedule.shared.generated.resources.action_go_home
import hufeschedule.shared.generated.resources.text_widget_pin_manual_steps
import hufeschedule.shared.generated.resources.text_widget_pin_picker_row
import hufeschedule.shared.generated.resources.text_widget_pin_steps_huawei
import hufeschedule.shared.generated.resources.text_widget_pin_steps_oppo
import hufeschedule.shared.generated.resources.text_widget_pin_steps_vivo
import hufeschedule.shared.generated.resources.text_widget_pin_steps_xiaomi
import hufeschedule.shared.generated.resources.title_widget_pin
import hufeschedule.shared.generated.resources.title_widget_pin_manual
import hufeschedule.shared.generated.resources.title_widget_pin_pick
import hufeschedule.shared.generated.resources.toast_widget_pin_adding
import hufeschedule.shared.generated.resources.widget_pin_compact
import hufeschedule.shared.generated.resources.widget_pin_double_days
import hufeschedule.shared.generated.resources.widget_pin_tiny
import hufeschedule.shared.generated.resources.widget_pin_vertical

/**
 * 挂件类型与 Provider 类名后缀。
 *
 * 四个 Provider 都定义在 :androidApp 模块（共享模块无法反向引用），
 * 因此这里按类名拼装 ComponentName。修改 Provider 类名时需同步 AndroidManifest。
 */
private val PIN_TARGETS = listOf(
    "widget.tiny.TinyNativeProvider",
    "widget.compact.CompactNativeProvider",
    "widget.double_days.DoubleDaysNativeProvider",
    "widget.list_vertical.ListVerticalNativeProvider"
)

/** 判定窗口：给系统确认框留出的时间 */
private const val PIN_VERIFY_DELAY_MS = 2500L

/** 常见国产 ROM 的组件入口各不相同，按厂商给不同指引 */
private enum class RomBrand { VIVO, OPPO_X, XIAOMI, HUAWEI, OTHER }

private fun detectRomBrand(): RomBrand {
    val id = buildString {
        append(Build.MANUFACTURER).append(' ')
        append(Build.BRAND).append(' ')
        append(Build.PRODUCT)
    }.lowercase()
    return when {
        "vivo" in id || "iqoo" in id -> RomBrand.VIVO
        "oppo" in id || "realme" in id || "oneplus" in id || "oplus" in id || "oneplus" in id ->
            RomBrand.OPPO_X
        "xiaomi" in id || "redmi" in id || "poco" in id || "blackshark" in id -> RomBrand.XIAOMI
        "huawei" in id || "honor" in id -> RomBrand.HUAWEI
        else -> RomBrand.OTHER
    }
}

private val RomBrand.guideText: StringResource
    get() = when (this) {
        RomBrand.VIVO -> Res.string.text_widget_pin_steps_vivo
        RomBrand.OPPO_X -> Res.string.text_widget_pin_steps_oppo
        RomBrand.XIAOMI -> Res.string.text_widget_pin_steps_xiaomi
        RomBrand.HUAWEI -> Res.string.text_widget_pin_steps_huawei
        RomBrand.OTHER -> Res.string.text_widget_pin_manual_steps
    }

/**
 * 一键固定挂件（收纳成单行：点一下 → 选类型 → 尝试添加）。
 *
 * 首选 requestPinAppWidget()：桌面弹系统确认框，一步到位。
 * 但国产 ROM 经常把请求吞掉，且有两种失败形态：
 *  1. 直接返回 false；
 *  2. 返回 true，确认页闪一下就关，用户什么都没看到。
 * 所以用「系统里该 Provider 的挂件实例数是否真的增加」作为唯一判据：
 * 窗口期结束仍未增加、且本页已回到前台 → 判失败，按手机厂商给出对应路径的手动指引。
 */
@Composable
actual fun PlatformWidgetPinSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val brand = remember { detectRomBrand() }

    var showPicker by remember { mutableStateOf(false) }
    var manualGuideFor by remember { mutableStateOf<String?>(null) }
    val addingMessage = stringResource(Res.string.toast_widget_pin_adding)

    val labels = listOf(
        stringResource(Res.string.widget_pin_tiny),
        stringResource(Res.string.widget_pin_compact),
        stringResource(Res.string.widget_pin_double_days),
        stringResource(Res.string.widget_pin_vertical)
    )

    fun attemptPin(providerClass: String, label: String) {
        ToastManager.show(addingMessage)
        val component = ComponentName(
            context.packageName,
            "${context.packageName}.$providerClass"
        )
        val before = runCatching {
            appWidgetManager.getAppWidgetIds(component).size
        }.getOrDefault(0)

        val requested = runCatching {
            appWidgetManager.requestPinAppWidget(component, null, null)
        }.getOrDefault(false)

        if (!requested) {
            manualGuideFor = label
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val after = runCatching {
                appWidgetManager.getAppWidgetIds(component).size
            }.getOrDefault(0)
            val backInForeground = lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.RESUMED)
            // 挂件数没增加且已回到前台 → 确认页被 ROM 吞了，走手动指引
            if (after <= before && backInForeground) {
                manualGuideFor = label
            }
        }, PIN_VERIFY_DELAY_MS)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.title_widget_pin),
            style = MaterialTheme.typography.bodyLarge
        )

        // 不支持 pin 的桌面也不要藏掉入口：点了会直接落到厂商手动指引
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.text_widget_pin_picker_row),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = labels.joinToString(" / ") { it.substringAfter(' ') },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "  ›",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 第一步：选类型
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(Res.string.title_widget_pin_pick)) },
            text = {
                Column {
                    PIN_TARGETS.forEachIndexed { index, providerClass ->
                        val label = labels[index]
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPicker = false
                                    attemptPin(providerClass, label)
                                }
                                .padding(vertical = 14.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (index != PIN_TARGETS.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    // 第二步：系统没搞定时的厂商专属指引
    manualGuideFor?.let { label ->
        AlertDialog(
            onDismissRequest = { manualGuideFor = null },
            title = { Text(stringResource(Res.string.title_widget_pin_manual)) },
            text = { Text(stringResource(brand.guideText, label)) },
            confirmButton = {
                TextButton(onClick = {
                    manualGuideFor = null
                    // 直接回到桌面，省得用户自己找
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) {
                    Text(stringResource(Res.string.action_go_home))
                }
            },
            dismissButton = {
                TextButton(onClick = { manualGuideFor = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}
