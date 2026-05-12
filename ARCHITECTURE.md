---
name: architecture
description: 架构说明文档，用于介绍当前项目的模块划分、主流程、状态存储、架构优点、问题和后续演进方向。
metadata:
  short-description: 说明代码结构与主流程
---

# Architecture

> 这份文档是后续 AI 接手开发时最重要的参考之一，建议在结构发生变化时同步更新。

## 项目概览

当前工程是一个单模块 Android 应用：

- 根工程：Gradle Kotlin DSL
- 应用模块：`app`
- 包名：`cc.ai.zz`
- 最低版本：Android 13 (`minSdk = 33`)

## 架构结论摘要

当前架构已经可以支撑个人使用场景，但还处于“功能能用、结构偏轻”的阶段。它的主要问题不是功能缺失，而是职责边界还不够稳定，因此后续应优先做小规模整理，而不是继续直接堆功能。

当前约定里，场景时间参数采用统一语义：

- “固定 X 秒”默认表示主动作后的停留时长
- “返回后等待”“下一轮前缓冲”这类时间使用独立配置表达
- 不把主动作停留时长和阶段间隔合并为同一个时间参数

## 代码分层现状

当前代码仍保持单模块，但包结构已经按 `app / core / feature` 做了第一步轻量整理：

- `app`：应用级初始化，例如 `Application`
- `core`：跨 feature 的轻量基础能力，例如导航、权限协调
- `feature`：按业务能力拆分页面、自动化、悬浮窗、OCR

这一步的目标是先把“应用入口 / 基础能力 / 具体业务能力”分开，降低继续在根包平铺的维护成本；当前仍不引入多模块。

### 入口与应用生命周期

- [app/src/main/java/cc/ai/zz/app/MyApp.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/app/MyApp.kt)
  - 初始化全局 `context`
  - 初始化 `Toaster`
  - 初始化 `MMKV`

- [app/src/main/java/cc/ai/zz/feature/home/MainActivity.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/home/MainActivity.kt)
  - 作为应用启动入口与操作中心页面
  - 在页面创建时请求通知权限
  - 仅在固定周期任务启动前检查主悬浮窗权限
  - 在页面打开时同步任务状态；只有当前确实有周期任务或 OCR 在运行时，才会触发统一停止
  - 当前仍直接使用 `GestureEvent.ACT_*` 常量发命令，尚未再包一层页面侧领域入口

### 页面层

- [app/src/main/java/cc/ai/zz/feature/home/MainActivity.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/home/MainActivity.kt)
  - 展示操作中心页面
  - 触发固定间隔的上滑或点击任务
  - 发起 `MediaProjection` 授权并开启 OCR
  - 切换坐标定位悬浮窗，辅助读取 `CLICK` 所需的屏幕比例坐标
  - 当前上滑和点击任务都不默认绑定 OCR；后续新增场景时再按场景要求决定是否绑定
  - 在页面打开时停止当前周期任务并隐藏主悬浮窗，这是当前设计上的显式停止机制
  - 页面打开时只处理通知权限；无障碍状态在真正发起相关操作时再检查和引导

### 自动化执行层

- [app/src/main/java/cc/ai/zz/feature/automation/command/GestureEvent.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/automation/command/GestureEvent.kt)
  - 定义动作事件模型和动作常量

- [app/src/main/java/cc/ai/zz/feature/automation/command/GestureTask.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/automation/command/GestureTask.kt)
  - 通过 `Intent + GestureEvent` 将事件发送给服务
  - 统一封装服务启动方式：`ACT_STOP` 停止当前周期任务和当前 OCR，其余命令按前台服务语义启动

- [app/src/main/java/cc/ai/zz/feature/automation/service/GestureService.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/automation/service/GestureService.kt)
  - 前台服务
  - 接收动作事件
  - 统一按 `GestureEvent.action` 做服务内命令分发
  - 服务实例创建时先进入 `dataSync` 前台，OCR 真正启动后再升级到 `mediaProjection`
  - 作为 OCR 宿主，以 `mediaProjection` 类型前台服务启动截图会话
  - 同时承载周期任务与 OCR；`ACT_STOP` 统一停止当前周期任务和当前 OCR，并隐藏主悬浮窗，但不主动降级前台服务
  - 当前服务宿主按设计保持前台常驻；即使 OCR 未启动成功或当前任务已停止，也不以自动退出前台为目标
  - 当前不依赖系统重建后的自动恢复，任务与 OCR 需要由显式命令重新发起
  - 协调 OCR 与周期任务入口，并维护服务前台状态

- [app/src/main/java/cc/ai/zz/feature/automation/service/PeriodicTaskRunner.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/automation/service/PeriodicTaskRunner.kt)
  - 承接周期任务的启动、步骤调度和下一轮推进
  - 统一处理上滑、点击、返回等 plan 步骤执行
  - 周期任务运行中如果发现无障碍失效，会直接停止当前任务，不再继续进入下一轮
  - 协调倒计时展示与点击锚点读取

- [app/src/main/java/cc/ai/zz/feature/automation/executor/GestureAccessibilityService.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/automation/executor/GestureAccessibilityService.kt)
  - Android 无障碍服务
  - 真正执行系统返回、点击和上滑手势
  - 检查无障碍是否开启
  - 提供当前活跃窗口包名给 OCR 标注

- [app/src/main/java/cc/ai/zz/feature/automation/executor/GestureExecutor.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/automation/executor/GestureExecutor.kt)
  - 定义周期调度层所需的最小动作执行接口

- [app/src/main/java/cc/ai/zz/feature/automation/executor/AccessibilityGestureExecutor.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/automation/executor/AccessibilityGestureExecutor.kt)
  - 把 `GestureExecutor` 适配到无障碍服务实现

### OCR 层

- [app/src/main/java/cc/ai/zz/feature/ocr/coordinator/OcrCoordinator.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/ocr/coordinator/OcrCoordinator.kt)
  - 串起 OCR 完整流程
  - 控制 2 秒轮询，并将识别结果和失败状态输出到日志
  - 在识别完成后调用 OCR 规则引擎，按配置触发等待、返回或比例点击
  - 当前停止轮询时默认不释放截图会话，便于继续复用本次授权
  - 当前 `stop()` 会尽量取消当前轮并阻止旧轮继续回写结果；底层识别任务在极端时序下仍可能继续完成，但结果会被丢弃
  - 如果 `MediaProjection` 会话被系统回收，当前会停止 OCR 并要求重新授权

- [app/src/main/java/cc/ai/zz/feature/ocr/rule/OcrRuleEngine.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/ocr/rule/OcrRuleEngine.kt)
  - 负责加载并合并 OCR 规则
  - 规则来源包括默认规则、分辨率覆盖规则和外部 override
  - 负责规则匹配和动作执行编排

- [app/src/main/java/cc/ai/zz/feature/ocr/capture/ScreenCaptureProvider.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/ocr/capture/ScreenCaptureProvider.kt)
  - 基于 `MediaProjection` 创建并复用截图会话
  - 在同一会话内持续输出屏幕帧，避免每轮 OCR 重建 `VirtualDisplay`

- [app/src/main/java/cc/ai/zz/feature/ocr/recognize/LocalOcrProvider.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/ocr/recognize/LocalOcrProvider.kt)
  - 基于 ML Kit 本地 OCR 识别截图文字

### 悬浮窗层

- [app/src/main/java/cc/ai/zz/feature/overlay/manager/FloatingWindowManager.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/overlay/manager/FloatingWindowManager.kt)
  - 管理统一控制悬浮窗的显示、隐藏和状态更新

- [app/src/main/java/cc/ai/zz/feature/overlay/manager/CoordinateLocatorFloatingWindowManager.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/overlay/manager/CoordinateLocatorFloatingWindowManager.kt)
  - 管理坐标定位悬浮窗的显示与隐藏

- [app/src/main/java/cc/ai/zz/feature/overlay/view/BaseFloatingView.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/overlay/view/BaseFloatingView.kt)
  - 提供拖拽、单双击等通用能力

- [app/src/main/java/cc/ai/zz/feature/overlay/store/OverlayPositionStore.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/overlay/store/OverlayPositionStore.kt)
  - 统一管理悬浮窗位置的 `MMKV` 读写
  - 先把位置持久化从 View 中抽离，后续再按需要继续承接默认值、边界修正等策略

- [app/src/main/java/cc/ai/zz/feature/overlay/store/OverlayPositionKey.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/overlay/store/OverlayPositionKey.kt)
  - 统一描述某个悬浮窗对应的位置存储 key
  - 避免继续在 View 内散落 `KEY_X / KEY_Y` 这类裸字符串

- [app/src/main/java/cc/ai/zz/feature/overlay/view/MainFloatingView.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/overlay/view/MainFloatingView.kt)
  - 统一控制悬浮窗实例
  - 当前承担重新打开页面、倒计时展示和点击位置锚点

- [app/src/main/java/cc/ai/zz/feature/overlay/view/CoordinateLocatorFloatingView.kt](/Users/aschu/IdeaProjects/zz/app/src/main/java/cc/ai/zz/feature/overlay/view/CoordinateLocatorFloatingView.kt)
  - 提供一个可拖动的辅助定位浮窗
  - 实时展示当前中心点对应的屏幕比例坐标

## 当前主流程

### 启动流程

1. 用户点击应用图标
2. 展示 `MainActivity` 操作中心页面
3. 页面同步当前状态；只有检测到确实有运行中的周期任务或 OCR 时，才统一停止

### 启动任务流程

1. 用户在 `MainActivity` 中点击“开始上滑”或“开始点击”
2. 页面按固定参数构造 `GestureEvent`
3. 页面发送 `GestureEvent`
4. `GestureService` 接收事件并进入前台服务
5. 服务调用 `GestureAccessibilityService` 执行具体动作
6. 任务启动时显示主悬浮窗，并持续展示当前轮次剩余时间

补充说明：

- 上滑场景当前采用“每次上滑后固定等待 3 秒”的固定策略
- 点击场景当前采用“点击后停留 35 秒，返回后固定再等待 2.5 秒”的两段式固定策略

### 命令分发模型

1. 外部入口统一构造 `GestureEvent`
2. `GestureEvent.action` 作为 `GestureService` 的唯一命令分发键
3. 如需附加参数，仅通过 Intent extras 补充
4. 后续新增命令时，优先扩展 `GestureEvent.action`，不要再引入并行分发协议

### OCR 流程

1. 用户在 `MainActivity` 点击“开启 OCR”
2. 页面发起 `MediaProjection` 授权
3. `GestureService` 先满足前台服务启动时限，再在拿到有效授权结果后以 `mediaProjection` 类型前台服务运行 OCR
4. `ScreenCaptureProvider` 创建一次截图会话并持续复用
5. `OcrCoordinator` 每 2 秒触发一轮截图和 OCR
6. OCR 状态浮窗展示最近一次最终状态
7. OCR 识别结果写入日志，并交给规则引擎决定是否执行动作
8. OCR 智能模式不显示主倒计时悬浮窗

### 悬浮操作流程

1. 控制悬浮窗双击时重新打开操作页，同时按当前设计停止正在运行的任务
2. 控制悬浮窗可拖动，并记录位置到 `MMKV`
3. 点击动作使用控制悬浮窗当前位置作为自动化点击锚点
4. 坐标定位悬浮窗由操作页按钮切换显示，用于快速读取 `CLICK` 比例坐标

## 主要状态存储

- 悬浮窗位置通过 `MMKV` 持久化
- 当前位置持久化入口已收敛到 `OverlayPositionStore`
- 周期任务状态主要保存在 `PeriodicTaskRunner` 内存中
- OCR 截图会话状态主要保存在 `ScreenCaptureProvider` 内存中
- 当前没有统一的配置仓库或任务仓库
- 因此当前服务更适合“显式启动、显式停止”的轻量模型，不依赖系统回收后的自动恢复

## 当前架构优点

- 模块数量少，便于快速迭代
- 启动路径直接，适合个人工具场景
- 悬浮窗交互已经形成基本闭环
- 动作执行与无障碍服务已经打通

## 当前架构问题

- 页面层直接感知动作常量，缺少更稳定的领域模型
- `GestureService` 已先拆出周期调度器，但仍同时承担前台服务、命令分发、状态维护和 OCR 协调，职责仍偏重
- 点击动作依赖控制悬浮窗位置推导，逻辑仍有一定耦合
- OCR 对 `MediaProjection` 生命周期和系统行为较敏感，异常恢复策略还不够完整
- OCR 停止链路已经补上当前轮取消和旧结果隔离；后续仍可继续增强底层任务的强取消能力
- 运行中的无障碍失效已经会触发停任务兜底；后续仍可继续优化恢复引导与提示策略
- 已有面向 OCR 与前台服务链路的最小日志，但仍缺少统一日志、错误上报和状态观测入口
- 缺少测试覆盖，回归主要依赖手工验证

## 后续扩展放置约定

后续新增功能时，优先按现有边界落代码，不为了“更通用”提前加层。

- 新的页面入口、页面按钮、页面侧权限触发：优先放 `feature.home`
- 新的服务命令、命令常量、命令发送入口：优先放 `feature.automation.command`
- 新的周期场景、步骤模型、时间规则：优先放 `feature.automation.plan`
- 周期任务调度、步骤推进、运行中停任务策略：优先放 `feature.automation.service`
- 具体动作执行能力适配：优先放 `feature.automation.executor`
- 悬浮窗展示、拖拽、倒计时、位置持久化：优先放 `feature.overlay`
- OCR 截图、识别、轮询协调：优先放 `feature.ocr`
- 导航、权限协调这类跨 feature 的轻量能力：优先放 `core`

补充约定：

- 新需求如果只是扩展已有能力，优先在原 feature 内补齐，不新建平行 feature
- 只有某一块明显变胖、变难懂时，再针对那一块做局部整理
- 不为了“以后可能会用到”提前抽象多套实现

## 当前测试策略

- 当前优先覆盖纯逻辑层，例如 `GesturePlanFactory`、`GesturePlan / GestureStep`、`GestureEvent` 以及从 `GestureService` 中提炼出的轻量运行时计算逻辑
- `Activity`、`Service`、无障碍手势分发、悬浮窗交互等 Android 系统耦合较重的部分，暂时仍以手工验证为主
- 为了测试而做的代码整理应保持轻量：只接受小规模纯逻辑抽离，不为了覆盖率引入额外复杂架构

## 当前建议不要急着做的事

- 不要先引入多模块拆分
- 不要先引入复杂脚本编排系统
- 不要先重写为 Compose 页面
- 不要在没有统一动作模型前继续扩张动作种类

## 建议的下一步演进方向

第一阶段：

- 先补齐核心文档
- 补基础验证清单

第二阶段：

- 提炼动作模型和任务调度器
- 把页面触发逻辑与调度逻辑分离
- 把配置和状态访问集中化

第三阶段：

- 增加更多动作类型
- 支持动作序列
- 增加更清晰的运行状态反馈

## 相关文档

- 任务池见 [TASKS.md](/Users/aschu/IdeaProjects/zz/TASKS.md)
- 手工回归清单见 [MANUAL_TESTS.md](/Users/aschu/IdeaProjects/zz/MANUAL_TESTS.md)

## 当前维护建议

后续开发时，这份文档只在以下情况下更新：

- 模块职责发生变化
- 核心调用流程发生变化
- 明确完成了一次小规模架构整理
