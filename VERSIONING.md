# 版本管理指南

本项目用 **Git + 语义化版本 + Git 标签** 管理版本。下面说明：版本号改哪里、怎么发版、日常怎么提交。

---

## 1. 版本号的唯一来源

| 用途 | 文件与字段 | 当前值 |
| --- | --- | --- |
| Android 内部版本号 | `androidApp/build.gradle.kts` → `versionCode` | `36` |
| Android 对外版本名 | `androidApp/build.gradle.kts` → `versionName` | `"2.1.0"` |
| 桌面端版本 | `desktopApp/build.gradle.kts` → `val appVersion` | `"2.1.0"` |
| 发布锚点 | Git 标签 | `v2.1.0` |

**三条硬性约束：**

1. **三处版本号与 Git 标签必须一致**：`2.1.0` ↔ `v2.1.0`。不允许 APK 是 2.1.0、标签却是 v2.0.9。
2. **`versionCode` 只增不减**，每次发版 `+1`。Android 靠它判断新旧，重复或回退会导致升级安装失败。
3. **遵循语义化版本 `主.次.补丁`**：
   - `2.1.1` —— 只修 bug，行为不变
   - `2.2.0` —— 新增功能，向下兼容
   - `3.0.0` —— 不兼容变更（数据库结构、导入脚本格式、配置项改名等）

`versionName` / `appVersion` / 标签是给人看的，`versionCode` 是给系统比大小的，三者一起改，别漏。

---

## 2. 发版流程（推荐用脚本）

### 方式 A：一键脚本

```powershell
# 先确认工作区干净
git status

# 预览要改什么（不写文件、不提交）
./scripts/release.ps1 -Version 2.2.0 -DryRun

# 确认无误后正式执行
./scripts/release.ps1 -Version 2.2.0
```

脚本会做：`versionCode +1` → 改 `versionName` 和 `appVersion` → 把 CHANGELOG 的 `[Unreleased]` 归档为 `[2.2.0]` → 提交 → 打附注标签。最后手动推送：

```bash
git push
git push origin v2.2.0
```

### 方式 B：手工四步

```bash
# 1. 确认干净、能编译
git status
./gradlew :androidApp:assembleDebug

# 2. 改三处版本号
#    androidApp/build.gradle.kts : versionCode = 37 , versionName = "2.2.0"
#    desktopApp/build.gradle.kts : val appVersion = "2.2.0"

# 3. 写 CHANGELOG，然后提交
git add -A
git commit -m "chore(release): v2.2.0"

# 4. 打附注标签并推送
git tag -a v2.2.0 -m "release 2.2.0"
git push && git push origin v2.2.0
```

> 标签一定要用 `-a`（附注标签）。轻量标签没有打标签人、日期和说明，发版记录会缺一半信息。

### 发版检查清单

- [ ] 测试通过、能过编译
- [ ] `versionCode` 已 +1（且比历史所有值都大）
- [ ] `versionName` / `appVersion` / 标签三者一致
- [ ] CHANGELOG 已更新并能看懂"用户视角改了什么"
- [ ] 标签已推送到远端（`git push origin --tags` 或单独推）
- [ ] 安装包发在 GitHub Releases，**不要提交进仓库**

---

## 3. 日常开发

```bash
git switch -c feature/课程提醒        # 开分支（功能完成就合、就删）
# ... 改代码，多次提交 ...
git add -p                            # 按块暂存，比 add . 精确
git commit -m "feat: 支持按周次提醒"

git switch main
git pull
git merge --no-ff feature/课程提醒    # 保留合并记录
git branch -d feature/课程提醒
git push
```

**分支命名：** `feature/xxx`（新功能）、`fix/xxx`（修 bug）、`hotfix/x.y.z`（线上救火）、`refactor/xxx`（重构）。分支活过两周就会打架，短开短合。

紧急修线上问题时，从标签拉分支，不要从当前 main：

```bash
git switch -c hotfix/2.1.1 v2.1.0
# 修完 → 升 PATCH 版本 → 打标签 v2.1.1 → 合回 main
```

---

## 4. 提交信息的写法

格式：`类型: 说明`（Conventional Commits），中文说明即可。

| 类型 | 含义 | 对版本号的影响 |
| --- | --- | --- |
| `feat` | 新功能 | 次版本 +1 |
| `fix` | 修 bug | 补丁 +1 |
| `refactor` | 重构，不改行为 | 不变 |
| `perf` | 性能优化 | 补丁 +1 |
| `docs` | 文档 | 不变 |
| `build` | 构建/依赖 | 不变 |
| `chore` | 杂务、发版提交 | 不变 |
| `feat!` 或正文写 `BREAKING CHANGE:` | 不兼容改动 | 主版本 +1 |

一条 commit 只做一件事，"改了一堆"这种提交三个月后自己都看不懂。

---

## 5. 出错了怎么救

| 场景 | 命令 |
| --- | --- |
| 改乱了想丢弃 | `git restore <文件>` |
| 提交信息写错（未推送） | `git commit --amend` |
| 提交到错的分支 | `git reset --soft HEAD~1`，切分支重提 |
| 误删分支 / 提交 | `git reflog` 找哈希 → `git switch -c rescue <哈希>` |
| 已推送的提交要撤销 | `git revert <哈希>`（不要用 reset 改写公共历史） |
| 标签打错了（未推送） | `git tag -d v2.2.0`，改完重新打 |
| 标签已推送要改 | 删除本地和远端标签：`git tag -d v2.2.0 && git push origin :refs/tags/v2.2.0`，再重打 |
| 必须强推 | `git push --force-with-lease`（**永远不要**用 `--force`） |

`git reflog` 是保命符：只要提交过，几乎都能找回来。

---

## 6. 第一次推到 GitHub

本仓库目前只有本机历史，没有远端。步骤：

1. 在 GitHub 新建**空仓库**（不要勾选 README / .gitignore / License），仓库名建议 `shiguangschedule`。
2. 关联并推送：

```bash
git remote add origin https://github.com/<你的用户名>/shiguangschedule.git
git push -u origin main
git push origin v2.1.0
```

首次 push 会弹出凭据窗口登录；不想配 SSH 就用 HTTPS + Personal Access Token（密码处填 token）。

3. 回到 `CHANGELOG.md`，把底部两个链接里的 `你的用户名` 换成真实用户名，提交一次。

---

## 7. 关于上游（可选）

源码来自 `XingHeYuZhuan/shiguangschedule`，是 **zip 导入**，不含上游提交历史（`v2.1.0` 这个标签是本项目自己的基线，不是上游的标签）。

- **只维护自己的分支**：不用管上游，按本文档正常发版即可。
- **想跟随上游新版本**：加一个只读远端，先看差异再手工挑拣，不要直接 merge（两边没有共同祖先，会产生整仓冲突）：

```bash
git remote add upstream https://github.com/XingHeYuZhuan/shiguangschedule.git
git fetch upstream
git diff --stat v2.1.0 upstream/main     # 看上游改了什么
# 需要某次改动时：git cherry-pick <哈希>，或手工改
```

---

## 8. 常见坑

1. **把 `local.properties` 提交了** —— 里面是本机 `sdk.dir`，换台电脑就报错。已忽略，别用 `git add -f`。
2. **把 `build/`、`.gradle/`、`.kotlin/` 提交了** —— 几百 MB 垃圾。已忽略，首次 `git add -A` 前先看 `git status`。
3. **提交了签名文件（`*.jks` / `*.keystore`）** —— 等于泄露发布密钥，已忽略；万一已经提交，立刻更换密钥。
4. **只推代码不推标签** —— 发布记录丢失，`git push` 不会自动推标签。
5. **改完版本号忘了打标签** —— 半年后无法回答"线上那个包是哪个 commit"。
6. **用 `git add .` 一把梭** —— 会混进临时文件，用 `git status` 或 `git add -p` 过一眼。
