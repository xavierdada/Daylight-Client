# Daylight 改动验证清单

对应改动：全局改名 nightx → daylight、白色主题、圆角风格、模块按键绑定、五种动画、
主菜单背景换成启动图、主菜单改横向并显示 `Daylight Client`、Dynamic Island 图标改为代码绘制、
HUD 混合状态泄漏修复、删除主菜单的 API 状态与 Changelog 显示。

> 说明：这份清单里 **0 和 1** 是必过项（不通过就别往下看），**2~7** 是功能项，
> **8** 是 Dynamic Island 图标（在最后）。

---

## 0. 环境与构建

```powershell
cd D:\NightX-Reborn-master\NightX-Reborn-master
.\gradlew.bat --status
```

| 检查 | 预期 |
|---|---|
| daemon 的 JVM | 显示 `1.8`（gradle.properties 里已把 `org.gradle.java.home` 钉到 `C:/Users/HP/.jdks/jdk8u504-b01`） |

```powershell
.\gradlew.bat build
```

| 检查 | 预期 |
|---|---|
| 结果 | `BUILD SUCCESSFUL` |
| 产物 | `build\libs\daylight-indev.jar` |
| 日志里**不应**出现 | `Unresolved reference: drawRect` |
| | `The integer literal does not conform to the expected type Int` |
| | `NoClassDefFoundError: java/util/jar/Pack200` |

零散的 `Version string 'indev' does not match SemVer` 和 `Deprecated Gradle features` 是**正常的**，
前者是 `mod_version=indev` 的提示，后者来自 Gradle 6.9.1。

```powershell
.\gradlew.bat runClient
```

| 检查 | 预期 |
|---|---|
| 启动 | Minecraft 1.8.9 窗口出现 |
| 日志里**不应**出现 | `ClassCastException: ... AppClassLoader cannot be cast to ... URLClassLoader` |
| 首次进入的画面 | **主菜单**（浅色背景 + 深色文字），**不是** Forge 的 mods 列表 |

---

## 1. HUD 信息面板（本轮修复重点）

### 1.1 人工观察

左上角 `<玩家名> Info` 面板：

| 检查 | 预期 |
|---|---|
| 四行文字（标题 / Played / Kills / YourDarling） | **全部是深色** `#1C1C22`，颜色一致 |
| 每行背后 | **没有**黑色矩形块 |
| 面板底色 | 浅灰（与半透明底叠加后约 `(199,197,197)`，不是纯白，这是正常的） |
| 面板边框 | 浅黄（`hudAccent`） |
| 左侧头像 | 玩家皮肤头部，正常显示 |

> 反面特征（修复前就是长这样）：数据行变成**白色像素字 + 紧贴的纯黑实心块**，
> 而标题行正常 —— 因为标题画在 `drawCustomHead` 之前。

### 1.2 自动化判定

游戏内按 **F2** 截图，然后：

```powershell
cd D:\NightX-Reborn-master\NightX-Reborn-master
.\tools\verify-hud.ps1                       # 自动找最新的 F2 截图
# 或指定文件：
.\tools\verify-hud.ps1 -Screenshot "C:\path\to\2026-01-01_12.00.00.png"
```

| 输出 | 含义 |
|---|---|
| `bug signature: < 400` + `RESULT: PASS` | 通过（exit 0） |
| `bug signature: > 400` + `RESULT: FAIL` | 失败（exit 1），信息面板又出现黑块了 |
| `exit 2` | 找不到截图，用 `-Screenshot` 指定 |
| `exit 3` | 图片读不了 |

### 1.3 这个脚本本身已经过自检

用同一台机器上的两张图跑过，结果符合预期：

| 输入 | pure white | bug signature | 判定 |
|---|---|---|---|
| 修复前的截图 | 1491 | **1260** | FAIL（exit 1） |
| 把黑块抹掉的模拟修复图 `_sim_fixed.png` | 307 | **76** | PASS（exit 0） |

判定逻辑：统计**左上角 HUD 区域**内「纯白像素（>250）在 3px 内存在纯黑像素（<20）」的个数。
正常的主题文字是 `#1C1C22`，既不纯黑也不纯白，所以计数接近世界背景的噪声水平（~76）。
阈值 400 距离两侧都有约 5 倍余量。

`_sim_fixed.png` 留在工作区根目录，作为**反向对照样本**，可随时复跑：

```powershell
.\tools\verify-hud.ps1 -Screenshot D:\NightX-Reborn-master\_sim_fixed.png   # 应 PASS
```

---

## 2. 按键绑定（每个模块）

打开 ClickGUI（默认右 Shift）→ 右键任一模块展开设置 → 最后一行。

| 步骤 | 预期 |
|---|---|
| 展开模块 | 最后一行显示 `Bind: None` |
| 左键该行 | 变成 `Bind: ...`（进入监听） |
| 按 `R` | 显示 `Bind: R` |
| 关闭 GUI，按 `R` | 该模块开关切换 |
| 重新展开，左键该行后按 **ESC** | 绑定变回 `None`，**GUI 不关闭** |
| 右键该行 | 立即清除绑定 |
| 没有设置项的模块 | 右键**也能**展开（只有按键绑定一行） |

> 回归点：监听状态下按 ESC 不应该关掉 ClickGUI —— 这是 `SmoothClickGui.keyTyped`
> 把按键先路由给按键行的结果。

---

## 3. 主菜单（布局 + 背景）

| 检查 | 预期 |
|---|---|
| 背景 | 浅色渐变，**没有** `Launching Daylight...` 字样 |
| 背景是否随鼠标移动 | **不**移动（原来的鼠标视差已去掉） |
| **按钮布局** | SINGLE PLAYER / MULTI PLAYER / ALT MANAGER / OPTIONS / EXIT **横向排成一排**，不再是竖列 |
| 标题 | 按钮排上方居中显示 **`Daylight Client`**（加载完成、进入主菜单后可见） |
| 窗口拉窄 / 调大 GUI Scale 时 | 五个按钮**等比缩小**并保持一排，不换行、不溢出屏幕 |
| 右上角 | **没有** `API Connection:` 这一行 |
| 左上角 | **没有** `Changelogs:` / `Known Bugs:` 区块 |
| 右下角 | `Daylight Client` + `Your currently build is <版本>`，**没有** `(API Dead)` / `(Outdated)` / `(Latest)` 后缀 |
| 按钮 | 浅灰填充；鼠标悬停时**变深**；文字深色 |
| 飘落粒子 | 浅灰小方块，在白底上**可见** |

---

## 4. 五种动画

| # | 动画 | 观察点 | 反例（说明没生效） |
|---|---|---|---|
| 1 | 模块展开/收起 | 右键模块，设置区高度**平滑增长**，下面的模块一起往下滑 | 设置区瞬间整块跳出 |
| 2 | 鼠标悬停 | 模块行 / 分类表头的底色**渐变** | 底色瞬间切换 |
| 3 | 打开 GUI | 按右 Shift，面板从**屏幕中心放大**出现，背景遮罩同时渐暗 | 面板直接出现在最终位置 |
| 4 | 面板拖动惯性 | 按住分类表头快速甩动后松手，面板**继续滑一小段**再停 | 松手立刻停住 |
| 5 | 分类折叠 | 右键表头，模块列表被**裁掉**着收起（不是整块消失） | 列表瞬间消失 |

> 可选边界检查：把 `Gui` 模块的 `Scale` 改成 `0.4` 或 `2.0`，再展开模块 ——
> 动画和裁剪应对齐，不应该出现内容被裁错位置。
> （`SmoothConstants.uiScale` 就是为了让 scissor 框跟上额外缩放。）

---

## 5. 圆角风格

| 检查 | 预期 |
|---|---|
| ClickGUI 分类表头 / 模块行 / 设置行 | 四角约 4px 圆角，行与行之间有细缝 |
| 模块启用时的高亮 | 圆角胶囊 |
| 展开的 Float/Int 滑条 | 不超出圆角卡片 |
| 主菜单、选项、多人游戏等原版界面按钮 | 圆角（原来是直角） |
| 原版界面按钮文字 | 深色（原来是白色，白底上看不见） |

---

## 6. 回归检查（确认没改坏）

| 检查 | 预期 |
|---|---|
| 聊天栏 | 半透明黑底 + 白字，能正常淡出 |
| 右上 ArrayList 模块列表 | 深色文字可读，**无黑块** |
| 左上 `Daylight` 水印 | 深色文字 + 浅黄描边 |
| TargetHud（打人时中间的头像+血量） | 名字/血量深色可读，**无黑块** |
| 血条 / 护甲 / 快捷栏 | 正常 |
| ESC 暂停菜单、背包 | 按钮圆角、文字深色 |
| ClickGUI 各分类能否拖动 | 正常 |

> 第 6 项里的三处「无黑块」和第 1 项是**同一个根因**，一起看：
> `drawCustomHead` / `drawRoundedAvatar` / `drawSmoothOutlineRect` 现在都会在结束时
> 保持混合开启，并且不再混用裸 `glEnable/glDisable` 与 `GlStateManager`。

---

## 7. 已删除 / 未处理

### 7.1 本次删掉的 API 与 Changelog

主菜单上的 `API Connection: <OK|No>` 状态行、左上 `Changelogs:` 区块、右上 `Known Bugs:`
区块，以及版本号后面的 `(Latest)` / `(API Dead)` / `(Outdated)` 后缀。连带清理：

| 位置 | 删除内容 |
|---|---|
| `APIConnecter` | `changelogs` / `bugs` 字段，`checkChangelogs()` / `checkBugs()` 方法 |
| `URLComponent` | `CHANGELOGS` / `BUGS` 常量 |
| `Launch` | 启动时的 `checkChangelogs()` / `checkBugs()` 调用（少两次注定失败的请求） |
| `GuiMainMenu` | 上面几段绘制代码，以及刷新按钮里的两个调用 |

保留的：`checkStatus()`（`Discord` RPC 和 `Donate` 链接还在用它返回的字段）、
`loadPictures()`、`loadDonors()`、`checkStaffList()`。

> 验证方式：主菜单左上角和右上角应当是**空的**（只有背景图）；右下角两行是
> `Daylight Client` 和 `Your currently build is <版本>`，没有括号后缀。
> 启动日志里也不应再出现 `Loaded Changelogs` / `Loaded Bugs`。

### 7.2 仍然依赖远端 API 的地方

| 项 | 说明 |
|---|---|
| 披风 | 远端贴图缺失 → 回退到纯米色 `Cape.kt` 的 `beigeCape` |
| 主菜单 logo / 粒子 | 远端贴图缺失 → logo 回退成文字，粒子用占位贴图并染成浅灰 |
| `YourDarling` 的值 | 来自 `APIConnecter`，API 挂掉时显示 `None` |
| `Donate` 按钮 | 链接来自 `checkStatus()` 的返回值 |

这些都是把 URL 和贴图名一起改名（`nightx` → `daylight`）的既定代价，不是 bug。

### 7.3 其它未处理

| 项 | 说明 |
|---|---|
| DropDown 风格 | `Gui` 模块 → `Style = DropDown` 仍是直角、无按键绑定、无动画。那是另一套绘制代码。 |
| 披风观感 | 纯米色 `#F2E8D5`，没有明暗和褶皱。要改色在 `Cape.kt` 的 `beigeCape`。 |

---

## 8. Dynamic Island（深色胶囊 + 新通知）

整个模块按参考图重写过。常态是**深蓝半透明胶囊**、白字、段间 ` · ` 分隔，只有三段。

| 检查 | 预期 |
|---|---|
| 第 1 段 | **太阳**图标 + `Daylight` |
| 第 2 段 | **人形**图标 + 你的玩家名 |
| 第 3 段（默认） | **显示器**图标 + 当前 FPS |
| 第 3 段（Scaffold 开启时） | **方块**图标 + 快捷栏可放置方块总数 |
| 分隔符 | ` · `，颜色比正文暗一档（`#96AABE`） |
| 图标风格 | 全部白色实心、同一粗细、无渐变 |
| 音乐 / Ping / 时间 | **不存在了**（对应设置项也已删除） |

### 8.2 模块开关通知（对照参考图）

| 检查 | 预期 |
|---|---|
| 布局 | 左边一个约 24x24 的**圆角图标卡片**（比胶囊底色亮一档），右边两行文字 |
| 第 1 行 | 白色 `<模块名> Toggled`（例如 `KillAura Toggled`） |
| 第 2 行（普通模块） | `<模块名> has been ` 白色 + `Enabled!` **绿** `#5FAE53` / `Disabled!` **红** `#BB5565` |
| 第 2 行（Scaffold 开启时） | `156 blocks left` 白色 + ` - 6.03 block/s` **灰** `#969BAA` |
| 图标（普通模块 / ArmorBreak） | **iOS 开关**：白色轨道 + 灰圆点在右（开启），深色轨道 + 浅灰圆点在左（关闭） |
| 图标（Scaffold） | **白色线框立方体**（外方框 + 内部小方框 + 三条直边），开和关都用它 |
| 底部进度条 | 圆角、浅紫 `#C9C4F0`、高 4px、宽度=内容宽，按剩余显示时间从满到空；为 0 时不画 |
| 两行文字 | 左对齐（都缩进到图标卡片右侧） |

> 验证方法：进游戏按顺序开、关同一个模块（例如 KillAura）。两次通知的
> **开关圆点位置**和**状态词颜色**应当相反。
> Scaffold 的速率来自 `Scaffold.blocksPerSecond`（最近约 1 秒内成功放置的方块数），
> 由 `Scaffold.onPacket` 里发往服务器的 `C08PacketPlayerBlockPlacement` 计数。

### 8.3 动画与既有功能（不应失效）

| 检查 | 预期 |
|---|---|
| 弹出 / 收起 | 胶囊宽高与透明度有弹簧动画，不是硬切 |
| `BackgroundBlur` | 关掉后胶囊没有模糊层 |
| `Border` / `BorderThickness` | 边框可开关、可调粗细 |
| `BackgroundAlpha` / `CornerRadius` | 可调透明度与圆角 |
| `NoDisappear` + `CutApart` | 通知来临时胶囊分成上下两块；关掉 `CutApart` 时两块边框连成一体 |
| `AnimSpeed` / `Bounciness` | 动画速度与回弹可调 |
| `ShowFPS` | 关闭后第三段消失（**但 Scaffold 开启时方块数仍然显示**） |

### 8.4 已删除 / 待你决定

| 项 | 说明 |
|---|---|
| 通知的倒计时进度条 | 已按新参考图重新加回（底部浅紫圆角条），设置在代码里是 `PROGRESS_HEIGHT` / `progressGap` / `progressPadding` |
| `GEAR` 图标 | 代码保留但**当前无调用点**（通知里的齿轮被开关/立方体图标取代） |
| **Scaffold 方块数重复** | `Scaffold.onRender2D` 里**另有一处** `"N Blocks"`（画在屏幕中央偏上，由 `Interface` 模块的 `ScaffoldCounte` 开关控制）。开启 Scaffold 时同一信息会**同时出现在准星和岛上**，需要决定只保留哪一处 |
| 深色 vs 浅色 | 按参考图做成了深色。要与白色主题一致的话，改 `DynamicIsland.kt` 第 46-59 行的颜色常量即可 |
| 未使用的图标贴图 | `assets/minecraft/daylight/` 下 7 个 PNG（`logo/user/music/fps/ping/clock/gear`）**已无任何代码引用**，是死资源（约 480 KB） |
