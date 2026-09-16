# ============================================================
# 后端镜像：多阶段构建
# 第一阶段用 Maven 镜像编译打包，第二阶段只带 JRE 运行。
# 为什么要分两阶段？编译需要 JDK + Maven + 全部依赖（几百 MB），
# 而运行只需要 JRE + 一个 jar，分阶段后最终镜像能小一大半。
# ============================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 换成阿里云镜像，容器里下载依赖才不会慢到超时
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 先只复制 pom 并预热依赖：只要 pom 没变，这一层就能命中缓存，
# 以后改代码重新构建时不用再下一遍依赖（Docker 分层缓存最实用的技巧之一）
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# 再复制源码并打包
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

# 时区设置：容器默认是 UTC，不设置的话日志与入库时间会比北京时间少 8 小时
ENV TZ=Asia/Shanghai

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
