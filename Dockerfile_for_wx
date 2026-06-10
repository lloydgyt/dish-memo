# 二开推荐阅读[如何提高项目构建效率](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/scene/build/speed.html)
# 选择构建用基础镜像。如需更换，请到[dockerhub官方仓库](https://hub.docker.com/_/java?tab=tags)自行选择后替换。
FROM maven:3.9.9-eclipse-temurin-17-alpine as build

# 指定构建过程中的工作目录
WORKDIR /app

# 将src目录下所有文件，拷贝到工作目录中src目录下（.gitignore/.dockerignore中文件除外）
COPY src /app/src

# 将pom.xml文件，拷贝到工作目录下
COPY settings.xml pom.xml /app/

# 执行代码编译命令
# 自定义settings.xml, 选用国内镜像源以提高下载速度
RUN mvn -s /app/settings.xml -f /app/pom.xml clean package

# 选择运行时基础镜像
FROM alpine:3.19

# 安装依赖包，如需其他依赖包，请到alpine依赖包管理(https://pkgs.alpinelinux.org/packages?branch=v3.19)查找。
# 选用国内镜像源以提高下载速度
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.tencent.com/g' /etc/apk/repositories \
    && apk add --update --no-cache openjdk17-jre-headless \
    && rm -f /var/cache/apk/*

# 容器默认时区为UTC，如需使用上海时间请启用以下时区设置命令
# RUN apk add tzdata && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone

# 使用 HTTPS 协议访问容器云调用证书安装
RUN apk add ca-certificates

# 指定运行时的工作目录
WORKDIR /app

# 将构建产物jar包拷贝到运行时目录中
COPY --from=build /app/target/*.jar .

# 暴露端口
# 此处端口必须与「服务设置」-「流水线」以及「手动上传代码包」部署时填写的端口一致，否则会部署失败。
EXPOSE 8080

# 环境变量 （注释掉的配置指的是放在云托管的部署参数中，以保密）
ENV SERVER_PORT=8080
# ENV DB_ADDRESS='localhost:3306'
# ENV DB_USERNAME=root
# ENV DB_PASSWORD='your_password'
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379
ENV REDIS_DATABASE=0
# ENV BAILIAN_API_KEY='your-bailian-api-key'
ENV BAILIAN_BASE_URL='https://dashscope.aliyuncs.com/compatible-mode/v1'
ENV BAILIAN_MODEL='qwen3.6-flash'
ENV SUGGESTION_IMAGE_URL_ALLOWED_HOSTS='oss.example.com,img.example.com,7072-prod-d5gdc5h99b1442a27-1424479475.tcb.qcloud.la'

# 执行启动命令.
# 写多行独立的CMD命令是错误写法！只有最后一行CMD命令会被执行，之前的都会被忽略，导致业务报错。
# 请参考[Docker官方文档之CMD命令](https://docs.docker.com/engine/reference/builder/#cmd)
CMD ["java", "-jar", "/app/cook-history-service-1.0.0.jar"]
