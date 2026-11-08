# paas-core —— linux/arm64 运行镜像
#
# 设计原则：镜像只装「运行时 + 已构建好的 jar」，不在镜像里跑 Maven。
#   · 气隙环境里没有 Maven 仓库，重建镜像要能脱网完成；
#   · jar 由打包机 `mvn -o package` 产出，与镜像解耦 ⇒ 同一镜像脚本可用于任何版本。
#
# 基础镜像选择：eclipse-temurin:21-jre-alpine（多架构 tag，arm64 原生）
#   · 与 ADR-0002（JDK21）一致；alpine + musl，镜像小；
#   · 与厂商的 corretto:8-alpine 同一个家族思路（JRE 而非 JDK），只是版本对齐自研栈。
#
# 与厂商 gaop-be 镜像的差异（均为有意为之）：
#   1. JDK8 → JDK21（自研代码要求）；堆用 MaxRAMPercentage 随 limit 自适应，
#      不写死 -Xmx（厂商写死 320m，节点内存一变就得改清单）。
#   2. 非 root 运行（uid 10001）。厂商镜像以 root 跑，本仓主动收紧（deviations B 类）。
#   3. 不装 openssh-server、不复制任何主机密钥（与厂商 arm64 重建版一致）。
#   4. 时区数据显式安装，TZ=Asia/Shanghai 才能生效（alpine 默认无 tzdata）。
FROM eclipse-temurin:21-jre-alpine

ARG APP_VERSION=0.1.0
ARG VCS_REF=unknown

LABEL org.opencontainers.image.title="paas-core" \
      org.opencontainers.image.description="MSOP 自研后端（厂商 gaop-be 的可替换实现）" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.base.name="eclipse-temurin:21-jre-alpine"

# curl：健康检查与运维排查；tzdata：时区。
# 目录：/opt/gaop/upload/client 与 ~/gaop-program 是 application.yml 的落盘默认值，
# 提前建好并 chown，避免非 root 运行时首次写入权限不足。
RUN apk add --no-cache curl tzdata \
 && addgroup -S -g 10001 paas \
 && adduser -S -u 10001 -G paas -h /home/paas paas \
 && mkdir -p /app /opt/gaop/upload/client /home/paas/gaop-program \
 && chown -R paas:paas /app /opt/gaop /home/paas

WORKDIR /app
# 构建上下文里只有这一个 jar（由 build-image.sh 组装），避免把 target/ 全量送进 daemon
COPY --chown=paas:paas paas-core-app.jar /app/app.jar

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=k8s \
    SERVER_PORT=8080 \
    HEALTHCHECK_PATH=/actuator/health \
    # MaxRAMPercentage 依赖 cgroup limit，K8s 里必须声明 memory limit 才有意义
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k -Djava.security.egd=file:/dev/urandom -Dfile.encoding=UTF-8"

USER paas
EXPOSE 8080

# 容器级自检（K8s 用 readiness/liveness 探针，这里只是 docker run 时的兜底）
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health/liveness" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
