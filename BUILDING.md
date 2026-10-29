# 从源码构建

使用 JDK 17、Python 3 和自带 Gradle 8.8 Wrapper。首次构建需要网络下载 Forge、映射和 Maven 依赖。源码包不携带第三方模组二进制或个人缓存。

## 编译依赖

即使不在游戏中安装可选联动，编译仍需这些 API：

1. 按[固定依赖说明](libs/optional/README.md)从社区 Astral Sorcery 固定提交构建 `astralsorcery-1.20.1-1.0.0.0.jar`，放入 `libs/optional/`。在上游目录执行 `gradlew.bat jar reobfJar`，使用 Java 17；不要使用 1.12.2 JAR。
2. 在 MMC 项目目录运行：

```powershell
python tools/prepare_gtceu.py
python tools/prepare_recipe_integrations.py
```

脚本从官方源下载固定版本并校验哈希，外部模组不嵌入 MMC。其他 Maven API 由 Gradle 解析。Extra Utilities Rebirth 仅在开启其开发运行开关时需要，不是普通编译依赖。

## 发布 JAR

```powershell
.\gradlew.bat build jarJar reobfJarJar '-Pforge_version=47.4.23'
```

Linux／macOS：

```bash
chmod +x gradlew
./gradlew build jarJar reobfJarJar -Pforge_version=47.4.23
```

使用 `build/libs/modularmachinerycontinuum-1.4.0-1.20.1-all.jar`，该文件经过 Forge 重映射并内嵌 GeckoLib 4.8.4。不要将开发类文件或无 `-all` JAR 当作完整发布文件。依赖缓存完整后可以追加 `--offline`。ZIP 时间戳与上游元数据可能导致重建哈希不同。

默认 Forge 版本由 `gradle.properties` 指定，上述命令显式使用 47.4.23。日常开发可以运行 `runClient`／`runServer`，可选联动开关及测试边界见 [TESTING](TESTING.md)。
