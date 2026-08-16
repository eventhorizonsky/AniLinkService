# 运行 Spring Boot 仅需 JRE（比 JDK 小）。须与 frostwire jlibtorrent 预编译 .so 的 glibc 匹配：
# `17-jdk` 无后缀时多为 Ubuntu noble；勿用 jammy，否则 JNI 会 LinkageError（Failed to load jlibtorrent）
FROM eclipse-temurin:17-jre-noble

# 只装 ffmpeg + vainfo（诊断用）。Intel iHD / AMD radeonsi 等 libva 驱动不在镜像里写死，
# 由 docker-entrypoint.sh 依据宿主透传的 /dev/dri 在运行时自动安装；
# NVIDIA 场景宿主走 nvidia runtime（--gpus all），镜像无需装 NVIDIA 驱动。
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    vainfo \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 与 README 推荐一致：-v <host>/data:/data、-v <host>/media:/media/anime
# H2 与默认 media.data.root-dir 使用相对路径 ./data/...，工作目录必须为 /
WORKDIR /

# 默认 H2；切 PostgreSQL 时覆盖为 pgsql 并设置 DB_*（见 README）
ENV DB_PROFILE=h2
# 字幕/缩略图/下载暂存等落在持久化卷 /data 下（可用 -e MEDIA_DATA_DIR=... 覆盖）
ENV MEDIA_DATA_DIR=/data/media-data
ENV LANG=C.UTF-8
ENV LANGUAGE=C.UTF-8
ENV LC_ALL=C.UTF-8

ADD api/target/ani-link-service.jar /app.jar

# 按宿主硬件自动准备 libva 驱动的入口脚本
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# 发布说明（release-please 生成的 changelog；dev 构建为占位内容）
COPY CHANGELOG.md /CHANGELOG.md

EXPOSE 8081

# 推荐挂载点；未 -v 时由引擎创建匿名卷，避免写满容器可写层
VOLUME ["/data", "/media/anime"]

ENTRYPOINT ["/docker-entrypoint.sh"]
