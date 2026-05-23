# syntax=docker/dockerfile:1.7
#
# TSS GRC Platform — backend container.
#
# Multi-stage build:
#   1. builder — Maven + JDK 17, runs `mvn package -DskipTests`
#   2. runtime — JRE-only Alpine image, non-root user, healthcheck
#
# Frontend (`tss-grc-ui-v2/`) is built and served separately — see
# `Dockerfile.frontend` in a later sprint, or serve via nginx in the same pod.
#
# Build:   docker build -t tss/grc-platform:latest .
# Run:     docker run -d --name grc -p 8084:8084 \
#            -e JWT_SECRET="$(openssl rand -base64 48)" \
#            -e DB_URL="jdbc:sqlserver://db:1433;databaseName=TSS_GRC;encrypt=true;trustServerCertificate=true" \
#            -e DB_USERNAME=sa -e DB_PASSWORD=secret \
#            tss/grc-platform:latest
#

# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

# Install Maven (Alpine doesn't ship a recent enough version)
ARG MAVEN_VERSION=3.9.9
RUN apk add --no-cache curl tar bash \
 && curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    | tar -xzC /opt \
 && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

WORKDIR /build

# Copy pom + warm dependency cache (better layer caching when only sources change)
COPY tss-grc-backend/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline

# Copy sources and build
COPY tss-grc-backend/src ./src
RUN mvn -B -DskipTests package \
 && cp target/tss-grc-platform-*.jar /build/app.jar


# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Non-root user — never run Java as root in containers
RUN addgroup -S grc && adduser -S -G grc -u 10001 grc

# Curl for healthcheck only
RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder --chown=grc:grc /build/app.jar /app/app.jar

USER grc
EXPOSE 8084

# Tunables (override via -e at run time)
#
# -Duser.timezone=Asia/Riyadh pins the JVM default timezone so every
# LocalDate.now() / LocalDateTime.now() returns Saudi-local dates. The
# Alpine base defaults to UTC, which would silently shift dates by 3
# hours every day for any "now" call between midnight and 3am Riyadh
# — wrong audit due dates, wrong SLA windows, wrong overdue sweeps.
# GrcApplication.main() sets the same value as a safety net for
# non-Docker launches; defining it here makes the intent visible and
# lets operators override per-deploy via JAVA_OPTS env override.
ENV PROFILE=prod \
    JAVA_OPTS="-Duser.timezone=Asia/Riyadh -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XshowSettings:vm" \
    SPRING_PROFILES_ACTIVE=prod

# Liveness via Actuator. Adjust path if /actuator changes.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8084/actuator/health | grep -q '"status":"UP"' || exit 1

# exec form so signals propagate (SIGTERM → graceful shutdown)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE"]
