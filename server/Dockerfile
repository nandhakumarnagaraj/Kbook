# ── Build stage ────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Build identity, supplied by ops/deploy-production.sh from the checked-out clone.
#
# The Maven build context is server/ (ops/docker-compose.production.yml), so the
# repository is not visible to the build and git-commit-id-maven-plugin cannot read a
# commit. pom.xml sets failOnNoGitDirectory=false, so that failed silently and shipped a
# header-only git.properties: the deployed jar carried no commit id, and "which commit is
# running?" could only be guessed from a wall-clock build time that came from a different
# file entirely (build-info.properties).
ARG GIT_COMMIT_ID
ARG GIT_BRANCH
ARG GIT_COMMIT_TIME

# Cache dependency layer separately from source
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q 2>/dev/null || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -Dmaven.test.skip=true -q

# Authoritative build identity, written after packaging so it cannot be lost to the
# plugin's no-git fallback. Kept in its own RUN so a Maven failure still reports as a
# Maven failure rather than being masked by the identity check.
#
# Writing target/classes/git.properties is NOT sufficient: `mvn package` above has
# already assembled the jar, so the file lands on disk but never inside the artifact.
# An earlier revision validated target/classes only, so the build passed and printed a
# plausible commit id while the shipped jar still carried the plugin's 35-byte
# header-only file. The identity is therefore injected into each jar, and then read
# back OUT OF THE JAR -- the check must inspect the thing that actually ships.
RUN set -eu; \
    : "${GIT_COMMIT_ID:?GIT_COMMIT_ID build arg is required - build via ops/deploy-production.sh}"; \
    printf 'git.branch=%s\ngit.commit.id=%s\ngit.commit.id.abbrev=%.7s\ngit.commit.time=%s\n' \
      "$GIT_BRANCH" "$GIT_COMMIT_ID" "$GIT_COMMIT_ID" "$GIT_COMMIT_TIME" \
      > target/classes/git.properties; \
    commit="$(sed -n 's/^git\.commit\.id=//p' target/classes/git.properties)"; \
    if [ "${#commit}" -ne 40 ] || [ -z "$commit" ]; then \
      echo "FATAL: git.commit.id is not a 40-char sha: '$commit'" >&2; exit 1; \
    fi; \
    case "$commit" in \
      *[!0-9a-f]*) echo "FATAL: git.commit.id is not lowercase hex: '$commit'" >&2; exit 1;; \
    esac; \
    for j in target/*.jar; do \
      rm -rf target/identity; mkdir -p target/identity/BOOT-INF/classes; \
      cp target/classes/git.properties target/identity/BOOT-INF/classes/git.properties; \
      jar --update --file "$j" -C target/identity BOOT-INF/classes/git.properties; \
    done; \
    for j in target/*.jar; do \
      rm -rf /tmp/verify-identity; mkdir -p /tmp/verify-identity; \
      ( cd /tmp/verify-identity && jar --extract --file "/app/$j" BOOT-INF/classes/git.properties ); \
      embedded="$(sed -n 's/^git\.commit\.id=//p' /tmp/verify-identity/BOOT-INF/classes/git.properties)"; \
      if [ "$embedded" != "$commit" ]; then \
        echo "FATAL: $j does not carry the build identity. Found '$embedded', expected '$commit'" >&2; exit 1; \
      fi; \
      echo "identity verified inside $j: $embedded"; \
    done

# ── Runtime stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN apk add --no-cache wget libwebp-tools
RUN addgroup -S kbook && adduser -S kbook -G kbook

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown kbook:kbook app.jar

USER kbook

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8081/api/v1/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
