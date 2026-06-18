# -------------------------------------------------------------------
# 1. Builder Stage (JDK + Node.js for Vaadin Frontend Build)
#
# Two operating modes, selected by what the build context contains:
#
#   (a) CI release flow: the `build-jar` workflow job pre-builds the fat
#       JAR and the `build-container` matrix downloads it into target/
#       before running `docker build`. The Dockerfile sees `target/*.jar`
#       in the context and skips the in-image Maven phase entirely
#       — Docker build collapses to ~3–5 min (jlink + image export only).
#
#   (b) Local `docker build .` from a clean checkout: target/ is empty,
#       so the Dockerfile falls back to running `mvn package` itself.
#       Cache mounts (Maven repo, npm cache, node_modules) keep that
#       fallback path fast on repeated local builds.
#
# .dockerignore lets `target/spring-ai-playground-*.jar` through to the
# context while still excluding the rest of `target/`, so stale local
# build artefacts don't leak in.
# -------------------------------------------------------------------
FROM ghcr.io/graalvm/jdk-community:21 AS builder
WORKDIR /app
COPY . .

# Cache mounts (only consulted when the fallback `mvn package` actually runs):
#   /root/.m2/repository : Maven local repo (jars).
#   /root/.npm           : npm package download cache for Vaadin's build-frontend goal.
#   /app/node_modules    : the materialized node_modules directory.
# `sharing=locked` prevents two parallel builds from corrupting these caches.
#
# Toolchain (nodejs / npm / maven) and `mvn package` are gated on the absence
# of a pre-built JAR so the CI fast-path skips both — no microdnf install and
# no Maven Central round-trip when `build-jar` already produced the artifact.
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    --mount=type=cache,target=/root/.npm,sharing=locked \
    --mount=type=cache,target=/app/node_modules,sharing=locked \
    if compgen -G 'target/spring-ai-playground-*.jar' > /dev/null; then \
      echo 'Pre-built fat JAR detected in build context — skipping toolchain install + mvn'; \
    else \
      echo 'No pre-built JAR in target/ — installing toolchain + running mvn package -Pproduction'; \
      microdnf install -y nodejs npm findutils gzip tar maven \
        && microdnf clean all \
        && mvn -B package -Pproduction -DskipTests; \
    fi

# -------------------------------------------------------------------
# 2. Layer Extraction Stage
# -------------------------------------------------------------------
FROM ghcr.io/graalvm/jdk-community:21 AS layers
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
# Boot 4 removed the layertools jarmode; tools + --launcher reproduces the same
# exploded layer layout that JarLauncher consumes in the runner stage below.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# -------------------------------------------------------------------
# 3. Custom JRE via jlink (GraalVM JDK 21, with Truffle/JS support intact)
#
# Module set was derived by running `jdeps --print-module-deps --recursive
# --ignore-missing-deps` against the extracted Spring Boot layers, plus a
# small safety margin for reflection-loaded modules:
#   - jdk.crypto.cryptoki / jdk.crypto.ec : Bouncy Castle / TLS edge cases
#   - jdk.dynalink                        : GraalVM JS invokedynamic
#   - jdk.localedata                      : full locale catalog (ICU/Vaadin)
#   - jdk.naming.dns                      : DNS for HTTP clients
#   - jdk.security.auth                   : SASL/JAAS used by some MCP transports
#   - jdk.zipfs                           : NIO zip filesystem for jar nesting
# Output: ~70 MB JRE (vs ~380 MB full JDK), with the GraalVM JIT / Truffle
# polyglot stack still available for the JavaScript tool sandbox.
# -------------------------------------------------------------------
FROM ghcr.io/graalvm/jdk-community:21 AS jre-builder
# `--strip-debug` invokes binutils' objcopy on native libraries, so the JDK
# image needs binutils installed before jlink can run.
RUN microdnf install -y binutils && microdnf clean all
RUN "$JAVA_HOME/bin/jlink" \
      --add-modules \
        java.base,java.compiler,java.desktop,java.instrument,java.naming,\
java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.sql,\
java.xml.crypto,jdk.attach,jdk.jdi,jdk.jfr,jdk.management,jdk.unsupported,\
jdk.crypto.cryptoki,jdk.crypto.ec,jdk.dynalink,jdk.httpserver,jdk.localedata,\
jdk.naming.dns,jdk.security.auth,jdk.zipfs \
      --no-header-files \
      --no-man-pages \
      --strip-debug \
      --compress=zip-9 \
      --output /opt/jre-min

# -------------------------------------------------------------------
# 4. Runner Stage (slim base + custom JRE + Spring Boot layers)
# -------------------------------------------------------------------
FROM debian:bookworm-slim
WORKDIR /app

# bookworm-slim has tini-friendly init via PID 1 directly; no extra packages
# needed for stdio MCP, but ensure CA certs are present for outgoing HTTPS
# (used by built-in tools / MCP clients).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/opt/jre-min
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-builder /opt/jre-min ${JAVA_HOME}

COPY --from=layers /app/extracted/dependencies/ ./
COPY --from=layers /app/extracted/spring-boot-loader/ ./
COPY --from=layers /app/extracted/snapshot-dependencies/ ./
COPY --from=layers /app/extracted/application/ ./

# OCI standard + MCP Registry ownership labels.
# `io.modelcontextprotocol.server.name` matches the `name` field in server.json
# and is what mcp-publisher checks for oci ownership verification.
LABEL org.opencontainers.image.source="https://github.com/spring-ai-community/spring-ai-playground" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.title="Spring AI Playground" \
      io.modelcontextprotocol.server.name="io.github.spring-ai-community/spring-ai-playground"

# Container default matches the Spring app default: Vaadin web UI on 8282 with the
# embedded MCP server speaking streamable-http. The `mcp-stdio` profile is opt-in
# via the env override, layered on top of the default profile so model config
# (Ollama / OpenAI) is preserved:
#   docker run -i --rm -e SPRING_PROFILES_INCLUDE=mcp-stdio \
#     -v spring-ai-playground:/root ghcr.io/.../spring-ai-playground
# The MCP Registry's runtimeArguments inject that env automatically for registry-
# driven launches, so registry users don't need to remember it.

EXPOSE 8282

ENTRYPOINT ["java", \
  "-Dpolyglot.engine.WarnInterpreterOnly=false", \
  "org.springframework.boot.loader.launch.JarLauncher"]