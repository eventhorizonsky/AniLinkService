# artplayer-proxy-mediabunny（vendored fork）

AniLinkService 内使用的 **ArtPlayer mediabunny 播放后端**（canvas + WebAudio），
源码来自 ArtPlayer 官方 monorepo：

- 上游：https://github.com/zhw2590582/ArtPlayer/tree/master/packages/artplayer-proxy-mediabunny
- 许可证：**MIT**，版权 (c) 2017-2026 Harvey Zhao (zhw2590582)；本目录保留上游源码结构与注释。

## 为什么 vendor 而不是直接用 npm 包

npm 上的 `artplayer-proxy-mediabunny@1.2.0` 发布产物会把所用到的 **mediabunny 整体内联**
（dist 零 import），且不暴露任何“自定义解码器注册”入口。因此即使应用侧调用
`@mediabunny/ac3` 的 `registerAc3Decoder()`，也只会注册到应用自己那份 mediabunny，
插件内联副本看不到 → AC3/EAC3 音轨 `canDecode()=false` 被静默丢弃（有画无声）。

## 本地改动（fork 差异）

1. **mediabunny 外置**：不再内联，改为运行时 `import … from 'mediabunny'`，与 `ui/` 顶层
   依赖（pnpm 去重后的单一实例）共享**同一个解码器注册表**。播放前调用
   `registerAc3Decoder()`（`@mediabunny/ac3`，MPL-2.0）即可让 AC3/EAC3 走 WASM 解码。
2. **移除 m3u8/HLS 专属控制 UI**（`m3u8.js` 及其在 `index.js` 的挂载）：AniLink 只播文件直链，
   不需要 HLS 清晰度/音轨切换面板。
3. 其余改动以「外置 mediabunny / 移除 m3u8」为目标，引擎/事件桥逻辑尽量与上游 `src/` 保持一致。
   如后续本地调整过引擎逻辑，请先与上游逐文件核对差异（`git diff --no-index <上游目录> 本目录`），
   并同步更新本清单与仓库根 `THIRD_PARTY_NOTICES.md`（涉及源码级修改时需保留对应许可声明）。

## 说明与义务

- 本项目**不修改 mediabunny / @mediabunny/ac3 本体**（二者为 MPL-2.0，作为普通依赖使用，
  分发义务见仓库根目录 `THIRD_PARTY_NOTICES.md`）。
- 本目录为 MIT 代码的再分发与少量修改，保留上游 MIT 版权声明即可；仓库根
  `THIRD_PARTY_NOTICES.md` 已登记本 fork。
- 已知限制（与上游一致，供接入参考）：
  - 渲染走 canvas + WebAudio，无真实 `<video>`/MSE；ArtPlayer 控制条/进度/倍速/音量照常，
    timeupdate 默认 250ms；
  - ArtPlayer 原生字幕（`textTracks`）在代理模式下不可用 → 播放层代码在代理模式下仅启用
    libass(ASS/SSA)，SRT/VTT 会提示不支持；
  - PIP/airplay 依赖原生媒体接口，代理模式不可用；
  - 服务端需支持 HTTP Range/CORS；视频解码依赖 WebCodecs（HEVC 依平台）。
