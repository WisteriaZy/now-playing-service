# tosu 提交前检查

基线：已提交的 SPlayer-Next 适配 `b005875`；当前分支：`feat/tosu`。

## 已通过

- tosu 检测器：26 项；SPlayer 检测器回归：21 项。
- Java tosu 合并、防阻塞、封面类型、稳定地址、原子缓存读取：39 项。
- 设置接口和旧配置兼容：19 项。
- 前端交互回归：8 项；TypeScript 类型检查和 Vite 构建通过。
- 实际检测 EXE + Java 服务端到端：一次性旧版封面转换、过期封面、快速选歌、暂停、跳转、断连恢复通过。
- 前端构建与 JAR 内静态资源一致；前端增量补丁已检查可反向应用。
- 用户确认 lazer 播放、切歌、倍速和 OBS 封面正常。自动检查不是所有 osu! 客户端/皮肤的完整实机认证。

## 提交范围

包括 tosu 检测/后端/UI 适配、构建后的前端与检测程序、测试、使用说明和前端增量补丁。
不包括官方旧版组件 JS/CSS、本地测试包、用户配置、日志、运行时下载数据。

官方资源仍被 Git 忽略；干净源码构建不会自动包含它们。没有增加 GitHub Actions 或发布 Release。
当前前端补丁依赖 `frontend-splayer-next.patch`，应单独说明给上游维护者。

## 本地审查构建

为了不覆盖正在运行的测试版，最终审查构建保存为
`test-build-tosu/now-playing-precommit.jar`，可用 `Start-Tosu-Precommit.cmd` 启动。
启动前退出其他 Now Playing；原配置目录保持不变。

SHA-256：`02a6a44a0f4da87f496daf31aae815e30ea29444189cf20c553bc20274af5944`。
