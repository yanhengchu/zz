# OCR 规则配置

## 1. 文件位置

- 默认内置规则文件放在 [ocr_rules_default.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_rules_default.csv)
- 当前常用的 1172x2748 设备覆盖文件放在 [ocr_rules_1172x2748.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_rules_1172x2748.csv)
- `1080x2313` 设备覆盖文件放在 [ocr_rules_1080x2313.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_rules_1080x2313.csv)
- OCR 易错字字典维护在 [ocr_confusions.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_confusions.csv)
- OCR 清洗配置维护在 [ocr_clean_config.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_clean_config.csv)
- [ocr_clean_config.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_clean_config.csv) 当前只从 `assets` 内置加载，不能像外部实验规则一样在手机上直接修改；如果要调整过滤文案或清洗字符，需要重新打包/安装
- 外部实验规则优先使用 `/sdcard/Android/data/cc.ai.zz/files/ocr/ocr_rules.override.csv`
- 加载顺序是：先读默认内置规则，再按当前分辨率尝试读取同名覆盖文件；如果没有精确命中，会按当前 `w/h` 选择一份比例最接近的分辨率覆盖文件；最后再读外部 CSV；如果 `id` 相同，后加载的规则覆盖前面的规则
- 应用启动时会自动创建外部 CSV 覆盖文件，方便直接用 Excel / WPS 编辑
- 内置默认规则、分辨率覆盖规则和外部实验规则统一维护 CSV，列结构保持一致

当前分辨率覆盖规则的口径是：

- 默认规则维护在 `ocr_rules_default.csv`
- 应用运行时会按当前屏幕像素尝试读取 `ocr_rules_<width>x<height>.csv`
- 如果没有对应分辨率文件，会从现有分辨率文件里按 `width / height` 选择一份比例最接近的覆盖文件
- 分辨率文件里只需要写“需要覆盖的规则”，没写的规则继续沿用默认规则
- 分辨率文件支持“按 `id` 局部覆盖”：`id` 必填，其他列留空时表示继承默认规则；最常见的写法就是只覆盖 `action_target / else_target`

## 2. 配置示例

最常编辑的基础字段是：

- `id`
- `pkg`
- `keywords`
- `action_type`

按当前使用习惯：

- 规则有冲突时，再补 `priority`
- 需要排查某条规则时，再补 `log`
- 点击类规则，再补 `action_target`

如果只是临时加一条实验规则，推荐先按最小写法配置，其他字段都可以省略，系统会走默认值。

最小示例：

CSV 外部实验规则推荐这样写：

```csv
id,priority,log,timeout,pkg,keywords,action_type,value_policy,action_target,else_target
exp_back,0,0,,,"广告|领取成功",BACK,,,
```

说明：

- `keywords` 用 `|` 分隔，表示这些词都要命中
- `/` 主要用于不同场景下的别名或等价表达，例如 `继续领奖励/领取奖励`
- 如果关键词本身需要 `/` 字符，例如 `4/5`，请写成 `\/`，例如 `今日打卡任务 4\/5`
- 如果关键词后面跟动态时间，可以用 `mm:ss` 作为时间占位符，例如 `倒计时mm:ss/倒计吋mm:ss`
- 如果关键词中间有不固定数字，可以用 `num` 作为数字占位符，例如 `看视频再得num金币/看广告视频再得num金币`
- 只想快速实验时，通常只改 `id / keywords / action_type`
- `CLICK` 时再补 `action_target`
- 如果需要基于动态时间变化决定是否执行动作，再补 `value_policy`
- 如果需要按 `num` 的阈值在两个点击点之间分支，再补 `else_target + value_policy`

别名示例：

```csv
video_swipe,10,0,,,"首页|倒计时mm:ss/倒计吋mm:ss",SWIPE,UNCHANGED,,
```

OCR 易错字字典示例：

```csv
canonical,variants
已,"已|己|乙"
持,"持|待"
时,"时|吋|盯"
领,"领|顿|额"
```

字典约束：

- `ocr_confusions.csv` 当前按“单字混淆字典”使用
- `canonical` 必须是单个字符
- `variants` 里也只建议填写单个字符，用 `|` 分隔
- 不要把 `领取`、`继续` 这类多字词组直接写进字典；词组级差异仍然优先放回规则里的 `/` 别名
- 如果某个差异不适合做全局单字归一化，也不要放进字典，避免影响其他规则

OCR 清洗配置示例：

```csv
type,value
remove_ascii_punctuation,1
remove_ascii_letters,1
remove_chars,"，。、；！？·（）"
drop_line_prefix,"ocr:"
drop_line_contains,"调试浮窗"
drop_line_exact,"完整过滤文案"
```

说明：

- `ocr_clean_config.csv` 当前是随包内置配置，不参与外部 override 合并
- `remove_ascii_letters=1` 时，会额外去掉 OCR 文本里的 `a-z` 和 `A-Z`
- 当前规则侧会保护这些保留字，不会因为 `remove_ascii_letters=1` 被误删：`num`、`mm:ss`、`ALL`、`TIMEOUT_ALL`
- 想快速试验过滤词或清洗字符时，仍然需要修改项目里的 asset 后重新安装应用

默认规则、分辨率覆盖规则和外部实验规则都推荐直接按这套 CSV 结构维护：

```csv
id,priority,log,timeout,pkg,keywords,action_type,value_policy,action_target,else_target
exp_back,0,0,,,"广告|领取成功",BACK,,,
```

分辨率覆盖示例：

```csv
id,priority,log,timeout,pkg,keywords,action_type,value_policy,action_target,else_target
ad_next,,,,,,,,0.48:0.55,0.48:0.61
```

说明：

- 这条覆盖只改 `ad_next` 的两个点击点位
- 其他字段例如 `pkg / keywords / action_type / value_policy / log` 都继续继承默认规则
- 分辨率覆盖文件里的 `id` 必须已经存在于默认规则中；如果找不到对应默认规则，这条覆盖会被忽略并记 warning 日志
- `ocr_rules_1080x2313.csv` 和 `ocr_rules_1172x2748.csv` 当前都按这种 patch 方式维护，不再承担“默认完整规则”的职责

完整 CSV 示例：

```csv
id,priority,log,timeout,pkg,keywords,action_type,value_policy,action_target,else_target
ad_wait,10,0,,,"广告|后可领奖励",WAIT,,,
ad_back,10,1,,,"广告|领取成功",BACK,,,
ad_next,20,0,,,"广告|领取成功|再看一个视频继续领奖励",CLICK,LT:300,0.48:0.56,0.82:0.56
video_swipe,10,0,,,"首页|倒计时mm:ss/倒计吋mm:ss",SWIPE,UNCHANGED,,
```

## 3. 顶层结构

- CSV 没有 `rules` 顶层，直接一行一条规则。

## 4. 规则字段说明

- `id`：规则唯一标识，用于日志、排查和区分规则
- `priority`：优先级，放在 `id` 后面；值越大越先匹配；可省略，默认 `0`
- `pkg`：可选；规则生效的包名范围。空着表示全场景生效；写 1 个表示只对单个包名生效；写多个时用 `|` 分隔，命中任意一个即可
- `log`：是否在该规则命中时额外打印这一轮 OCR 原始识别文本；放在 `priority` 后面；默认 `0`
- `keywords`：必须全部命中的关键词列表
  其中同一关键词位可用 `/` 写多个别名，命中任意一个即可
  `/` 主要用于不同场景下的等价表达，不建议把所有 OCR 错别字都堆在这里
  如果关键词本身包含 `/`，请写成 `\/`
  如果关键词后面跟动态时间，可以写 `mm:ss` 作为时间占位符
  如果关键词中间有动态数字，可以写 `num` 作为数字占位符
  可以写 `ALL` 表示任意非空 OCR 结果都视为命中
  可以写 `TIMEOUT_ALL` 表示只有在对应 `timeout` 已触发时才视为命中
- `action_type`：命中后的执行动作类型
- `action_target`：仅 `CLICK` 时需要填写的主点击比例坐标，格式 `x:y`
- `else_target`：仅 `CLICK` 时可选；当数值阈值条件不满足时使用的备用点击坐标，格式 `x:y`
- `value_policy`：可选；当前支持动态时间比较和数值阈值比较

结论：

- 常用基础字段：`id / pkg / keywords / action_type`
- 规则冲突时：再补 `priority`
- 需要排查某条规则时：再补 `log`
- 点击类规则：再补 `action_target`

## 4.1 当前执行流程

- 每轮 OCR 会先按 `priority` 从高到低处理规则；值越大越优先
- 同一 `priority` 内，按 CSV 当前顺序逐条做 `keywords` 匹配
- 某条规则 `keywords` 命中后，会继续做 `value_policy` 判断：
  - 主动作可执行：立即执行，结束本轮
  - 主动作不可执行，但有 `else_target` 可走：执行备用点击，结束本轮
  - `value_policy` 不满足，且没有 `else_target`：记为 `skip`，继续尝试同优先级里的下一条规则
- 如果同优先级里前面规则 `skip`，后面同优先级规则仍然有机会命中并执行
- 如果当前优先级里已经出现过命中规则，但最后都只是 `skip`，本轮不会再继续尝试更低优先级规则
- 只有当更高优先级规则完全没有任何 `keywords` 命中时，才会继续看更低优先级规则
- `ALL`：表示任意非空 OCR 结果都视为命中
- `TIMEOUT_ALL`：只在对应 `timeout` 已触发时才会命中，普通 OCR 轮次不会直接命中
- `timeout` 当前语义是“源规则执行成功后，等待目标规则在后续轮次命中”；不是“时间一到立刻直接执行动作”

## 5. 动作类型

- `WAIT`：不执行任何操作，继续等待下一轮 OCR
- `BACK`：执行一次返回操作
- `SWIPE`：执行一次上滑操作
- `CLICK`：按屏幕比例点击指定位置

## 5.1 动态时间比较

- 规则关键词里写了 `mm:ss`，并且配置了 `value_policy`，就会自动提取动态时间值并参与比较
- `value_policy=CHANGED`：本次提取值和上次不同才执行动作
- `value_policy=UNCHANGED`：本次提取值和上次相同才执行动作
- 当前首页倒计时场景更推荐 `value_policy=UNCHANGED`，也就是倒计时不变时才执行上滑
- 如果配置了 `value_policy`，但关键词里没有 `mm:ss`，则回退到普通规则执行，不会阻断动作
- OCR 停止或重新启动后，这份“上次动态值”状态会清空，重新开始比较
- `log=1`：该规则命中时，会额外把这一轮 OCR 原始识别文本打印到日志里，方便排查

## 5.2 数值阈值点击分支

- 规则关键词里写了 `num`，并且 `CLICK` 同时配置了 `action_target + else_target + value_policy` 时，可以按数字阈值在两个点位之间分支
- `num` 表示“从当前关键词里提取一段整数”，例如：
  - `看视频再得num金币` 可以匹配 `看视频再得568金币`
  - `num再看一个视频继续领奖励` 可以匹配 `1200再看一个视频继续领奖励`
- 当前支持的阈值表达有：`LT:300`、`LTE:300`、`GT:300`、`GTE:300`、`EQ:300`
- 各表达式含义：
  - `LT:300`：提取到的数字 `< 300` 时满足条件
  - `LTE:300`：提取到的数字 `<= 300` 时满足条件
  - `GT:300`：提取到的数字 `> 300` 时满足条件
  - `GTE:300`：提取到的数字 `>= 300` 时满足条件
  - `EQ:300`：提取到的数字 `== 300` 时满足条件
- 规则命中后：
  - 条件满足：点击 `action_target`
  - 条件不满足：如果配置了 `else_target`，点击 `else_target`
  - 条件不满足且未配置 `else_target`：跳过本轮动作

示例：

```csv
ad_next,2,,1,"再看一个视频继续领奖励|num金币|继续领奖励/领取奖励|坚持退出",CLICK,0.50:0.56,0.82:0.56,GT:300
```

当前项目的默认口径：

- `value_policy` 表示“什么时候走 `action_target`”
- 不满足条件且配置了 `else_target` 时，走 `else_target`
- 如果 `num` 没有成功提取出来，当前会回退到普通执行，也就是默认走 `action_target`

## 6. CLICK 字段

- `action_target` / `else_target` 格式都是 `x:y`
- `x`：点击横坐标占屏幕宽度的比例，建议范围 `0.0 ~ 1.0`
- `y`：点击纵坐标占屏幕高度的比例，建议范围 `0.0 ~ 1.0`

示例：

- `0.50:0.56` 表示点击屏幕宽度 50%、高度 56% 附近
- `0.82:0.56` 表示点击屏幕宽度 82%、高度 56% 附近

## 7. 当前建议的匹配口径

- 默认使用“多关键词同时命中”来判断当前页面状态
- 不建议只使用单个泛化词，例如仅配置 `广告`
- `/` 主要用于不同场景下的别名或等价表达，例如 `继续领奖励/领取奖励`
- OCR 高概率出现的单字错别字，优先维护在 [ocr_confusions.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_confusions.csv) 中统一归一化
- [ocr_confusions.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_confusions.csv) 只建议维护单字混淆，不建议写多字词组
- 如果需要额外清洗某些整行文案，或统一维护“匹配前要去掉的字符”，优先维护在 [ocr_clean_config.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_clean_config.csv)
- [ocr_clean_config.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_clean_config.csv) 当前只能随包内置修改，不能像外部规则 CSV 一样在设备上热更新
- 只有当某个错别字属于词组级差异、或不适合做全局单字归一化时，才考虑在规则里用 `/` 补别名
- 当前像 `后可领奖励/后可顿奖励/后可额奖励` 这类只差单字 OCR 误识别的场景，更推荐收敛到字典里，而不是在规则里写多组近似词
- OCR 里的倒计时这类动态文案，优先用 `mm:ss` 占位符表达，不要把具体时间写死
- OCR 里像金币数、积分数这类动态数字，优先用 `num` 占位符表达，不要把具体数字写死
- 代码层在匹配前会先读取 [ocr_clean_config.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_clean_config.csv) 做整行过滤和字符清洗
- `remove_ascii_punctuation=1` 时，会去掉常见 ASCII 标点
- `remove_ascii_letters=1` 时，会去掉 OCR 文本里的英文大小写字母
- `remove_chars` 用于补充需要统一去掉的字符，例如 `·`
- `drop_line_prefix / drop_line_contains / drop_line_exact` 用于整行过滤不想参与 OCR 匹配的文案
- 代码层会先按 [ocr_confusions.csv](/Users/aschu/IdeaProjects/zz/app/src/main/assets/ocr_confusions.csv) 做单字归一化，再进行规则匹配
- 规则关键词也会走同一套匹配前归一化；但 `num`、`mm:ss`、`ALL`、`TIMEOUT_ALL` 这些保留字会先被保护，不会因为清洗规则丢失语义
- 如果两条规则可能同时命中，应给更具体的规则更高优先级
- 推荐命中后只执行优先级最高的一条规则

## 8. 新增规则建议

新增规则时，优先按“页面状态 + 动作”来命名，例如：

- `ad_wait`
- `ad_back`
- `ad_cont`

同时建议遵循以下约束：

- 每条规则尽量配置 `2~4` 个关键词，提高匹配精度

## 9. 后续扩展建议

如果后续需要新增配置，优先沿着这几个方向扩展：

- 增加新的 `action.type`
- 增加按包名限制规则生效范围
- 增加局部区域 OCR 匹配，而不是全屏文本匹配

第一版先保持字段最少，便于维护和排查。
