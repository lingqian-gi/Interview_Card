# 面试卡片 Android App

> 卡片式面试题库浏览器（Jetpack Compose + Markwon）。
> 数据来自 `../qa/` 的 Markdown 题库，经 `../scripts/gen-cards.mjs` 编译成 JSON。

## 一、功能

| 功能 | 说明 |
|---|---|
| **卡片翻转** | 正面 = 问题；点击翻到背面 = 短答案 |
| **展开全文** | 背面可展开完整正文（Markdown 渲染，支持表格/代码块） |
| **分册筛选** | 01~06 六个分册切换 |
| **浏览模式** | 顺序 / 随机 / 只看不熟 |
| **熟 / 不熟标记** | 本地持久化（DataStore），标记后自动跳下一张 |
| **随机抽题** | 顶部 🎲 按钮 |
| **数据来源展示** | 顶部条显示"内置 / 外部目录"+ 进度统计 |

## 二、数据来源（两条通路）

App 按此**优先级**加载数据：

```
① 外部目录：/sdcard/interview-qa/cards/        ← 手动同步的最新数据
② 内置 assets：app/src/main/assets/cards/      ← 装机即用的兜底
```

### 手动同步流程（PC → 手机）

```bash
# ─── PC 侧 ───
cd d:/codebuddy_proje/简历项目复习
node scripts/gen-cards.mjs --full --split      # 重新编译卡片数据
# 把 qa/cards/ + qa/cards.json 推送到手机可访问的位置（git push / 网盘 / adb push）

# ─── 手机侧（Termux，或直接 adb push）───
# 方式 A：adb push（USB 连接时）
adb push qa/cards/ /sdcard/interview-qa/cards/

# 方式 B：Termux git
cd /sdcard/interview-qa && git pull
```

然后在 App 里点右上角 **🔄 重新加载**。

## 三、编译

### 方式 A：命令行（已配置好）

```bat
cd android-app
build.bat assembleDebug
```
产物：`app/build/outputs/apk/debug/app-debug.apk`

### 方式 B：Android Studio

1. `File → Open` → 选择 `android-app` 目录
2. 等 Gradle Sync 完成
3. 点 ▶ Run（需连接手机或启动模拟器）

## 四、安装到手机

```bash
# USB 连接 + 开启 USB 调试后
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或把 `app-debug.apk` 传到手机，用文件管理器点击安装（需允许"未知来源"）。

## 五、环境要求（本机已满足）

| 组件 | 版本 |
|---|---|
| JDK | Android Studio 自带 jbr（OpenJDK 21） |
| Gradle | 9.1.0 |
| AGP | 8.13.0 |
| compileSdk | 36（android-36） |
| minSdk | 26（Android 8.0） |

## 六、目录结构

```
android-app/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/cards/            ← ★ 卡片数据（gen-cards.mjs 生成）
│       │   ├── index.json
│       │   ├── 01.json ~ 06.json    （含完整正文）
│       │   └── cards.json           （轻量版，兜底）
│       ├── java/com/caj/interviewcards/
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── Models.kt         （数据模型）
│       │   │   ├── CardRepository.kt （外部优先 + assets 兜底）
│       │   │   └── ProgressStore.kt  （熟/不熟持久化）
│       │   └── ui/
│       │       ├── MainViewModel.kt
│       │       ├── HomeScreen.kt     （主界面）
│       │       ├── CardScreen.kt     （卡片组件）
│       │       └── MarkdownView.kt   （Markwon 封装）
│       └── res/
├── build.bat                        ← ★ 一键编译
└── local.properties                 （SDK 路径，不入 git）
```

## 七、更新题库后的完整流程

```bash
# 1. PC：编辑题库（qa/*.md）
# 2. PC：重新生成卡片数据
node scripts/gen-cards.mjs --full --split
node scripts/gen-cards.mjs                    # 同时更新轻量版
# 3. PC：把数据 +（可选）重新编译 APK
cd android-app && build.bat assembleDebug
# 4. 手机：adb install -r .../app-debug.apk  或 推数据到 /sdcard/interview-qa/cards/ 后点"重新加载"
```
