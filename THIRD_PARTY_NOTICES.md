# THIRD-PARTY NOTICES

本文件汇总 AniLinkService 前端（`ui/`）运行时打包/分发可能涉及的主要第三方依赖及其许可义务，
重点是 **2026-09 新增的 AC3/EAC3 WASM 播放链路**相关组件。AniLinkService 自身代码按仓库 LICENSE
（Apache-2.0）发布；下列第三方组件保留各自许可证，其义务只作用于对应组件，不影响本项目自身代码许可。

本项目仅作为上述组件的**使用方**：未修改其源码，均按其官方扩展点/API 接入
（AC3/EAC3 解码经 `@mediabunny/ac3` 的 `registerAc3Decoder()` 注册；未 fork、未内联改动）。

## 需要特别标注的组件（MPL-2.0，弱 copyleft）

以下两个组件基于 **Mozilla Public License 2.0 (MPL-2.0)**。MPL-2.0 为文件级 copyleft：
- 本项目**未修改**其文件，因此只影响这些组件自身；
- 对外分发（发布构建产物、Docker 镜像、可执行包）时，需保留其版权与许可声明，并让接收者能获得所用版本的源代码（下方给出上游地址与版本即可满足，因组件未被修改）；
- 若未来对其做了源码级修改，被修改的文件需继续以 MPL-2.0 提供。

| 组件 | 版本（semver 范围） | 许可证 | 上游源代码 | 用途 |
| --- | --- | --- | --- | --- |
| `mediabunny` | ^1.55.7（与 artplayer-proxy-mediabunny 的 ^1.43.1 解析为同一实例） | MPL-2.0 | https://github.com/Vanilagy/mediabunny | MKV/MP4/TS 等容器解复用；WebCodecs/WebAudio 播放后端；AC3 之外的解码基础设施 |
| `@mediabunny/ac3` | ^1.55.7 | MPL-2.0 | https://github.com/Vanilagy/mediabunny/tree/main/packages/ac3 | AC-3 / E-AC-3 的 WASM（libavcodec）解码器/编码器注册 |

MPL-2.0 全文：https://www.mozilla.org/en-US/MPL/2.0/ （或见各包内 LICENSE 文件）

## 本功能链路中的其他组件

| 组件 | 许可证 | 上游 | 说明 |
| --- | --- | --- | --- |
| `artplayer-proxy-mediabunny`（vendored fork） | MIT | https://github.com/zhw2590582/ArtPlayer/tree/main/packages/artplayer-proxy-mediabunny | ArtPlayer 的 mediabunny 播放后端代理。因 npm 1.2.0 产物内联了私有 mediabunny 副本、无法注册 AC3 解码器，AniLink 在 `ui/src/vendor/artplayer-proxy-mediabunny/` 收入其 MIT 源码并做了少量修改（外置 mediabunny 依赖、移除 HLS UI）；差异与说明见该目录 README.md。不使用 npm 版插件 |
| `artplayer` / `artplayer-plugin-danmuku` / `artplayer-plugin-vtt-thumbnail` | MIT | https://github.com/zhw2590582/ArtPlayer | 播放器本体与弹幕/缩略图插件（既有依赖） |
| `libass-wasm` | MIT | https://github.com/weizhenye/libass-wasm | ASS/SSA 字幕渲染（既有依赖） |

## 分发时的操作建议

1. 保留 `node_modules` 中上述包自带的 LICENSE 文件（构建产物若剔除应在本声明中提供全文链接）。
2. 发布物（镜像/静态站点）中随附本文件，并保持版本与 `ui/package.json`/`pnpm-lock.yaml` 一致。
3. 上述 MPL-2.0 组件未被修改；如未来升级/打补丁，请同步更新“上游源代码/版本”列并注明改动点。

（如需严格核对各依赖最终解析版本，以 `ui/pnpm-lock.yaml` 为准。）
