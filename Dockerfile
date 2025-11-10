FROM openjdk:17-ea
#创建响应文件夹
RUN mkdir -p /app/config
# 设置环境变量，用于用户指定媒体目录和配置目录
ENV CONFIG_DIR /app/config
ADD api/target/ani-link-service.jar app.jar
ENV LANG=C.UTF-8
ENV LANGUAGE C.UTF-8
ENV LC_ALL=C.UTF-8
EXPOSE 8081
#运行程序主体
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]

