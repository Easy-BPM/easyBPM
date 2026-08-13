FROM gradle:jdk21 AS builder
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --home-dir /app --shell /usr/sbin/nologin easybpm \
    && mkdir -p /app/logs \
    && chown -R easybpm:easybpm /app

COPY --from=builder --chown=easybpm:easybpm /home/gradle/project/build/libs/*.jar app.jar

USER easybpm
EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
