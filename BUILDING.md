# 从源码构建

使用 JDK 17。仓库自带 Gradle 8.8 Wrapper，无需单独安装 Gradle。首次构建需要联网下载 Gradle、Forge、映射及 Maven 依赖。

## 准备编译依赖

运行时的可选联动在编译时仍需要 API。大部分依赖由 Gradle 下载；Astral Sorcery 社区版需要先从固定提交构建，把 `astralsorcery-1.20.1-1.0.0.0.jar` 放入 `libs/optional/`。固定提交、源码链接和构建方法见 [依赖说明](libs/optional/README.md)。本源码包不附带外部模组二进制。

Extra Utilities Rebirth 仅在启用 `-PwithRainbowIntegration` 运行开发游戏时需要，不是普通编译的必要依赖。不要用 1.12.2 或 NeoForge 版本替代这些依赖。

## 构建发行包

PowerShell：

```powershell
.\gradlew.bat build jarJar reobfJarJar
```

Linux / macOS：

```bash
chmod +x gradlew
./gradlew build jarJar reobfJarJar
```

默认 Forge 47.3.0。要使用已验证的全联动环境 Forge 47.4.23：

```powershell
.\gradlew.bat build jarJar reobfJarJar '-Pforge_version=47.4.23'
```

发行文件为 `build/libs/modularmachinerycontinuum-1.0.0-1.20.1-all.jar`，通过 Forge Jar-in-Jar 内嵌 GeckoLib 4.8.4。分发时选用此文件；普通无 `-all` 的 JAR 不含完整内嵌依赖。外部联动不会被一起打包。

依赖下载并缓存完整后可追加 `--offline`。本地依赖来源固定，但上游构建时间、ZIP 元数据等可能令重建文件哈希不同，不要求逐字节一致。

开发回归及可选运行开关见 [VERIFY.md](VERIFY.md)。公开源码包含模组全部 Java 源码、资源、构建脚本和回归源码；内部开发过程记录不影响构建。
