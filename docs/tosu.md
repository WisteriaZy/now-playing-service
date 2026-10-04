# osu!（tosu）接入方案与使用

## 已实现

- 平台 key 为 `tosu`，通用设置「更多 → 其他 → osu!（tosu）」。
- 端口字段 `tosuPort`，默认 24050；仅连接本机 `/json/v2`。
- 菜单/选歌试听、打图、回放均读取当前谱面音频信息，不限定为游戏内状态。
- 使用歌名、歌手、音频时间和总时长；优先 Unicode 元信息与 mp3Length，未知音频长度才退回谱面长度。
- 暂停采用 `game.paused` 与时间变化判断；旧接口缺该字段时，时间停滞 650ms 后判为暂停。
- 不用音量或窗口焦点判暂停。负进度按 0 显示，重试/跳转直接校准。
- 以音频路径加歌名/歌手识别曲目，不把同音频的难度切换当成换歌。

## 快速切歌保护

1. C# 每次输出单行完整快照；HTTP 请求最短间隔 200ms，1500ms 超时，失败退避至 2500ms。
2. Java 不再为 tosu 同步搜索歌曲资料，直接更新歌曲和进度。
3. 封面与歌词分别用单工作线程加载器：600ms 防抖，一个执行中的请求、一个可替换的最新请求，不积累任务队列。
4. 完成时核对请求实例，覆盖 A → B → A 场景；过期结果丢弃。已发出的同步网络请求不保证能取消，但不会阻塞状态读取。
5. 歌词来源、解析方式不变；tosu 的歌词 API 查询先返回空/已缓存歌词，不等待网络。
6. 背景按谱面相对路径从 tosu Files API 获取，拒绝路径穿越、外部地址和重定向，限制图片大小 5MiB。
7. 图片保存为当前曲目的内存数据，不共用其他播放器的 cover_base64.txt。文字/图片文件输出在资源稳定后更新。

## 本机测试版

独立目录：`test-build-tosu/`，不覆盖此前 `test-build/` 或官方安装目录。

1. 启动 osu! 与 tosu，先打开 `http://127.0.0.1:24050/json/v2`，应得到 beatmap/time 等 JSON。
   HTTP 500 且提示 osu not ready/running 表示 tosu 已启动，但尚未识别可读取的 osu!。
2. 退出占用 9863 的官方版/旧测试版。
3. 双击 `test-build-tosu/Start-Tosu-Test.cmd`。
4. 打开 `http://127.0.0.1:9863/settings/general`，展开「更多」，确认 osu!（tosu）已选中；如有自定义端口，修改后点保存图标。
5. 在选歌界面快速滚动后停住：歌名/进度应及时跟随，封面和歌词稍后补齐；不能依次追赶之前经过的歌曲。
6. 测试试听暂停、切换难度、重试、DT/HT、关闭 osu!/tosu 后重开。
7. OBS 浏览器源仍使用 `/widget`，歌词使用 `/lyric`；样式设置在 `/settings/widget`。

本机测试包继续使用此前补齐的官方旧版 OBS 资源，这些资源不提交到仓库。干净源码构建仍需要自行补齐。
原有 SPlayer 设置保留在旧目录，新目录复制设置后默认选择 tosu；不会修改官方配置。

## 测试与限制

已准备并运行：26 项 C# 检测器检查；39 项 Java 合并/过期结果/非阻塞、图片类型及封面地址检查；19 项设置 API 检查；8 项 UI 回归（含 SPlayer）。
另有完整 Java 服务 + 实际检测 EXE + 本机模拟 tosu 的端到端测试，覆盖慢背景、快速选歌、暂停、跳转、断连恢复。

真实 osu! stable/lazer 的所有菜单状态还需实机验证。数据依赖 tosu 版本与识别状态；多实例跟随 tosu 当前聚焦客户端。
仅支持 PNG/JPEG/GIF/WebP 背景；不获取皮肤、PP 或按键数据。没有新增歌词来源或修改歌词格式。
播放器公开秒级字段维持不变，WebSocket/进度接口使用 tosu 毫秒锚点；不做倍速平滑插值，DT/HT 依靠定期锚点校正。

## 构建与源码

Java 使用 JDK 11；检测器使用 .NET 8。前端源码位于相邻 now-playing-frontend 分支。
`patches/frontend-tosu.patch` 是相对于已有 SPlayer 前端实现的增量补丁；从原作者前端基线重建时先应用 frontend-splayer-next.patch，再应用此补丁。

```powershell
mvn package dependency:build-classpath -Dmdep.outputFile=target/verification-classpath.txt
python verification/run_tosu_checks.py --java-home "C:\Program Files\Java\jdk-11.0.13"
python verification/run_settings_api.py --java-home "C:\Program Files\Java\jdk-11.0.13"
dotnet run --project verification/Tosu/Tosu.Checks.csproj -c Release
python verification/tosu_end_to_end.py --java-home "C:\Program Files\Java\jdk-11.0.13"
```

端到端检查会启动独立端口和独立配置的测试进程，并自动关闭；不启动/控制真实 osu!。

## lazer 无扩展名封面兼容

lazer 背景通常是 `d/da/<hash>` 这类没有扩展名的内容寻址文件，tosu 返回的 Content-Type 可能为空或 application/octet-stream。
封面加载现已按 PNG/JPEG/GIF/WebP 文件签名识别类型，不再依赖响应头；大小上限、路径限制和过期结果隔离保持不变。
已使用在线 lazer 验证真实背景经检测器和 Java 服务进入封面接口；`/api/query/track` 提供稳定封面地址，图片接口/转换接口返回的图片与原始文件逐字节一致。

可选的实时验证（需先启动 osu! 和 tosu；使用独立端口和配置，不控制游戏）：

```powershell
python verification/tosu_live_artwork.py --java-home "C:\Program Files\Java\jdk-11.0.13" --tosu-port 24050
```

## 旧版 OBS 卡片切歌后封面更新

旧卡片仅在歌名/歌手变化时转换一次封面，不能依赖后续封面就绪事件。现在 `track.cover` 在首次歌曲快照中就返回稳定的相对 URL：
`/api/cover/tosu/current?v=...`。转换接口或图片 GET 请求最多等待 5 秒读取当前图片，不阻塞检测器和进度查询。
旧请求也读取当前曲目，避免返回已离开曲目的封面；图片响应禁用缓存。图片就绪不再发送多余的 Track 事件。
原有文件签名识别和慢加载/过期结果隔离保留，未修改官方 JS/CSS。

本地本次修复包使用 `test-build-tosu/Start-Tosu-CoverFix.cmd`，加载 `now-playing-coverfix.jar`。
先关闭旧测试进程再启动；配置目录共用且未改动。旧 JAR 和启动脚本保留，避免覆盖运行中的程序。

提交前另已检查封面缓存的完成标记和值原子读取，避免完成瞬间被误判为空。
