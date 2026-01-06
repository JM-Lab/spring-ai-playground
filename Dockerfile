# -------------------------------------------------------------------
# 1. Builder Stage (JDK + Node.js for Vaadin Frontend Build)
# -------------------------------------------------------------------
FROM ghcr.io/graalvm/jdk-community:21 AS builder
WORKDIR /app

RUN microdnf install -y nodejs npm findutils gzip tar maven
COPY . .

RUN mvn clean package -Pproduction -DskipTests

# -------------------------------------------------------------------
# 2. Layer Extraction Stage
# -------------------------------------------------------------------
FROM ghcr.io/graalvm/jdk-community:21 AS layers
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# -------------------------------------------------------------------
# 3. Runner Stage
# -------------------------------------------------------------------
FROM ghcr.io/graalvm/jdk-community:21
WORKDIR /app

COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

EXPOSE 8282

ENTRYPOINT ["java", \
  "-Dpolyglot.engine.WarnInterpreterOnly=false", \
  "org.springframework.boot.loader.launch.JarLauncher"]