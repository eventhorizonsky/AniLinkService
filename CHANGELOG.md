# Changelog

## [1.2.0](https://github.com/eventhorizonsky/AniLinkService/compare/v1.1.0...v1.2.0) (2026-08-14)


### Features

* 下载任务支持停滞检测、断点续传重试与暂存文件自动清理 ([d80c74c](https://github.com/eventhorizonsky/AniLinkService/commit/d80c74ced169f5e46a60ca5aa0eafd050fec6b24))
* 优化下载任务表格标题和状态显示样式，增强移动端适配 ([c60d60c](https://github.com/eventhorizonsky/AniLinkService/commit/c60d60cbe6373de31da21c054c9905d56d83b4f5))
* 支持 Tracker 列表订阅并优化新任务附加 Tracker 逻辑 ([94fae92](https://github.com/eventhorizonsky/AniLinkService/commit/94fae92ef377fbb0dd76efd8b5af184f8d17b48b))
* 新增看板统计数据接口及相关视图对象，优化系统信息展示 ([46af8ce](https://github.com/eventhorizonsky/AniLinkService/commit/46af8ce10425df6cd2e209f7f2e2f0b171472926))
* 添加 QEMU 和 Docker Buildx 设置，支持多平台构建 ([8e4bf94](https://github.com/eventhorizonsky/AniLinkService/commit/8e4bf949da39eff243304bb9049496afb8faef2d))
* 添加任务状态绑定查看功能，优化状态判断逻辑 ([3b86ccb](https://github.com/eventhorizonsky/AniLinkService/commit/3b86ccb0a94b6f87c9cbc9ffbb0caafb2f436730))
* 添加移动端适配，优化下载任务和资源搜索界面 ([69a3b2a](https://github.com/eventhorizonsky/AniLinkService/commit/69a3b2aeff88b2f88c2dd65c0b8f4e36d481be57))
* 添加路由变化监听，滚动到页面顶部 ([3e50cbb](https://github.com/eventhorizonsky/AniLinkService/commit/3e50cbb343d20ec50f0d7f85944bcd9528d5831f))


### Bug Fixes

* 优化移动端侧边栏行为，点击非分组导航时自动收起 ([15bbaf9](https://github.com/eventhorizonsky/AniLinkService/commit/15bbaf900e07ee316e87492a9a19ee2919d95bb6))
* 修复下载限速失效问题，站点配置更新后立即生效 ([ee91022](https://github.com/eventhorizonsky/AniLinkService/commit/ee91022735d9e94bbe027987078c5bdb627721d7))
* 修复移动端发现页滚动无法自动加载的问题 ([ae94094](https://github.com/eventhorizonsky/AniLinkService/commit/ae94094be1e338793da793f9db95848921efde49))
* 更换无效的媒体库图标 ([eae61f9](https://github.com/eventhorizonsky/AniLinkService/commit/eae61f9b18a18414d983fbf9fef4915d293b7327))
* 更新 H2 数据库配置，修复数据库关闭问题 ([9f4c8f0](https://github.com/eventhorizonsky/AniLinkService/commit/9f4c8f06fdc27fa1bf8bddb27e5a90972ffd8d4c))
* 更新路由参数监听，修复详情页面的路由跳转问题 ([be97de4](https://github.com/eventhorizonsky/AniLinkService/commit/be97de4cd09303d08a0b8bd3f039bfd700826451))
* 添加搜索标题到用户管理、动漫库、字幕库和视频文件管理页面 ([e577cfb](https://github.com/eventhorizonsky/AniLinkService/commit/e577cfb14ab80d297501fa8364ffed96f17f3dd8))
* 清理终态任务的速度文本，避免展示无意义信息 ([0517861](https://github.com/eventhorizonsky/AniLinkService/commit/051786182baf19e7e0840244c8f45ff62ee0ce10))
* 移动端选中导航后收起侧边栏，优化用户体验 ([65471fd](https://github.com/eventhorizonsky/AniLinkService/commit/65471fd37023e9e99e44cd5c34e347f73377612e))
* 调整发现页响应式样式，优化移动端滚动行为 ([4d0bd7d](https://github.com/eventhorizonsky/AniLinkService/commit/4d0bd7d7d5092f9a2cb8f8d06042b1fc692472b3))
* 调整后台管理页面的移动端适配 ([ee757fd](https://github.com/eventhorizonsky/AniLinkService/commit/ee757fdc05a7107c74ef6bb8dc66a188d951d0f9))
* 隐藏仪表板滚动条以优化用户界面 ([6d84069](https://github.com/eventhorizonsky/AniLinkService/commit/6d84069902c1d12b05d7164ecaf68b1b3df33c2a))

## [1.1.0](https://github.com/eventhorizonsky/AniLinkService/compare/v1.0.0...v1.1.0) (2026-08-08)


### Features

* 新增定时任务管理后台及番剧信息补全同步任务 ([05f7df9](https://github.com/eventhorizonsky/AniLinkService/commit/05f7df97adaf276bb83264196bc62e9ec19c8522))
* 添加 DOMPurify 和 marked 库以支持 Markdown 渲染和安全处理 ([c834cdb](https://github.com/eventhorizonsky/AniLinkService/commit/c834cdb161e907b02d6f37f9e2fd52f21ff8a613))
* 重构菜单项，将系统设置相关项移至单独的菜单组 ([4e00f34](https://github.com/eventhorizonsky/AniLinkService/commit/4e00f34584e01f123aae45d828e2942ce05b82f0))


### Bug Fixes

* 为 MCP 列表类工具增加 limit 参数并截断返回，防止 LLM 上下文过长 ([5f637ad](https://github.com/eventhorizonsky/AniLinkService/commit/5f637ad5c796c1e05d011305cacac14909318b51))
* 优化平板/移动端控件约束，修复播放页控件hover失效的问题 ([1fc06a7](https://github.com/eventhorizonsky/AniLinkService/commit/1fc06a7fb497afd704698bc77045378b9057c800))
* 修复追番页面，当需要手动匹配时，搜索结果未展示animeTitle的问题 ([9904228](https://github.com/eventhorizonsky/AniLinkService/commit/9904228b3b19cfc0c9d5fe4ccbc20f44b0e9fbc0))

## 1.0.0 (2026-08-07)


### Features

* add AnimeDetail, Player, Profile, and Search views with corresponding functionality and styles ([28af119](https://github.com/eventhorizonsky/AniLinkService/commit/28af11981cbf072e56581aac3078465bdf9b41cc))
* add Bangumi Next API mirror configuration and episode comments feature ([513de8d](https://github.com/eventhorizonsky/AniLinkService/commit/513de8d050b9f0aa4fdd663b903e025bb02758f7))
* add BangumiComments component for displaying user comments with loading and empty states ([93ddc54](https://github.com/eventhorizonsky/AniLinkService/commit/93ddc54e970ddc510fc9186dc330e78565f4393f))
* add danmaku functionality and enhance episode list display ([7cefa92](https://github.com/eventhorizonsky/AniLinkService/commit/7cefa9276e04321db46ab544cfacdfb6d1889eaa))
* add QueueProgress and VideoFileManager components for media file management ([3df5ac5](https://github.com/eventhorizonsky/AniLinkService/commit/3df5ac5d671a3b4fa0d87efc0c88024098316467))
* add QueueProgress component for monitoring metadata and match progress ([8f19bb0](https://github.com/eventhorizonsky/AniLinkService/commit/8f19bb076334e41b54b50213a3ddb3543bd3a970))
* add remote access feature with role-based access control ([9e7c2f2](https://github.com/eventhorizonsky/AniLinkService/commit/9e7c2f2d566033d0572acda8404b62525606f974))
* add search functionality for Dandan anime and bind unmatched follows ([276d503](https://github.com/eventhorizonsky/AniLinkService/commit/276d5038176d034d1d6375679863f98659126d0a))
* add subtitle management features and enhancements ([ef8ffec](https://github.com/eventhorizonsky/AniLinkService/commit/ef8ffec0638392b573e43345b08556ca665aaaee))
* add user profile history and messages views ([a737dc5](https://github.com/eventhorizonsky/AniLinkService/commit/a737dc5bfcd11a9f5453b1f18724489ad90e0f91))
* add user registration and email verification features ([07c14a4](https://github.com/eventhorizonsky/AniLinkService/commit/07c14a40ef0130d2cdcc43ed198209e1396ff593))
* Enhance API response logging and global exception handling with improved error messages and client disconnect detection ([ecc5ac6](https://github.com/eventhorizonsky/AniLinkService/commit/ecc5ac69e930526703787a507edda5bed8627c90))
* Enhance media file and subtitle processing ([f2c6f96](https://github.com/eventhorizonsky/AniLinkService/commit/f2c6f96b981d5218027573eb1e45b46acc1b4cb4))
* enhance media file management with rematch functionality ([e24153e](https://github.com/eventhorizonsky/AniLinkService/commit/e24153e9106e3049ef6216d678d6e212a7493808))
* enhance UI and functionality across Home, Profile, RemoteAccess, and Search views ([2cf77d0](https://github.com/eventhorizonsky/AniLinkService/commit/2cf77d0272397dbd04fd180f27e119d38b0a7037))
* Refactor database initialization and user management ([e9355ad](https://github.com/eventhorizonsky/AniLinkService/commit/e9355adc69a1882c7f47c139d976d19ddfb1cee3))
* 优化 VTT 雪碧图生成逻辑，支持并行提取缩略图并改进临时文件管理 ([bb5f665](https://github.com/eventhorizonsky/AniLinkService/commit/bb5f6657d41eb9c2f6029c86a09eab380fab0e23))
* 优化剧集标题显示，添加通过弹弹play播放按钮 ([149680e](https://github.com/eventhorizonsky/AniLinkService/commit/149680efd0ec99b0002b6fe898b2cd060774846b))
* 优化动漫匹配逻辑，添加日志记录以便调试 ([737b2b0](https://github.com/eventhorizonsky/AniLinkService/commit/737b2b07add5f0b680caa9659dee24060a080337))
* 优化发现页样式，调整滚动行为以提升移动端用户体验 ([e9229d1](https://github.com/eventhorizonsky/AniLinkService/commit/e9229d149cd33e5ca0b25e42dcc3e65d06d1a91e))
* 优化媒体库按钮图标显示，使用v-icon组件替代icon属性 ([3d9f42c](https://github.com/eventhorizonsky/AniLinkService/commit/3d9f42c47c3dae61f16944db7310e6a5c99743c1))
* 优化搜索功能，添加弹弹番剧库搜索与清除功能，重构结果展示样式 ([cedcc73](https://github.com/eventhorizonsky/AniLinkService/commit/cedcc73d44cc3c592d2927b7578df1f6a94c8257))
* 优化播放器布局，调整移动端滚动行为以提升用户体验 ([668bce5](https://github.com/eventhorizonsky/AniLinkService/commit/668bce5639da50657887db9e69b5a45983119258))
* 优化消息按钮交互，支持移动端和PC端适配，调整样式以提升用户体验 ([16b2b0b](https://github.com/eventhorizonsky/AniLinkService/commit/16b2b0b3398088a9a987c4be805149e9f74ac227))
* 优化用户菜单，简化返回首页按钮 ([339d2f8](https://github.com/eventhorizonsky/AniLinkService/commit/339d2f8b4731445430b3d3fae32e4c714affd298))
* 优化移动端追番卡片布局，调整样式以提升用户体验 ([75cd32c](https://github.com/eventhorizonsky/AniLinkService/commit/75cd32ca29b88028552aee629c2664a9a82a7796))
* 优化追番封面处理逻辑，优先保留 Bangumi 图片，避免不必要的覆盖 ([a461086](https://github.com/eventhorizonsky/AniLinkService/commit/a46108675c9b1ce6046a9314dfd5accd50fda7f3))
* 删除不再使用的 README 图片 ([033e459](https://github.com/eventhorizonsky/AniLinkService/commit/033e459439899a7cec783419a77352de5a765725))
* 后台新增版本列表，展示 GitHub 发布记录并标记当前版本，OCI description 改为短描述并内嵌 CHANGELOG ([bfb24e9](https://github.com/eventhorizonsky/AniLinkService/commit/bfb24e9b85f8a283d73d61bc7d177c7adc15f7b9))
* 增加 AniLinkService 图标的显示尺寸，提升视觉效果 ([73bdf7a](https://github.com/eventhorizonsky/AniLinkService/commit/73bdf7ab2af6cf3b71584bf9eefa76b4496febe9))
* 增强媒体库扫描服务，添加路径规范化和并发控制，优化文件处理逻辑 ([a3ff946](https://github.com/eventhorizonsky/AniLinkService/commit/a3ff946303cb74d23548011a922d02a33da760f5))
* 引入 release-please 发布机制，dev 与 master 分支分流构建镜像 ([80ec06c](https://github.com/eventhorizonsky/AniLinkService/commit/80ec06cccf738b874c30abf06b5c00d972071469))
* 新增媒体库接口，支持获取媒体库内容和视频流，添加字幕文件处理功能 ([cafe46f](https://github.com/eventhorizonsky/AniLinkService/commit/cafe46ff17dd994b5d95b313cc87d894dddf8b74))
* 更新 artplayer 依赖至 5.4.0 版本 ([5055aa3](https://github.com/eventhorizonsky/AniLinkService/commit/5055aa3f4c27d5b8c6b2e0b8a459aaf54c79b241))
* 更新 Bangumi API 配置，替换代理主机和端口为镜像地址 ([e4952b9](https://github.com/eventhorizonsky/AniLinkService/commit/e4952b9fe98a16adda77c51cc02a2c39231dd750))
* 更新 Dandan 应用密钥配置逻辑，添加密钥配置状态字段 ([523f763](https://github.com/eventhorizonsky/AniLinkService/commit/523f763788d7e2fbc09118f3d9d1775f308c36e3))
* 更新 Dockerfile 使用 JRE，添加 .dockerignore 文件以减小构建上下文 ([6784bd7](https://github.com/eventhorizonsky/AniLinkService/commit/6784bd7d302522ef4f0056c9bc9f2bbe65e87520))
* 更新 Dockerfile 使用更小的 JRE 版本以匹配 frostwire jlibtorrent 的 glibc ([18177d8](https://github.com/eventhorizonsky/AniLinkService/commit/18177d83f5dc9fb11d4fa995f683f8a335683fa2))
* 更新 README 图片 ([19e0aa5](https://github.com/eventhorizonsky/AniLinkService/commit/19e0aa57d7d0d7bab11695e4b88e1cdf1cb5936a))
* 更新 README 图片命名 ([5de6c76](https://github.com/eventhorizonsky/AniLinkService/commit/5de6c761890550a6cc73d87957ad6d64e512b00e))
* 更新 README.md，添加关于获取 AppId 和 AppSecret 的参考链接 ([ee812a2](https://github.com/eventhorizonsky/AniLinkService/commit/ee812a210786bba448d44940ff2c510e7434ea04))
* 更新 README.md，调整标题格式并添加 bangumi 链接 ([fce02ad](https://github.com/eventhorizonsky/AniLinkService/commit/fce02ad7ed398a941bc55cc02cb72774d6b4840f))
* 更新DanmakuService以支持查询参数，优化DandanClientUtil的get方法；删除HelloWorld组件；重构ShinWeeklySchedule组件以使用网格布局；调整MainLayout的导航链接；修改Home和Search视图以简化结构；更新vite配置以更改API代理地址 ([347b9fa](https://github.com/eventhorizonsky/AniLinkService/commit/347b9fa5b4b1dcb3acd24ebc09865ab5eebd220f))
* 更新Dockerfile以支持动态指定DANDAN_IMAGE_DIR环境变量并修改ENTRYPOINT命令 ([e8529f0](https://github.com/eventhorizonsky/AniLinkService/commit/e8529f00fc0971e7077f874cc825d3442f388755))
* 更新Docker工作流，优化构建步骤和依赖管理 ([2282b29](https://github.com/eventhorizonsky/AniLinkService/commit/2282b294fd0d6254eaf426e6c03ec223df9f6d04))
* 更新Docker工作流，动态设置镜像名称 ([b57402c](https://github.com/eventhorizonsky/AniLinkService/commit/b57402cb05c51403804f7ae60d0e7cfd9b5c321a))
* 更新Docker工作流中的镜像路径为动态获取 ([8b39c06](https://github.com/eventhorizonsky/AniLinkService/commit/8b39c0634c7a19dd1bf083dfc4a5ffdc0153f5e1))
* 更新Liquibase表创建测试，调整changelog文件顺序以支持新版整合初始化脚本 ([5d04783](https://github.com/eventhorizonsky/AniLinkService/commit/5d047837538a43418528b6ee7a5506cef862a6ed))
* 更新从原始 JSON 解析动漫信息的逻辑，支持弹弹 bangumi API 响应格式 ([a54e50d](https://github.com/eventhorizonsky/AniLinkService/commit/a54e50db1184446c21a40541e2b5f4d33aef7c9b))
* 更新多个组件的样式和功能，优化图片适应性，添加日期时间格式化函数 ([c9a79f7](https://github.com/eventhorizonsky/AniLinkService/commit/c9a79f7859696ea6cbf3217fab583be9896729dc))
* 更新字幕偏移量处理，支持字幕轨道切换和持久化 ([84da3f9](https://github.com/eventhorizonsky/AniLinkService/commit/84da3f9ea7bb536eb1134ba98d49a2e967028e28))
* 更新视图组件样式，优化响应式布局和文本处理 ([daf43aa](https://github.com/eventhorizonsky/AniLinkService/commit/daf43aae465a6c3b29131c5062b9ed96c5ddb3ec))
* 更新页面标题和播放器设置，优化用户体验 ([27840f7](https://github.com/eventhorizonsky/AniLinkService/commit/27840f71fb5d180201e372f85a395638833085ba))
* 添加 Bangumi API 代理配置，支持代理主机和端口设置 ([754d50c](https://github.com/eventhorizonsky/AniLinkService/commit/754d50cd7a29ebbcaba1afccb8a8101fcd88a11c))
* 添加 BBCode 渲染功能，支持颜色、对齐和 Markdown 风格链接 ([09b4329](https://github.com/eventhorizonsky/AniLinkService/commit/09b432996db32ba5da76cfc085a45bfbd817a65d))
* 添加 Dandan API 代理接口，支持季度番剧列表、热门趋势等功能 ([a2bd5ef](https://github.com/eventhorizonsky/AniLinkService/commit/a2bd5ef6a870068461e1e635af050e236735fef2))
* 添加 Dandan API 地址配置，更新相关服务和界面 ([1c97041](https://github.com/eventhorizonsky/AniLinkService/commit/1c97041e0ed9bb5580a26e30b25dd666f96e2932))
* 添加 episodeId 字段到播放历史相关 DTO 和实体，更新相关服务和视图以支持新字段 ([aef0400](https://github.com/eventhorizonsky/AniLinkService/commit/aef0400d23b59385e46cabda740f1a8bd54541f3))
* 添加 MCP API Key 管理功能，包括生成、更新和数据库变更日志支持 ([c1d9b95](https://github.com/eventhorizonsky/AniLinkService/commit/c1d9b9523aee5674dcda3e6c81cdabbb3d62a47d))
* 添加 RSS 过滤功能，支持正向和排除过滤规则，优化预览功能 ([7e37a03](https://github.com/eventhorizonsky/AniLinkService/commit/7e37a03eb41848a2688efb40f4ab30f78930f3b4))
* 添加从原始 JSON 补建/更新 Anime 记录的功能，优化动漫信息获取逻辑 ([ca3205c](https://github.com/eventhorizonsky/AniLinkService/commit/ca3205c560c4ca14819da90a7ac6ffa412ac72ad))
* 添加加载状态的骨架屏效果，优化用户体验 ([8fcb88c](https://github.com/eventhorizonsky/AniLinkService/commit/8fcb88ce3c9057d869ce1615c1a21e628334c330))
* 添加动漫记录自动创建功能，确保媒体库中的动漫信息同步到 anime 表 ([9d678cc](https://github.com/eventhorizonsky/AniLinkService/commit/9d678ccd30f3d4368450a4f14ac784f69217aea0))
* 添加响应式布局和横向滚动功能，优化追番列表展示 ([d25599a](https://github.com/eventhorizonsky/AniLinkService/commit/d25599a140561c8f30b19e981c2a41f3a465152e))
* 添加媒体文件元数据提取后自动将文件添加到匹配队列的功能 ([075f2f6](https://github.com/eventhorizonsky/AniLinkService/commit/075f2f68298ee31ba1bfc969704aa45cfe1fcf8a))
* 添加定时任务以自动重新匹配所有媒体库，优化匹配队列管理 ([4708007](https://github.com/eventhorizonsky/AniLinkService/commit/47080072bcc3e7ce44db483edf93146ee3eb1789))
* 添加封面图字段到弹幕和播放历史记录，优化相关服务以批量填充封面图 ([64cf85e](https://github.com/eventhorizonsky/AniLinkService/commit/64cf85eab79c451e08d3997d778dda5b751516c5))
* 添加弹幕发送记录持久化，支持个人中心查看和管理后台全量筛选 ([a23c662](https://github.com/eventhorizonsky/AniLinkService/commit/a23c662027a381d6fdbfb2fa0ca13c5deb56829e))
* 添加手动重建 release 镜像的 workflow，失败时无需重新发版 ([8d108d2](https://github.com/eventhorizonsky/AniLinkService/commit/8d108d2c129419c84924a2841ef4b7e6d2048734))
* 添加按剧集标记已读功能，优化未读消息统计与展示 ([911928d](https://github.com/eventhorizonsky/AniLinkService/commit/911928ddc1771dd9801864d9c73d9bfaab6cf3e7))
* 添加播放进度缩略图功能，支持生成 VTT 文件和雪碧图，更新相关配置 ([4ae0d44](https://github.com/eventhorizonsky/AniLinkService/commit/4ae0d440945d3fc93d610499f30240f5e7eece6f))
* 添加新番原始JSON数据接口及相关缓存机制；更新前端以显示新番时间表 ([5a67f96](https://github.com/eventhorizonsky/AniLinkService/commit/5a67f9642827061e633449329bb154f6c1e2da27))
* 添加日志记录功能到多个控制器和服务，增强异常处理和缓存管理 ([f6950cb](https://github.com/eventhorizonsky/AniLinkService/commit/f6950cb93c90cfc39519b295e08068418307d3f0))
* 添加标题关键词搜索功能，优化用户追番列表查询 ([6306674](https://github.com/eventhorizonsky/AniLinkService/commit/63066742ed7b68fd8892b1fe0539ce0aaeb680ca))
* 添加检查媒体库中是否存在动漫剧集的功能，避免创建无剧集的动漫记录 ([4fa66d6](https://github.com/eventhorizonsky/AniLinkService/commit/4fa66d611d2c6c9658e7c70fc3248b3232c67342))
* 添加用户管理功能，包括用户分页查询、角色分配及更新接口 ([27904e2](https://github.com/eventhorizonsky/AniLinkService/commit/27904e2131b09fb24b636c84ea9d911cbea9b9ee))
* 添加视频缩略图生成服务，支持通过环境变量配置输出目录 ([6649b3d](https://github.com/eventhorizonsky/AniLinkService/commit/6649b3d5cdb833d358b7865acaad05d895131ef8))
* 添加移动端 B 站式 TAB 组件，优化选集面板布局 ([a8808f3](https://github.com/eventhorizonsky/AniLinkService/commit/a8808f38c5794160bd90996d604e6d6c80712eba))
* 添加移动端滑动切换功能，优化播放器箭头导航 ([ff0227b](https://github.com/eventhorizonsky/AniLinkService/commit/ff0227b554059c3e879872c3cf7612a723597043))
* 添加移动端视口支持，优化弹幕和播放器设置 ([05a8e75](https://github.com/eventhorizonsky/AniLinkService/commit/05a8e751e9353d028fab6c30e31fcfccf0132838))
* 添加缓存管理功能，包括缓存统计和清理操作 ([99ad18e](https://github.com/eventhorizonsky/AniLinkService/commit/99ad18e7ea34e4bc7ff65fc2d733e126206bed27))
* 添加联系我们部分及群二维码 ([a4dce65](https://github.com/eventhorizonsky/AniLinkService/commit/a4dce65c2bee4be6d39ed69d03bd48b55d9ebd6c))
* 添加自动匹配未绑定追番功能，优化用户体验 ([73a0d59](https://github.com/eventhorizonsky/AniLinkService/commit/73a0d59d6cbce3c8d1444b87b509160a4f7de021))
* 添加获取番剧续播信息的 API 和 UI 组件，支持播放进度的显示与恢复 ([4144115](https://github.com/eventhorizonsky/AniLinkService/commit/4144115a507930f31cfdfc3ee8b954db8c5b5d19))
* 添加追番取消同步功能，支持将本地放弃追番状态同步至 Bangumi ([1957035](https://github.com/eventhorizonsky/AniLinkService/commit/19570350e90f3d7c7f9c375b45b9b1e45fbac9a2))
* 添加重新匹配媒体库功能，并调整批量匹配服务的请求参数日志 ([f5f5769](https://github.com/eventhorizonsky/AniLinkService/commit/f5f5769f7bb01122954dbc1b8535d9c47b6bfc6d))
* 移除DANDAN_IMAGE_DIR环境变量及相关配置，简化Dockerfile和README文档 ([7a7395a](https://github.com/eventhorizonsky/AniLinkService/commit/7a7395aa5ae3fd24bebc93f58218834ccdd44bc7))
* 移除Docker工作流中的仓库条件判断 ([b0a9ea9](https://github.com/eventhorizonsky/AniLinkService/commit/b0a9ea91812c6b0f6118326bad9fe7fa4a21118e))
* 规范化关键字处理逻辑，简化查询条件 ([00472de](https://github.com/eventhorizonsky/AniLinkService/commit/00472deb4551a981bbc45df0979c034d2f62ccf2))
* 调整批量匹配文件数量限制，从50个减少到20个 ([3a75fb1](https://github.com/eventhorizonsky/AniLinkService/commit/3a75fb17890ce273d404de1344bf5783c4ee86d1))
* 调整播放进度同步逻辑，播放超过80%时即时同步至 Bangumi ([65c72f3](https://github.com/eventhorizonsky/AniLinkService/commit/65c72f3e7519a4795d08b61b2fa92664416d28ff))
* 调整移动端适配，优化播放器控件和点击事件处理 ([6616c19](https://github.com/eventhorizonsky/AniLinkService/commit/6616c196192577542d2fc261ee7bb42adc7fbe5e))
* 重构搜索组件，优化媒体库和番剧资料库功能，添加滚动加载和搜索功能 ([535afa8](https://github.com/eventhorizonsky/AniLinkService/commit/535afa882ed26833581ab572ae63fb74c00ce7df))
* 重构播放器界面，优化选集面板和样式 ([ed7b573](https://github.com/eventhorizonsky/AniLinkService/commit/ed7b573368ddc27c251c32cba098b570470299fa))
* 重构首页组件，优化追番列表和新番时间表功能，添加热门趋势展示 ([3395cc8](https://github.com/eventhorizonsky/AniLinkService/commit/3395cc8215aa60ee4d5de309916adeaaa78f150d))


### Bug Fixes

* 修正注释中的引号格式，确保一致性 ([03ba0b8](https://github.com/eventhorizonsky/AniLinkService/commit/03ba0b87b59ddbf1e3564020e1ef08ae1847b8f1))


### Documentation

* 说明 dev 合并到 master 建议使用 squash 以避免 changelog 重复 ([04e9710](https://github.com/eventhorizonsky/AniLinkService/commit/04e9710d88dd574bca25820b1b8e14ba1cc0a5d2))
