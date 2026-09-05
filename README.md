<div align="center">
  <h1>
    <img src="doc/img/README/icon.png" alt="AniLinkService icon" width="96" valign="middle" />
    <span valign="middle">AniLinkService</span>
  </h1>
</div>

基于弹弹play开放平台的本地追番服务，面向有 NAS、家用服务器或轻量云主机的用户，提供媒体库扫描、番剧匹配、弹幕播放、RSS 自动下载、追番通知和后台管理等能力。

![AniLinkService](https://socialify.git.ci/eventhorizonsky/AniLinkService/image?description=1&forks=1&issues=1&language=1&name=1&owner=1&pattern=Circuit+Board&pulls=1&stargazers=1&theme=Light)



完整文档站点：https://eventhorizonsky.github.io/ani-link-doc/

## 项目定位

AniLinkService 适合这类场景：

- 你有一台可以持续运行的 NAS 或家用服务器
- 你希望把本地番剧文件、弹幕、追番和自动下载整合到一个服务里
- 你可以使用 Docker，或具备基本的 Java / Node 本地开发环境
- 你的环境可以访问 `ghcr.io`

如果你只有一台电脑或手机，希望直接开箱即用，弹弹官方客户端通常会更合适：
https://www.dandanplay.com/



## 界面预览

<table>
  <tr>
    <td align="center" width="50%">
      <img src="doc/img/README/home.jpg" alt="首页" />
      <br />
      <sub>首页：新番时间表与常用入口</sub>
    </td>
    <td align="center" width="50%">
      <img src="doc/img/README/player.jpg" alt="播放页" />
      <br />
      <sub>播放页：视频、弹幕与字幕联动</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="doc/img/README/anime-detail.jpg" alt="番剧详情" />
      <br />
      <sub>番剧详情：海报、剧集与追番信息</sub>
    </td>
    <td align="center" width="50%">
      <img src="doc/img/README/discover.jpg" alt="发现页面" />
      <br />
      <sub>发现：新番趋势、近期热门与资料库</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="doc/img/README/my-follows.jpg" alt="我的追番" />
      <br />
      <sub>我的追番：追番进度与状态一览</sub>
    </td>
    <td align="center" width="50%">
      <img src="doc/img/README/play-history.jpg" alt="播放历史" />
      <br />
      <sub>播放历史：断点续播与多端同步</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="doc/img/README/messages.jpg" alt="消息中心" />
      <br />
      <sub>消息中心：番剧更新动态一目了然</sub>
    </td>
    <td align="center" width="50%">
      <img src="doc/img/README/download-center.jpg" alt="资源搜索和下载" />
      <br />
      <sub>后台：资源搜索、下载和管理</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="doc/img/README/admin-dashboard.jpg" alt="后台看板" />
      <br />
      <sub>后台看板：媒体库、任务与订阅概览</sub>
    </td>
    <td align="center" width="50%">
      <img src="doc/img/README/profile.jpg" alt="个人中心" />
      <br />
      <sub>个人中心：账号、设置与多端信息</sub>
    </td>
  </tr>
</table>



## 核心能力
**全自动资源流水线**  
- 配置 RSS 订阅后，全流程自动完成：资源下载 → 媒体库入库 → 番剧/剧集智能识别与匹配 → 自动抓取封面、简介等详情信息。播放时弹幕与字幕即开即有，全程无需人工干预。  
- 内置资源检索功能，点击下载即可自动完成入库与匹配。  
- 自带下载器，无需额外配置下载服务。  
- 支持媒体库目录自动扫描外挂字幕，内封与外挂字幕可快速切换

**开箱即看的播放体验**  
- 网页端直接播放，弹幕和字幕自动就位；播放进度自动记录，支持断点续播。  
- 内封/外挂字幕自动加载，并支持随时调整字幕延迟。  
- 深度整合弹弹 play 官方生态，一键唤起客户端播放。  
- 提供多种主题，按需自由切换。

<img src="doc/img/README/subtitle-management.png" alt="字幕管理" width="720" />

**打通 Bangumi**  
- 详情页与播放页自动聚合番剧评分及单集吐槽。  
- 绑定 Bangumi 账号后，播放过程自动打标，追番状态实时同步。  
- 支持一键导入 Bangumi 追番清单，轻松迁移。  
- 发现页「猜你喜欢」基于你的收藏实时生成个性化推荐，帮你找到下一部想看的动画（算法参考 [czy0729/Bangumi](https://github.com/czy0729/Bangumi)）。

<img src="doc/img/README/comments.png" alt="评论区" width="720" />

**追番管理**  
- 提供新番时间表、新番趋势、近期热门、番剧资料库等多维度发现工具，助你快速定位心仪作品。  
- 内置消息中心，番剧更新动态一目了然。

**多端访问**  
- 自适应布局，移动端推荐使用 via 播放器，避免系统播放器干扰弹幕功能。  
- 播放进度自动云端同步，多端无缝衔接，一键继续观看。
- 提供MCP支持，快速接入AI

<p align="center">
  <img src="doc/img/README/mcp-example.jpg" alt="AI MCP 示例" width="300" />
</p>

**省心部署**  
- Docker 一键部署，H2 数据库零配置快速启动，亦可无缝切换至 PostgreSQL。  
- 自带管理后台，涵盖媒体库、字幕、下载任务、订阅与定时任务，轻松掌控全局。

## 快速开始

### 前置条件

启动前建议先确认：

1. 服务器已安装 Docker，并且你知道如何创建和管理容器
2. 机器可以拉取 `ghcr.io/eventhorizonsky/anilinkserver:latest`
3. 你已拿到弹弹开放平台的 `AppId` 和 `AppSecret`

开放平台申请指引：
https://doc.dandanplay.com/open/#_3-申请-appid-和-appsecret

考虑到官方开放平台更倾向于为开发者颁发凭证，我们也为用户提供了中转服务：[下游代理服务介绍](https://eventhorizonsky.github.io/ani-link-doc/ani-link-proxy.html)。

### 1. 拉取镜像

```bash
docker pull ghcr.io/eventhorizonsky/anilinkserver:latest
```

### 2. 使用 H2 快速启动

先进入你希望持久化数据和媒体文件的目录：

```bash
mkdir -p ./anilink/data ./anilink/media
```

然后启动容器：

```bash
docker run -d \
  --name anilink \
  -p 8081:8081 \
  -e DB_PROFILE=h2 \
  -v ./anilink/data:/data \
  -v ./anilink/media:/media/anime \
  --restart unless-stopped \
  ghcr.io/eventhorizonsky/anilinkserver:latest
```

说明：

- `./anilink/data` 用于持久化 H2 数据库、缓存和临时文件
- `./anilink/media` 是示例媒体目录，会挂载到容器内的 `/media/anime`
- `-p 8081:8081` 左侧是宿主机端口，可改；右侧容器端口固定为 `8081`

### 3. 完成初始化

容器启动后，浏览器访问：

```text
http://<你的主机IP>:8081
```

按照安装向导依次完成：

1. 站点标题、描述和管理员账号配置
2. 弹弹开放平台 `AppId` / `AppSecret` 配置
3. 媒体库路径配置

如果你使用的是上面的 Docker 命令，媒体库路径应填写：

```text
/media/anime
```

初始化完成后，首页应能正常显示新番时间表；如果数据异常，优先检查 `AppId` 和 `AppSecret` 是否填写正确。

### 4. 配置自动下载

如果你的媒体库中已经有视频文件，这一步可以跳过。

进入后台管理后，可以在 RSS 订阅中添加订阅地址，例如：

```text
https://acg.rip/team/173.xml
```

支持的典型流程：

- 新增 RSS 订阅
- 按需配置 HTTP 代理
- 点击“立即检查”触发抓取
- 自动下载到媒体库并完成匹配

回到首页的“发现”页后，应能看到刚刚匹配成功的番剧。

## 部署说明

### PostgreSQL

默认数据库为 H2。如需切换 PostgreSQL，可设置：

```bash
-e DB_PROFILE=pgsql
-e DB_HOST=127.0.0.1
-e DB_PORT=5432
-e DB_NAME=anilink
-e DB_USER=postgres
-e DB_PASS=yourpassword
```

示例：

```bash
docker run -d \
  --name anilink \
  -p 8081:8081 \
  -e DB_PROFILE=pgsql \
  -e DB_HOST=192.168.1.100 \
  -e DB_PORT=5432 \
  -e DB_NAME=anilink \
  -e DB_USER=postgres \
  -e DB_PASS=yourpassword \
  -v ./anilink/media:/media/anime \
  --restart unless-stopped \
  ghcr.io/eventhorizonsky/anilinkserver:latest
```

### 自建镜像

如果你希望从源码构建镜像：

```bash
docker build -t anilink-service .
```

## 版本发布

项目使用 GitHub Actions + [release-please](https://github.com/googleapis/release-please) 自动化版本发布，commit 需遵循 [Conventional Commits](https://www.conventionalcommits.org/)（如 `feat:`、`fix:`、`feat!:`）。

- **`dev` 分支**为开发分支，每次 push 自动构建 `ghcr.io/<owner>/anilinkserver:dev-latest` 与 sha 标签镜像；
- **`master` 分支**通过 `dev -> master` PR 合入（建议用 **Squash and merge**，避免 merge commit 的 body 被 release-please 当成重复的 conventional commit）；release-please 会基于 PR 中合并的 Conventional Commits 自动分析，并打开「版本发布 PR」（同时更新 `pom.xml` 版本与 `CHANGELOG.md`）；
- 合并该「版本发布 PR」后，release-please 自动创建 Git tag（`vX.Y.Z`）与 GitHub Release（body 为 commit 分析生成的 changelog），随后 CI 构建 `latest` 与版本号镜像；
- 镜像内嵌 `/CHANGELOG.md`（完整发布说明），OCI label `org.opencontainers.image.description` 为简短项目描述 + 版本号。

当前基线版本为 `1.0.0`，`pom.xml` 中版本号由 release-please 自动维护，请勿手动修改。

## 本地开发

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- pnpm
- `ffprobe` 可执行文件

### 启动后端

```bash
cd api
mvn spring-boot:run
```

默认使用 H2。如需 PostgreSQL：

```bash
cd api
DB_PROFILE=pgsql mvn spring-boot:run
```

### 启动前端

```bash
cd ui
pnpm install
pnpm dev
```

### 目录结构

```text
api/   Spring Boot 后端
ui/    Vue 3 + Vite 前端
data/  默认数据目录
```

## 文档

- 完整文档站点：https://eventhorizonsky.github.io/ani-link-doc/
- 弹弹开放平台文档：https://doc.dandanplay.com/open/
- Swagger UI：服务启动后访问 `http://localhost:8081/swagger-ui/index.html`

README 只保留项目概览和常用启动方式；更详细的部署、配置和界面引导建议查看完整文档站点。

## 故障排查

- 无法访问页面：检查 `-p 8081:8081` 映射和服务器防火墙
- 扫描不到视频：确认媒体目录已正确挂载，且安装向导中填写的是容器内路径
- 启动失败：先查看容器日志 `docker logs anilink`
- 新番时间表或基础功能异常：优先检查弹弹开放平台 `AppId` / `AppSecret`

## 技术栈

- 后端：Spring Boot 3.4.x、Spring Data JPA、Liquibase、Sa-Token、SpringDoc OpenAPI
- 数据库：H2 / PostgreSQL
- 前端：Vue 3、Vite、Vuetify、Vue Router、Axios
- 播放器：Artplayer、artplayer-plugin-danmuku
- 媒体分析：FFmpeg / ffprobe
- 下载组件：jlibtorrent

## 联系我们

[QQ群组](doc/img/README/qrcode.jpg)

## 致谢

- Sa-Token: https://sa-token.cc/
- FFmpeg: https://ffmpeg.org/
- dandanplay 开放平台: https://doc.dandanplay.com/open/
- Artplayer: https://artplayer.org/
- artplayer-plugin-danmuku: https://github.com/zhw2590582/ArtPlayer/tree/master/packages/artplayer-plugin-danmuku
- jlibtorrent: https://github.com/frostwire/frostwire-jlibtorrent
- bangumi: https://bangumi.tv/dev
- czy0729/Bangumi（「猜你喜欢」推荐算法参考）: https://github.com/czy0729/Bangumi
