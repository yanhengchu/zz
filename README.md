---
name: readme
description: 项目总览文档，用于快速说明当前 Android 自动化手势工具的定位、现有功能、权限依赖、构建方式和文档入口。
metadata:
  short-description: 快速了解项目现状
---

# zz

`zz` 是一个个人使用的 Android 自动化手势工具。应用通过无障碍服务、前台服务和按需显示的主悬浮窗，在后台执行简单手势动作，减少重复手动操作。

## 当前阶段

项目当前采用“轻量协作”模式：

- 核心流程先保持稳定
- 需求尽量小步推进
- 由 AI 负责实现，开发者负责设计、评审和验收

## 当前功能

- 启动应用后请求通知权限，并提供固定任务与 OCR 入口
- 打开操作页，展示固定任务与 OCR 的操作入口
- 支持周期性上滑
- 支持周期性点击后再返回
- 支持在控制悬浮窗上查看当前轮次的剩余时间
- 支持开启本地 OCR：授权后复用同一截图会话，每 2 秒识别当前页面文字，显示简洁状态，并按规则触发动作
- 支持显示坐标定位悬浮窗，拖动时实时查看点击中心点的 x、y 屏幕比例坐标

## 当前代码结构

当前工程仍保持单模块，但代码包结构已经做了第一步轻量整理：

- `app`：应用级初始化
- `core`：跨功能的基础能力，例如导航、权限协调
- `feature.home`：操作页入口
- `feature.automation`：命令、计划模型、服务宿主、无障碍执行
- `feature.overlay`：悬浮窗 manager、view、位置存储
- `feature.ocr`：截图、识别、流程协调

当前悬浮窗位置持久化已经从 View 中抽离，统一收敛到 `OverlayPositionStore`；位置 key 由 `OverlayPositionKey` 描述，避免继续在悬浮窗 View 中散落裸字符串。

当前命令分发约定：

- 所有页面、悬浮窗和 OCR 入口，统一通过 `GestureEvent.action` 向 `GestureService` 发命令
- 如果某个命令需要额外参数，只通过 Intent extras 补充，不再新增第二套 `Intent.action` 分发
- 后续新增服务命令时，默认按这套模型扩展

当前时间语义约定：

- 场景里写的“固定 X 秒”，默认指主动作执行后的停留时长
- 如果还存在“返回后等待”“下一轮前缓冲”这类时间，它们属于独立配置，不和主动作停留时长混用
- 后续新增场景时，也统一沿用这套拆分方式，避免把总间隔和阶段间隔写成同一个时间概念

## 当前交互

- 启动入口是 [app/src/main/java/cc/ai/zz/feature/home/MainActivity.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/home/MainActivity.kt)
- 控制悬浮窗：
  - 仅在“开始上滑”“开始点击”这类固定周期任务运行时显示
  - 双击：重新打开操作页；按当前设计，进入操作页会停止正在运行的任务
  - 拖动：调整自动点击位置
- 坐标定位悬浮窗：
  - 通过“定位屏幕坐标”按钮显示或隐藏
  - 可拖动，内部实时展示当前中心点的 `x / 屏幕宽度`、`y / 屏幕高度`
  - 主要用于辅助填写 OCR `CLICK` 规则坐标
- 操作页：
  - 打开页面时会先停止当前任务并隐藏主悬浮窗
  - 固定间隔执行动作：上滑固定 3 秒；点击后停留固定 35 秒，返回后固定再等 2.5 秒
  - 开始上滑
  - 开始点击
  - 开启 OCR
  - 定位屏幕坐标
  - OCR 智能模式不显示主倒计时悬浮窗，但会显示简洁的 OCR 状态浮窗
  - 如果无障碍服务未开启，会引导跳转到系统无障碍设置页

## 当前场景需求

后续新增场景时，建议统一按 `一句话说明 + 用户操作 + 系统执行 + 关键约束 + 验收` 的格式补充，方便人阅读，也方便 AI 直接映射到实现。

### 周期上滑

用于按设定节奏持续执行上滑，替代用户反复手动上滑。

- 用户操作：
  用户打开操作页，点击“开始上滑”启动任务。
  任务运行过程中，双击悬浮窗可回到操作页并结束当前任务。
- 系统执行：
  任务启动后先等待 5 秒，再执行第一次上滑。
  每完成一次上滑后，固定等待 3 秒进入下一轮，并持续循环。
  悬浮窗展示当前轮次剩余时间。
- 关键约束：
  打开操作页视为停止当前任务。
  “固定 3 秒”指每次上滑后的下一轮等待，不和首次启动前的固定 5 秒等待混用。
  执行依赖无障碍服务可用。
  动作成功后默认不主动 toast，用户可直接从页面反馈观察执行结果；仅失败或异常时主动提示。
- 验收：
  点击“开始上滑”后任务能正常启动。
  首次上滑前有 5 秒等待。
  后续可以持续上滑，并能看到倒计时变化。
  返回操作页后的行为符合预期。

### 点击后返回

用于按固定流程循环执行“点击目标位置，停留一段时间，再返回”。

- 用户操作：
  用户打开操作页，点击“开始点击”启动任务。
  任务启动后，用户可在首次点击前通过拖动悬浮窗调整自动点击位置。
  任务运行过程中，双击悬浮窗可回到操作页并结束当前任务。
- 系统执行：
  任务启动后先等待 5 秒，再执行第一次点击。
  每一轮都按“点击 -> 等待固定 35 秒 -> 返回 -> 等待固定 2.5 秒”的顺序执行。
  返回后的 2.5 秒结束后，进入下一轮点击。
  悬浮窗展示当前轮次剩余时间。
- 关键约束：
  点击位置取自主悬浮窗当前位置，不单独配置点击坐标。
  如果当前拿不到点击位置，本轮点击跳过，但任务继续执行后续流程。
  “固定 35 秒”指点击后的停留时长；返回后的固定 2.5 秒等待是另一段独立间隔。
  当前周期点击任务默认不绑定 OCR；后续新增场景时，再按场景要求决定是否绑定 OCR。
  执行依赖无障碍服务可用。
  动作成功后默认不主动 toast，用户可直接从页面反馈观察执行结果；仅失败或异常时主动提示。
- 验收：
  点击“开始点击”后任务能正常启动。
  首次点击前有 5 秒等待。
  首次点击前可以通过拖动悬浮窗调整点击位置。
  后续能稳定循环执行点击、等待、返回、再点击。
  拖动悬浮窗后，后续点击位置会跟着变化。
  返回操作页后的行为符合预期。

## 依赖权限

- 悬浮窗权限：显示统一控制悬浮窗
- 无障碍服务：执行上滑、点击、返回等自动化动作
- 通知权限：显示前台服务通知
- 屏幕录制权限：用于 `MediaProjection` 屏幕截图，支持本地 OCR

补充说明：

- OCR 在 Android 14+ 上依赖 `MediaProjection` 的前台服务能力
- `GestureService` 会在创建时先进入 `dataSync` 前台，再在 OCR 启动时升级到 `mediaProjection`，避免前台服务启动超时
- 当前实现会在一次授权后复用同一截图会话，避免每轮 OCR 重建截图会话导致 `SecurityException`
- 当前实现默认持续持有本次截图会话，不会因为单次轮询结束就主动释放
- 当前页面入口下，用户每次手动重新开启 OCR，仍会重新触发系统录屏授权；当前“复用截图会话”主要发生在单次已启动的 OCR 轮询过程中
- 当前服务宿主一旦进入前台，会按当前设计继续保持前台，不因为单次 OCR 未启动、当前任务停止或 OCR 停止而自动降级
- 如果系统主动结束当前投屏会话，当前版本会停止当前 OCR，并要求用户重新授权后再开启
- 无障碍服务通常不需要每次重新授权，但部分 ROM 在“从最近任务划掉应用”后也可能直接关闭本应用的无障碍开关；当前版本会在真正发起需要无障碍的操作时再检查并引导用户处理
- 如果周期任务运行中检测到无障碍已经失效，当前版本会停止当前周期任务，不再继续进入下一轮循环

## 构建

在项目根目录执行：

```bash
./gradlew assembleRelease
```

当前已验证可以成功产出：

- `app/build/outputs/apk/release/app-release-unsigned.apk`

## 技术栈

- Android SDK
- Kotlin
- Gradle Kotlin DSL
- Android View XML
- Coroutine
- MMKV

## 当前限制

- 仅支持少量固定动作，尚未支持复杂脚本编排
- 点击坐标目前依赖控制悬浮窗位置推导
- 当前页面仍以传统 View 为主，短期内不准备切到 Compose
- 自动化逻辑、页面逻辑、悬浮窗逻辑还可以继续解耦
- OCR 当前仍以页面文字识别、状态浮窗、日志输出和规则触发为主，结果未做结构化分析
- OCR 对系统版本和 `MediaProjection` 行为较敏感，长期稳定性仍需继续手工验证
- OCR 当前停止时会尽量立即取消当前轮并阻止旧结果继续回写；底层识别任务在极端时序下仍可能继续完成，但结果会被丢弃
- `ACT_STOP` 当前用于统一停止当前周期任务和当前 OCR；进入操作页时，如果检测到确实有运行中的工作，才会触发它来清掉自动化状态
- 前台服务宿主当前按设计保持常驻；即使当前没有正在执行的周期任务或 OCR，也不以“自动降级前台服务”为目标
- 当前任务和 OCR 状态主要保存在内存中；如果系统直接回收进程或重建服务，不保证能自动恢复到回收前的运行状态
- `GestureService` 当前不依赖系统重建后的自动恢复；如果宿主被系统回收，需要由页面或用户重新显式发起任务
- 部分系统会把悬浮窗统计为“后台弹窗/后台显示界面”行为；当前项目的主悬浮窗仍可能触发这类系统提示

## 推荐协作方式

后续开发尽量只提供这 4 项信息：

- 目标：这次想做什么
- 交互：用户怎么操作
- 不做什么：这次先不碰哪些部分
- 验收：怎么才算完成

AI 会基于这 4 项直接接手实现，并在完成后说明改动、风险和验证结果。

## 测试协作约定

- 默认优先为纯逻辑补充单元测试，例如计划生成、模型默认值、调度计算
- 对 `Activity`、无障碍服务、悬浮窗、系统权限跳转这类系统耦合较重的部分，优先保留手工验证，不强行补 UT
- 只有当代码本来就值得提炼时，才顺手做有助于测试的小整理；不要单纯为了补测试把业务结构改重
- 提需求时如果希望补测试，建议在验收里明确写“补本次新增纯逻辑对应的 UT”

## 文档导航

- [README.md](/Users/aschu/IdeaProjects/zz/README.md)：项目入口与现状总览
- [ARCHITECTURE.md](/Users/aschu/IdeaProjects/zz/ARCHITECTURE.md)：代码结构、主流程与演进方向
- [OCR.md](/Users/aschu/IdeaProjects/zz/OCR.md)：OCR 功能流程、运行方式与日志/状态策略
- [OCR_RULES.md](/Users/aschu/IdeaProjects/zz/OCR_RULES.md)：OCR 规则、分辨率覆盖、清洗配置、错别字字典的唯一详细说明
- [TASKS.md](/Users/aschu/IdeaProjects/zz/TASKS.md)：任务池与后续优先级
- [MANUAL_TESTS.md](/Users/aschu/IdeaProjects/zz/MANUAL_TESTS.md)：核心功能最小手工回归清单

## 文档维护约定

后续改需求时，优先按下面这套分工维护，尽量避免同一件事在多份文档里重复展开：

- [README.md](/Users/aschu/IdeaProjects/zz/README.md)
  - 维护项目入口、当前能力、主要交互、权限要求和文档导航
  - 不展开 OCR 规则字段、占位符、分辨率覆盖等细节
- [ARCHITECTURE.md](/Users/aschu/IdeaProjects/zz/ARCHITECTURE.md)
  - 维护模块职责、主流程、代码边界和后续扩展放置约定
  - 不做规则字段级说明，只保留“规则加载/执行链路”的概览
- [OCR.md](/Users/aschu/IdeaProjects/zz/OCR.md)
  - 维护 OCR 功能目标、运行流程、日志策略、状态展示和风险限制
  - 不再作为规则配置说明来源；规则字段、占位符、清洗配置等统一引用 [OCR_RULES.md](/Users/aschu/IdeaProjects/zz/OCR_RULES.md)
- [OCR_RULES.md](/Users/aschu/IdeaProjects/zz/OCR_RULES.md)
  - 作为 OCR 规则配置的唯一详细来源
  - 统一维护：CSV 字段、`pkg`、`mm:ss`、`num`、`value_policy`、分辨率覆盖、`ocr_clean_config.csv`、`ocr_confusions.csv`
- [MANUAL_TESTS.md](/Users/aschu/IdeaProjects/zz/MANUAL_TESTS.md)
  - 只维护手工验收步骤、前置条件和预期结果
  - 不重复解释规则原理和字段语义，涉及配置时直接引用 [OCR_RULES.md](/Users/aschu/IdeaProjects/zz/OCR_RULES.md)
- [TASKS.md](/Users/aschu/IdeaProjects/zz/TASKS.md)
  - 只维护任务池、优先级、已确认但暂不处理的决策和实验结论
  - 不承载详细设计说明

建议按下面这条口径决定改哪份文档：

- 改了项目入口、首页交互、权限口径：优先改 [README.md](/Users/aschu/IdeaProjects/zz/README.md)，必要时再补 [MANUAL_TESTS.md](/Users/aschu/IdeaProjects/zz/MANUAL_TESTS.md)
- 改了模块职责、代码放置边界、核心流程：优先改 [ARCHITECTURE.md](/Users/aschu/IdeaProjects/zz/ARCHITECTURE.md)
- 改了 OCR 运行流程、日志、状态浮窗、风险口径：优先改 [OCR.md](/Users/aschu/IdeaProjects/zz/OCR.md)
- 改了 OCR 规则字段、规则文件、分辨率覆盖、清洗配置、错别字字典：只详细改 [OCR_RULES.md](/Users/aschu/IdeaProjects/zz/OCR_RULES.md)，其他文档最多轻量引用
- 改了手工回归路径或验收标准：改 [MANUAL_TESTS.md](/Users/aschu/IdeaProjects/zz/MANUAL_TESTS.md)
- 只是记录后续计划、暂缓项或实验结论：改 [TASKS.md](/Users/aschu/IdeaProjects/zz/TASKS.md)

## 当前建议

日常开发优先关注这 3 份文档：

- [README.md](/Users/aschu/IdeaProjects/zz/README.md)
- [ARCHITECTURE.md](/Users/aschu/IdeaProjects/zz/ARCHITECTURE.md)
- [TASKS.md](/Users/aschu/IdeaProjects/zz/TASKS.md)

其余文档默认作为参考，不要求每次开发都修改。

后续新增功能时，代码放置规则以 [ARCHITECTURE.md](/Users/aschu/IdeaProjects/zz/ARCHITECTURE.md) 中的“后续扩展放置约定”为准，默认沿现有 feature 边界扩展，不为了拆而拆。
