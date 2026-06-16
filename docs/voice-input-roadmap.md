# 语音输入后续计划

## 1. 撤回输入按键

### 目标
在语音转录文本上屏后，提供一个「撤回」按钮，允许用户一键撤销本次语音输入的全部内容，恢复到语音输入前的文本状态。

### 方案要点
- **时机**：语音转录提交文本后，在键盘工具栏（KawaiiBar）上短暂显示撤回按钮（如 5 秒内或直至用户操作其他按键）
- **实现方式**：在 `VoiceInputController` 或 `CommitTextHandler` 中记录本次提交前后的文本快照（`beforeText` / `afterText`），撤回时通过 `InputConnection` 删除已提交文本并恢复原始内容
- **交互**：可在语音按钮旁显示撤销图标（如 `ic_baseline_undo_24`），点击后撤销并隐藏按钮

### 涉及文件
- `app/src/main/java/org/fcitx/fcitx5/android/voice/VoiceInputController.kt` — 记录文本快照、暴露撤销接口
- `app/src/main/java/org/fcitx/fcitx5/android/voice/domain/CommitTextHandler.kt` — 可能需要增加 `undo()` 回调
- `app/src/main/java/org/fcitx/fcitx5/android/input/bar/KawaiiBarComponent.kt` — 显示/隐藏撤回按钮 UI
- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt` — 撤销逻辑的 InputConnection 操作

---

## 2. 用户修改语音输入文本后的反馈通道

### 目标
当用户对语音转录结果进行修改（编辑、删除、追加）后，将原始转录文本与修改后文本的差异反馈给服务端，用于模型持续优化。

### 当前基础
已有反馈基础设施（位于 `app/src/main/java/org/fcitx/fcitx5/android/input/voice/`）：
- `VoiceFeedbackCollector` — 在录音开始前检查上一次 session 的文本是否被修改，收集差异
- `FeedbackRepository` — 内存队列存储反馈事件（最多 500 条）
- `FeedbackUploader` — 通过 Retrofit 上传反馈到服务端
- `TextDiffUtil` — Levenshtein 距离计算文本差异

当前流程：下一次按下录音按钮时，才检查并上传上一次的反馈。

### 方案要点
- **实时检测**：监听 `InputConnection` 的文本变更，在语音转录的 session 内检测用户是否修改了已上屏文本（不再仅依赖「下次录音时检查」）
- **主动上传**：不等待下次录音触发，用户修改后即异步上传反馈（或批量延迟上传以避免频繁请求）
- **Session 关联**：每条反馈携带 `sessionId`，关联到对应语音转录记录
- **更丰富的反馈数据**：
  - 原始转录文本（original）
  - 用户修改后文本（final）
  - 修改耗时（timing）
  - 修改类型（删除/替换/追加，type）
  - 上下文信息（应用包名、输入框类型等，context）

### 涉及文件
- `app/src/main/java/org/fcitx/fcitx5/android/input/voice/VoiceFeedbackCollector.kt` — 增强检测逻辑
- `app/src/main/java/org/fcitx/fcitx5/android/input/voice/FeedbackUploader.kt` — 主动上传策略
- `app/src/main/java/org/fcitx/fcitx5/android/input/voice/FeedbackRepository.kt` — 可能需要持久化
- `app/src/main/java/org/fcitx/fcitx5/android/voice/model/FeedbackUploadRequest.kt` — 扩展请求字段
- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt` — 注册文本变更监听
