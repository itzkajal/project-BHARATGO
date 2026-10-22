# BHARATGO — Apna Bharat, Apni Kahani

BHARATGO is a beginner-friendly, India-focused travel planning application. Users can create, view, edit and delete trips. This version uses **Docker commands only**: build images from Dockerfiles, create containers with `docker run`, and connect the containers with a Docker network. **Docker Compose is not used.**

## 1. Architecture

```text
Browser: http://localhost:8080
       |
       v
bharatgo-frontend (Nginx + React static site)
       | /api/* reverse proxy over bharatgo-net
       v
bharatgo-backend (Java 21 + Spring Boot, port 8080)
       | over bharatgo-net
       v
bharatgo-db (MySQL 8.4, persistent volume)
```

MySQL is not published to the host. Only the frontend port is published. The backend and database communicate on a user-defined Docker network.

## 2. Requirements

- Backend: **Java 21 + Spring Boot 3.5**. The backend Dockerfile builds with Maven and Java 21, then runs on a Java 21 JRE image. You do not need to install Java or Maven on the host when using Docker.
- Docker Desktop on Windows/macOS, or Docker Engine on Linux
- Internet connection for the first image builds/pulls
- Recommended: 2 CPU cores and 2–4 GB free memory

Verify Docker:

```bash
docker --version
docker info
```

## 3. Build images

Run these commands from the `bharatgo-app` project root:

```bash
docker build -t bharatgo-backend:1.0 ./backend
docker build -t bharatgo-frontend:1.0 ./frontend
docker pull mysql:8.4
docker images
```

MySQL uses the official `mysql:8.4` image, so a separate database Dockerfile is not needed. The frontend Dockerfile builds React/Vite assets and then serves them with Nginx.

## 4. Create network and persistent volume

```bash
docker network create bharatgo-net
docker volume create bharatgo-mysql-data
docker network ls
docker volume ls
```

## 5. Start MySQL container

These are local learning credentials only. Change them before use outside a local classroom. Do not commit passwords to Git.

**Linux/macOS terminal:**

```bash
docker run -d \
  --name bharatgo-db \
  --network bharatgo-net \
  --network-alias bharatgo-db \
  --restart unless-stopped \
  -e MYSQL_ROOT_PASSWORD='ChangeThis_Root_Only_2026!' \
  -e MYSQL_DATABASE='travel_planner' \
  -e MYSQL_USER='travel_app' \
  -e MYSQL_PASSWORD='ChangeThis_App_Only_2026!' \
  -v bharatgo-mysql-data:/var/lib/mysql \
  -v "$PWD/database/init/001-schema.sql:/docker-entrypoint-initdb.d/001-schema.sql:ro" \
  -v "$PWD/database/init/002-seed.sql:/docker-entrypoint-initdb.d/002-seed.sql:ro" \
  --health-cmd='mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' \
  --health-interval=10s --health-timeout=5s --health-retries=10 --health-start-period=30s \
  mysql:8.4
```

**Windows PowerShell:** run the equivalent command below (the current directory is mounted using `${PWD}`):

```powershell
docker run -d `
  --name bharatgo-db `
  --network bharatgo-net `
  --network-alias bharatgo-db `
  --restart unless-stopped `
  -e MYSQL_ROOT_PASSWORD='ChangeThis_Root_Only_2026!' `
  -e MYSQL_DATABASE='travel_planner' `
  -e MYSQL_USER='travel_app' `
  -e MYSQL_PASSWORD='ChangeThis_App_Only_2026!' `
  -v bharatgo-mysql-data:/var/lib/mysql `
  -v "${PWD}/database/init/001-schema.sql:/docker-entrypoint-initdb.d/001-schema.sql:ro" `
  -v "${PWD}/database/init/002-seed.sql:/docker-entrypoint-initdb.d/002-seed.sql:ro" `
  --health-cmd='mysqladmin ping -h 127.0.0.1 -uroot -p"$env:MYSQL_ROOT_PASSWORD" --silent' `
  --health-interval=10s --health-timeout=5s --health-retries=10 --health-start-period=30s `
  mysql:8.4
```

Wait until MySQL is healthy:

```bash
docker ps
docker logs bharatgo-db
```

MySQL initialization scripts run only when the volume is empty. Existing database volumes are not reseeded or reinitialized.

## 6. Start backend container

Run after MySQL is healthy:

```bash
docker run -d \
  --name bharatgo-backend \
  --network bharatgo-net \
  --restart unless-stopped \
  -e SERVER_PORT=8080 \
  -e DB_HOST=bharatgo-db \
  -e DB_PORT=3306 \
  -e DB_NAME=travel_planner \
  -e DB_USER=travel_app \
  -e DB_PASSWORD='ChangeThis_App_Only_2026!' \
  bharatgo-backend:1.0
```

**PowerShell:** the same command can be entered using backticks instead of backslashes for line continuation, or paste it as a single line.

Check logs and health:

```bash
docker logs bharatgo-backend
docker inspect --format='{{.State.Status}}' bharatgo-backend
```

## 7. Start frontend container

```bash
docker run -d \
  --name bharatgo-frontend \
  --network bharatgo-net \
  --publish 8080:80 \
  --restart unless-stopped \
  bharatgo-frontend:1.0
```

Open **http://localhost:8080**. Nginx proxies `/api/` requests to `bharatgo-backend:8080` through the Docker network.

## 8. Verify API and CRUD

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/ready
curl http://localhost:8080/api/trips
```

Expected health response resembles `{"status":"ok","service":"bharatgo-api","java":"21"}`. Readiness should return `{"status":"ready","database":"connected"}` when MySQL is reachable.

Create a trip using curl (Linux/macOS/Git Bash):

```bash
curl -X POST http://localhost:8080/api/trips \
  -H 'Content-Type: application/json' \
  -d '{"destination":"Mumbai, Maharashtra","travelers":2,"budget":8500,"notes":"Weekend city trip"}'
```

Copy the returned `id` and test update/delete, replacing `1` with that ID:

```bash
curl -X PUT http://localhost:8080/api/trips/1 \
  -H 'Content-Type: application/json' \
  -d '{"destination":"Mumbai and Pune","travelers":3,"budget":12000,"notes":"Updated plan"}'
curl -i -X DELETE http://localhost:8080/api/trips/1
```

You can also create, edit and delete trips from the website UI.

## 9. Useful Docker commands

```bash
docker ps
docker ps -a
docker images
docker network inspect bharatgo-net
docker volume inspect bharatgo-mysql-data
docker logs -f bharatgo-frontend
docker logs -f bharatgo-backend
docker logs -f bharatgo-db
docker stats
```

## 10. Persistence test

```bash
docker stop bharatgo-db
docker rm bharatgo-db
```

Then repeat the MySQL `docker run` command from section 5 **without changing or deleting** `bharatgo-mysql-data`. The data remains in the volume. Restart backend if needed:

```bash
docker restart bharatgo-backend
```

Do not run `docker volume rm bharatgo-mysql-data` unless you intentionally want to delete all saved trips.

## 11. Rebuild after changing code

```bash
docker build -t bharatgo-backend:1.1 ./backend
docker build -t bharatgo-frontend:1.1 ./frontend
```

Remove the old relevant container, then run a new container with the new image tag. For example:

```bash
docker rm -f bharatgo-frontend
docker run -d --name bharatgo-frontend --network bharatgo-net -p 8080:80 --restart unless-stopped bharatgo-frontend:1.1
```

Use the same approach for backend updates, keeping its environment variables unchanged. Version tags such as `1.0`, `1.1`, and `1.2` make rollback easier.

## 12. Cleanup

Stop and remove app containers:

```bash
docker rm -f bharatgo-frontend bharatgo-backend bharatgo-db
docker network rm bharatgo-net
```

This intentionally leaves the database volume intact. To delete all database data too, run this separately and only when certain:

```bash
docker volume rm bharatgo-mysql-data
```

## API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Backend process health |
| GET | `/api/ready` | Database readiness |
| GET | `/api/trips` | List trips |
| POST | `/api/trips` | Create a trip |
| PUT | `/api/trips/:id` | Update a trip |
| DELETE | `/api/trips/:id` | Delete a trip |

## Important files

- `frontend/Dockerfile`: multi-stage React/Vite build and Nginx runtime.
- `frontend/nginx.conf`: SPA fallback and `/api/` reverse proxy.
- `backend/Dockerfile`: Java 21 Spring Boot multi-stage build, non-root user and health check.
- `backend/src/server.js`: REST API and MySQL connection pool.
- `database/init/001-schema.sql`: table, indexes and constraints.
- `database/init/002-seed.sql`: starter trips.
- `practicals/01-DOCKER-DEPLOYMENT-PRACTICAL.txt`: manual container deployment guide.
- `practicals/02-DOCKER-IMAGE-REGISTRY-PRACTICAL.txt`: Docker Hub and optional ECR guide.

## Security notes and validation status

The commands above use simple environment variables for a local classroom demo. Environment variables can be inspected by users with Docker access; for production, use Docker secrets or a managed secret store and avoid putting passwords directly in shell history. Do not publish MySQL port 3306 unless there is a specific need. This delivery has not been run against a live Docker daemon in this environment; execute the steps and verify logs, API responses, CRUD, and volume persistence on your Docker-enabled machine.
