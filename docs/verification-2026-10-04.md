# 最终构建与检查记录（2026-10-04）

## 已通过

| 检查 | 结果 |
| --- | --- |
| React / TypeScript 类型检查 | 通过 |
| Vite 生产构建 | 通过 |
| UI 交互回归 | 4 项通过 |
| GetMusicStatus / CheckMusicProcess Release 构建 | 通过 |
| 检测器离线断言 | 21 项通过 |
| 实际打包 EXE + 本机模拟 HTTP 服务 | 自定义端口、歌名/歌手、暂停、拖动、停止、恢复、断连通过 |
| Java 后端 Maven test/package（JDK 11） | 构建通过；设置接口检查另行运行，见下行 |
| 设置接口与旧配置检查 | 12 项通过：GET 默认端口、合法端口保存、边界、非法值拒绝、旧 HTTP/持久化配置兼容 |
| 产物一致性 | dist、后端静态资源、JAR 内容一致；两套 EXE/DLL 与构建输出一致 |
| 模板资源检查 | 仅缺已知的两项旧版组件设置资源 |
| Git diff 格式检查 | 前后端均通过 |

测试版 `test-build/now-playing.jar` 已刷新，原有 `Settings` 未改动。
SHA-256：`c060b573046c0799b242325c13516c68ed82d3a3c111921878f89d7e4a30dfe0`。

## 仍需明确的边界

- `/settings/widget` 所需旧版 JS/CSS 未随源码发布，本次只增加缺失说明，未恢复旧版组件设置。
- 可正常构建的播放器与设置入口是 `/player` 和 `/settings/player`。
- 用户已反馈 SPlayer 实机基础功能正常；这轮主要为构建、离线和模拟接口回归，未逐一实测其他播放器。
- 没有重新验证 Windows 开机自启、OBS、桌面插件、所有歌词源等既有功能。
- 进度仍为整数秒，不实现倍速补偿、同名同歌手不同版本的精确识别或歌词同步。
- 构建存在旧依赖/大分块等警告，没有为消除警告而升级整个项目依赖。

## 复现补充的后端检查

在项目根目录使用 JDK 11：

```powershell
mvn dependency:build-classpath -Dmdep.outputFile=target/verification-classpath.txt
python verification/run_settings_api.py --java-home "C:\Program Files\Java\jdk-11.0.13"
```

检查通过 MockMvc 直接调用控制器，不监听端口、不启动音频进程、不触碰实际用户配置。
日志位于 `target/settings-api-checks.log`。其余命令见 `docs/splayer-next.md`。
