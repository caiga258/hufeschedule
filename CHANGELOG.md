# 更新日志

本项目的所有重要变更都记录在此文件。

- 格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)
- 版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)
- 发版时由 `scripts/release.ps1` 自动把 `[Unreleased]` 归档为新版本

## [Unreleased]

### 新增

- 一级页面（今日课表 / 课表 / 我的）改为横向分页容器，支持手指横滑切换，拖动跟手、松手自动吸附。
- 课表页顶部新增「导入」入口，一键进入课表导入/导出。
- 内置湖财主校区 12 节默认作息（`DefaultSchedule`），新建课表与首次启动自动使用。
- 默认开学日期设为每年 9 月 5 日（`DefaultSemester`）：新安装、新建课表时自动填入最近一次 9 月 5 日。
- 第 3、4 节错峰规则（`StaggeredSchedule`）：以学校《主校区上课作息时间》（附件 3）为唯一依据，
  按「当前节次 × 上课教室」判定时间，课表、提醒、小组件统一换算。
- 新增湖财（HUFE）离线学校适配资源。
- 增加 `.gitattributes`、`CHANGELOG.md`、`VERSIONING.md` 与 `scripts/release.ps1` 发版脚本。

### 修改

- 应用更名为「财院课程表」（繁体「財院課程表」、英文 `Caiyuan Schedule`），桌面端窗口标题同步。
- 数据库版本 7 → 8，附带旧 13 节作息到新 12 节的纯数据迁移。
- 课表页周次切换改为标题栏的周次选择器，左右横滑让给一级页面切换。
- 第 3、4 节的错峰判定改为显式的「节次 × 教室」矩阵：第 3 节 2 种上课时间、第 4 节 3 种，
  判定时同时使用当前节次和上课教室，不再依赖「默认行 + 例外」的推断。
- 恢复上游的 iOS 构建配置（本地 Windows 临时注释的依赖与 KSP 配置已还原）。

### 修复

- 修复一级页面切换动画方向固定、与页面位置不符的问题：切换方向现在跟随页面在导航栏中的位置。
- 修复从二级页面返回一级页面时可能滚到错误页面的问题。
- 修复切换一级页面后导航栏残留上一页折叠状态的问题。

## [2.1.0] - 2026-11-01

### 说明

- 首次纳入 Git 管理，作为后续开发与发版的基线。
- 内容为上游 `XingHeYuZhuan/shiguangschedule` 的源码快照（zip 导入，不含上游提交历史）。
- 对应 `versionCode = 36`、Android `versionName = 2.1.0`、桌面版 `appVersion = 2.1.0`。

[Unreleased]: https://github.com/caiga258/hufeschedule/compare/v2.1.0...HEAD
[2.1.0]: https://github.com/caiga258/hufeschedule/releases/tag/v2.1.0
