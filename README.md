# Interview Cards · 面试题库卡片 App

> 把 Markdown 面试题库「编译」成卡片数据，装进一个 Android App —— 桌面端维护题库，手机端翻卡复习。

一个自用的面试复习小工具，由两部分组成：

| 组件 | 说明 |
|---|---|
| **`android-app/`** | Android App（Kotlin + Jetpack Compose），卡片式浏览、翻卡、标记、随手记 |
| **`scripts/`** | Node 脚本，把 Markdown 题库编译成 App 读取的 JSON |

> ⚠️ **本仓库只包含"软件"，不含任何题目内容。** 题库数据由使用者自行准备（见下文）。

---

## 一、整体设计

```
┌──────────────────────────────┐
│  ① Markdown 题库（唯一数据源）  │   ← 自己维护，例如 qa/*.md
└───────────────┬──────────────┘
                │  node scripts/gen-cards.mjs（编译）
                ▼
┌──────────────────────────────┐
│  ② 卡片数据 cards.json / 分册    │   ← 结构化 JSON
└───────────────┬──────────────┘
                │  放入 App assets，或推到手机外部目录
                ▼
┌──────────────────────────────┐
│  ③ Android App：翻卡复习        │
└──────────────────────────────┘
```

**核心思路**：App **不解析 Markdown、不联网**，只读结构化 JSON —— 逻辑极简、启动快。

---

## 二、目录结构

```
.
├── android-app/                 # Android 工程
│   ├── app/src/main/
│   │   ├── assets/cards/        # ★ 卡片数据（本仓库为空，需自行生成）
│   │   ├── java/com/caj/interviewcards/
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/            # 数据模型 / 加载器 / 进度 / 笔记存储
│   │   │   └── ui/              # 主界面 / 卡片 / 目录 / 笔记 / Markdown 渲染
│   │   └── res/
│   ├── build.gradle.kts
│   └── README.md                # App 详细说明
└── scripts/
    ├── gen-cards.mjs            # ★ 题库 Markdown → 卡片 JSON
    ├── gen-index.mjs            # 生成题库总索引
    └── gen-merged.mjs           # 生成合并版
```

---

## 三、App 功能

| 模块 | 功能 |
|---|---|
| **卡片** | 正面=问题，点击翻转看答案；答案可「展开完整正文」（Markdown 渲染，支持表格/代码块） |
| **浏览模式** | 顺序 / 随机 / 只看不熟 / ⭐收藏 / 待复习 |
| **标记与复习** | 掌握 / 不熟 / 再练（连续 3 次答对自动转掌握）/ ⭐收藏 |
| **进度** | 掌握进度条、今日统计、累计次数、续看上次位置 |
| **目录** | 全部（按题号）/ 按分册 / 按项目 / ⭐收藏；支持搜索（题号/问题/答案/标签）；点击跳转到卡片 |
| **随手记** | 记录零散问题（问题+补充+分类+标签），支持**分类筛选**与**一键导出**（复制 / 分享 / 存文件，Markdown 格式） |
| **语音备忘** | 前台服务保证息屏/后台持续录音（M4A/AAC），支持分类、关联笔记、播放与导出 |
| **重置** | ⋮ 菜单：重置进度 / 清空收藏 / 全部重置（均二次确认） |

**技术选型**：Jetpack Compose · kotlinx.serialization · DataStore · [Markwon](https://github.com/noties/Markwon)（Markdown 渲染）

**环境要求**：JDK 17+ · Gradle 8.9+ · AGP 8.x · compileSdk 36 · minSdk 26（Android 8.0）

---

## 四、准备你自己的题库数据

App 需要三类数据文件（放在 `android-app/app/src/main/assets/cards/`，或手机外部目录）：

```
cards/
├── index.json        分册索引
├── 01.json ~ NN.json 各分册（含完整正文）
└── cards.json        轻量版（全部卡片，不含全文）
```

### 生成方式

`gen-cards.mjs` 会把 Markdown 题库里每道题解析成一张卡片：

- **卡片正面** = 题目标题（如 `### Q12：xxx`）
- **卡片背面** = 题目标题下方的「一句话回答」段落（兼容 `一句话总结` / `一句话开场` / 正文首段兜底）

Markdown 题目格式示例：

```markdown
### Q12：FreeRTOS 的任务调度是如何实现的？

**一句话回答**：FreeRTOS 用「优先级 + 时间片」两级调度……

（以下是展开的完整正文……）
```

运行：

```bash
# 生成轻量版 cards.json
node scripts/gen-cards.mjs

# 生成分册版（含完整正文）
node scripts/gen-cards.mjs --full --split
```

> **注意**：`scripts/gen-cards.mjs` / `gen-index.mjs` / `gen-merged.mjs` 顶部的 `BOOKS` 数组里
> 定义了分册文件名（与 Markdown 文件名对应）。**使用前请改成你自己的文件名。**

---

## 五、数据通路（两条）

App 按以下**优先级**加载数据：

```
① 外部目录：/sdcard/interview-qa/cards/     ← 手动同步的最新数据
② 内置 assets：app/src/main/assets/cards/   ← 装机即用的兜底
```

**手动同步**（无需重装 App）：

```bash
adb push cards/ /sdcard/interview-qa/cards/
# 然后在 App 里点右上角 🔄 重新加载
```

---

## 六、编译与安装

```bash
# 编译 Debug APK
cd android-app
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 安装到手机（需开启 USB 调试）
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或用 Android Studio：`File → Open` 选择 `android-app` → 等 Gradle Sync → 点 ▶ Run。

> 首次编译需下载依赖（Gradle / AGP / AndroidX），需联网。
> 若项目路径含中文，`gradle.properties` 已加 `android.overridePathCheck=true` 放开限制。

---

## 七、License

见 [LICENSE](LICENSE)。
