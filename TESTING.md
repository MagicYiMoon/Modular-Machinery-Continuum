# 开发测试方法

使用 Java 17，先按 [BUILDING.md](BUILDING.md) 准备编译依赖。本文件只说明方法，不包含测试日志、截图或个人运行记录。

## 普通构建

```powershell
.\gradlew.bat build jarJar reobfJarJar '-Pforge_version=47.4.23'
```

正式构建不要启用专项测试开关。可选测试源码由 build.gradle 按属性加入；生产 JAR 排除专用夹具。

## 隔离服务端

自测会放置、破坏方块并修改测试定义，只能在新建的可丢弃目录运行。接受 Minecraft EULA，设置仅监听本机的独立端口，准备默认机器和配方。完整套件的 common 配置前提为 `[factory-system] enable-factory-controller-bydefault=true`。

```powershell
.\gradlew.bat runServer '-PselfTest' '-PselfTestRunDir=run-selftest' '-PverificationLowMemory' '-PverificationHeap=4G' '-Pforge_version=47.4.23'
```

另用不同新目录加 `-PwithIntegrations` 检查联动共存。观察具体断言与最终 `[selftest] OVERALL: PASS`；Gradle 正常退出不代表断言全部通过。

实际 ME 下单、跨进程恢复、客户端界面及资源包组合需要相应专项，不能用普通启动代替。运行属性及夹具源码见 build.gradle。测试后恢复普通构建参数。

## 2.0.3 仓室、升级总线与拆卸专项

使用独立的测试目录，不要使用玩家存档。联动组需准备可选依赖。

```powershell
.\gradlew.bat runServer '-PcomponentFeedbackTest' '-PselfTestRunDir=run-component-feedback' '-PverificationLowMemory' '-Pforge_version=47.4.23'
.\gradlew.bat runServer '-PcomponentFeedbackTest' '-PwirelessAssemblyTest' '-PwithIntegrations' '-PselfTestRunDir=run-component-feedback-ae' '-PverificationLowMemory' '-Pforge_version=47.4.23'
```

两组均须出现 `[component-feedback] OVERALL: PASS`，联动组还须出现 `[wireless-assembly] OVERALL: PASS`。实际覆盖见 [2.0.3 测试记录](TEST-RESULTS-2.0.3.md)。

提交问题时提供版本、复现步骤和必要错误信息，先移除账户、本机路径及其他个人信息。测试存档、截图、账户数据和原始日志不提交到源码仓库。
