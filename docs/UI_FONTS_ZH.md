# MMC 界面字体

MMC 自有界面统一使用 TrueType 平滑字体，包括标题、按钮、输入框、列表与悬停提示。字体的测量与绘制使用同一路径，避免按钮按旧字宽布局、显示时改用新字宽。只在 MMC 自有 Screen 中重定向默认字体；聊天和其他界面沿用其原有字体。文本明确指定的其他字体不被替换。

字体为 Noto Sans SC 的 Regular（400 字重）BMP 子集，内名为 **MMC UI Sans**，共覆盖 30,445 个 BMP 码位；未覆盖的文字和符号回退到当前资源包的 `minecraft:default`。使用 SIL Open Font License 1.1，字体的版权信息保留，完整许可证在 `assets/modularmachinery/font/ofl.txt`。它不是 GPL 字体，单独遵循 OFL；见根目录第三方声明。

资源包可替换以下文件：

- `assets/modularmachinery/font/ui.json`：一般界面字号。
- `assets/modularmachinery/font/ui_compact.json`：ES 右侧三行信息，按源版材质的 7 像素横线间距绘制。
- `assets/modularmachinery/font/mmc_ui_sans.ttf`：实际字体文件。

两个字体使用 3 倍超采样，直接以字体自身字号绘制，不再把位图文字用 Pose 缩小。紧凑字体只用于短信息栏；完整数值仍可悬停查看。两个 JSON 都可通过资源包改成仅引用 `minecraft:default`，恢复当前资源包的默认字形。

## 字体来源与再生成

原始文件：[Google Fonts / Noto Sans SC](https://github.com/google/fonts/tree/main/ofl/notosanssc)，`NotoSansSC[wght].ttf`。上游 SHA-256 为 `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。上游 OFL 文件随字体提供，未使用系统商业字体。

开发工具 `tools/prepare_ui_font.py` 使用 `fonttools==4.65.0` 固定字重、保留 BMP 字形并重命名。它还将 hmtx 的左侧承接量置零，保留字形轮廓与字符前进宽度，避免 Minecraft 1.20.1 的 TTF 提供器把字形左边距叠加两次而导致全角括号覆盖字母。它校验输入哈希，不自动下载或安装软件。准备该版本 FontTools 和原始文件后运行：

```shell
python tools/prepare_ui_font.py --source path/to/NotoSansSC-VF.ttf
```

正常 Gradle 构建使用仓库内已生成的 TTF，不依赖 Python、FontTools 或联网。字体更换后需要资源重载，并检查目标窗口下的文字与控件布局。
