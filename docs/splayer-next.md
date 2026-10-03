# SPlayer-Next 基础适配与测试

## 直接使用本次测试版

测试版目录：`test-build/`（独立配置，不覆盖已安装的 Now Playing）。

1. 退出原来的 Now Playing，包括托盘程序，避免占用 `9863` 端口。
2. 在 SPlayer-Next 的「设置 → 外部 API」中开启服务，默认端口 `14558`。
   不需要开启 WebSocket 或局域网访问。
3. 双击 `test-build/Start-SPlayer-Test.cmd`，保持控制台窗口开启。
   此测试目录不包含 Java；当前电脑已有 Java，其他电脑需安装 Java 11 或更新版本。
4. 浏览器打开 <http://127.0.0.1:9863>。
5. 通用设置中展开「更多」，在「其他」分类选择 SPlayer-Next。测试版初始已选中。
   收起「更多」会隐藏该分类和端口配置，并在按钮旁显示当前平台。
   若播放器使用自定义端口，在 API 端口框输入后点击右侧保存图标（或按 Enter）。
   连接说明位于端口标题旁的问号 tooltip。
6. 在 SPlayer 中播放歌曲，打开 Now Playing 的歌曲组件或播放器页查看结果。
7. 测试结束，在控制台按 Ctrl+C 退出测试版，再启动原来的 Now Playing。

## 手动验收

- 播放与暂停：歌名/歌手正确，暂停后进度停止，恢复后继续。
- 拖动进度：向前、向后跳转，Now Playing 应在约 1 秒内跟随（在线查询期间可能延迟）。
- 切歌：标题和歌手更新，总时长由播放器提供，不依赖搜索结果。
- 静音：仍显示播放，不因音量为零而暂停。
- 关闭播放器或关闭外部 API：信息清空，不继续显示旧歌。
- 重新打开 API 并播放：自动恢复；连续失败后的重试间隔最长 2.5 秒，另有请求超时。
- 修改端口：同步修改两边端口，保存后恢复；旧端口应无法连接。
- 非法端口：空值、0、65536、小数应无法保存，后端拒绝越界或 null。
- 退出后重新启动测试版：端口和平台选择保留。
- 歌词：保持 Now Playing 原有获取方式，不保证与 SPlayer 使用的歌词版本一致。

## 组件设置白屏与播放器入口

`/settings/widget` 是旧版歌曲组件设置页，不是播放器页面。它依赖的
`/assets/index-4783303a.js` 和 `/assets/index-ef1a3273.css` 被源码仓库明确忽略，
因此源码构建的测试版缺少它们。本次增加了资源加载失败说明，不会继续显示纯白页，
但并未重写或恢复旧版组件设置功能；需要完整发行版提供这两个资源。

- 播放器：<http://127.0.0.1:9863/player>
- 播放器样式设置：<http://127.0.0.1:9863/settings/player>

## 排查连接

在 PowerShell 中执行（端口按实际修改）：

```powershell
Invoke-RestMethod http://127.0.0.1:14558/api/now-playing | ConvertTo-Json -Depth 6
```

应返回带 `track`、`state`、`position` 等字段的 JSON。连接拒绝说明 API 未开启、
端口错误或播放器未运行；404 则需要检查 SPlayer 版本是否支持该接口。
未播放歌曲时 `track` 可为空，并不代表端口配置错误。
出错时请保留启动控制台里的日志。

## 实现边界

- 平台 key：`splayer-next`，配置字段：`splayerNextPort`，默认 14558。
- 仅请求 `GET /api/now-playing`；不读取歌词、不控制播放器、不使用 WebSocket。
- 使用 API 播放状态，不依赖 SMTC、音频设备或音量。
- 请求最短间隔 250ms，超时 1500ms，失败退避，断连输出 None。
- 沿用整数秒进度协议，不实现播放速度补偿或毫秒级同步。
- 封面仅支持 HTTP/HTTPS 图片；私有协议、空封面或下载失败使用现有默认封面。
- 仍按歌名/歌手判断切歌，同名同歌手的不同版本不是本次完整处理的范围。
- 选择 SPlayer 为备选平台时也可配置端口；进程识别名称为 `SPlayer-Next`。

## 源码与构建

前端源码在相邻的 `../now-playing-frontend` 仓库中。本次修改基于
`Widdit/now-playing-frontend@23fc42aa4ff66fdea7e5ff7bfdfb8a7ffaa86853`。
为了让后端 fork 保存完整实现，前端源代码补丁随附于
`patches/frontend-splayer-next.patch`，可在对应前端版本上用 `git apply` 应用。
后端静态资源和首页已经由该前端源码重新构建，未手工修改压缩 JS。

- Java 构建使用 JDK 11：`mvn -DskipTests package`。
  项目现有 Lombok 1.18.20 不兼容 JDK 21 编译；运行 jar 可使用已安装的 Java。
- 前端安装：`npm ci --ignore-scripts`；类型检查：`npx tsc --noEmit`。
  构建：`npx vite build --configLoader runner`。
- C# 项目分别为 `external_programs/AudioService/GetMusicStatus` 和 `CheckMusicProcess`，
  使用 .NET 8 SDK 构建。测试版已整合本次构建的检测程序。
- `Assets/AudioService` 保留仓库原有自包含运行库，仅更新这两个程序的 exe/dll。

自动检查：

```powershell
dotnet run --project verification/SPlayerNext/SPlayerNext.Checks.csproj -c Release
python verification/SPlayerNext/smoke.py
```

前者覆盖解析、限频、失败恢复等 21 项断言；后者用本机模拟 HTTP 服务验证实际打包的
GetMusicStatus.exe（自定义端口、元数据、暂停、跳转、停止、恢复、断连），不依赖真实播放器。

## UI 回归检查

在前端仓库运行 `npm test`：使用实际 HeroUI 组件验证保存平台初始化、其他分类折叠、
SPlayer 选中状态、切换配置显隐、端口校验/保存失败保留以及 tooltip 说明。
DOM 测试中跳过退出动画，不代替真实浏览器的视觉验收。
