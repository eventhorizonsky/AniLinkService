# 运行 Spring Boot 仅需 JRE；JDK 会多占数百 MB
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
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

EXPOSE 8081

# 推荐挂载点；未 -v 时由引擎创建匿名卷，避免写满容器可写层
VOLUME ["/data", "/media/anime"]

ENTRYPOINT ["sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -jar /app.jar"]
