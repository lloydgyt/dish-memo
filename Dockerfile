FROM eclipse-temurin:17-jdk

# 容器默认时区为UTC，如需使用上海时间请启用以下时区设置命令
# 用不了，必须基于 alpine！
# RUN apk add tzdata && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone

WORKDIR /app

COPY target/cook-history-service-1.0.0.jar cook-history-service-1.0.0.jar

EXPOSE 8080

# 环境变量 （注释掉的配置指的是放在云托管的部署参数中，以保密）
# ENV SERVER_PORT=8080
# ENV DB_ADDRESS='localhost:3306'
# ENV DB_USERNAME=root
# ENV DB_PASSWORD='your_password'
# ENV REDIS_HOST=localhost
# ENV REDIS_PORT=6379
# ENV REDIS_DATABASE=0
# ENV BAILIAN_API_KEY='your-bailian-api-key'
# ENV BAILIAN_BASE_URL='https://dashscope.aliyuncs.com/compatible-mode/v1'
# ENV BAILIAN_MODEL='qwen3.6-flash'
# ENV SUGGESTION_IMAGE_URL_ALLOWED_HOSTS='oss.example.com,img.example.com,7072-prod-d5gdc5h99b1442a27-1424479475.tcb.qcloud.la'

ENTRYPOINT ["java", "-jar", "cook-history-service-1.0.0.jar"]