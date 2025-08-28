## 1) Fetch & Pull Latest
```
git fetch --all --prune
git switch main
git pull --rebase
```
## 2) Choose Your Upgrade Method
### A. Running with Docker
Build a fresh image and replace the running container. Data will be preserved by the named volume.
```
# Build image
./mvnw clean package spring-boot:build-image -Pproduction -DskipTests=true \
  -Dspring-boot.build-image.imageName=jmlab/spring-ai-playground:latest

# Stop and remove the existing container (image and volume remain intact)
docker stop spring-ai-playground || true
docker rm spring-ai-playground || true

# Run a new container, reusing the named volume for data persistence
docker run -d -p 8282:8282 --name spring-ai-playground \
-e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
-v spring-ai-playground:/home \
--restart unless-stopped \
jmlab/spring-ai-playground:latest
```
Notes:
- Data persistence: application data is stored in the `spring-ai-playground` Docker volume and persists across container replacement.
- Linux: if `host.docker.internal` is unavailable, consider `--network="host"` or use the host’s IP on the Docker bridge (e.g., `172.17.0.1`).

### B. Running Locally
If you run Spring AI Playground directly on your machine (not in Docker), follow these steps:

```
# Clean and rebuild the application
./mvnw clean install -Pproduction -DskipTests=true

# Restart the application
./mvnw spring-boot:run
```
Notes:
- If an existing Spring AI Playground process is running, please stop it first (e.g., using Ctrl+C or terminating the process) to avoid port conflicts on `8282`.
- Data persistence: Data saved locally (e.g., in ~/.spring-ai-playground, or as configured in application.yaml) will persist.

## 3) Post-Upgrade Verification
   Check the application at http://localhost:8282 to ensure the new version is running.
   
----
