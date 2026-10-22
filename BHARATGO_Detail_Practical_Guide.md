# BHARATGO — Docker Deployment Practical (Git Clone to Public IP)

**Workflow:** GitHub repository → Dockerfiles → Docker images → Docker containers → browser verification  
**Deployment style:** Individual `docker` commands only. **Docker Compose is not used.**  
**Application stack expected:** React/Vite + Nginx frontend, Java 21/Spring Boot backend, MySQL 8.4 database  
**Repository:** https://github.com/chetanraval07/project-BHARATGO-.git

> **Important repository note:** I could not inspect the live GitHub repository from this environment. Therefore, this guide begins by cloning the exact URL and then asks you to verify the actual filenames and paths before building. If your repository uses different folder names or does not yet contain a Dockerfile, use the relevant troubleshooting section instead of assuming a build will work.

---

## Deployment flow

```text
GitHub repository
       |
       | git clone
       v
Local project folder
       |
       | docker build -f Dockerfile ...
       v
Docker images
       |
       | docker run ...
       v
Containers
  ├── bharatgo-frontend (Nginx, public web port 8080)
  ├── bharatgo-backend  (Java 21 / Spring Boot, private)
  └── bharatgo-db       (MySQL 8.4, private + persistent volume)
       |
       v
http://<EC2-PUBLIC-IP>:8080
```

## Before you start

You need:

- Git installed.
- Docker Engine running (Docker Desktop on Windows/macOS, or Docker Engine on Linux/EC2).
- Internet access to clone the repository, download base images, and download build dependencies.
- For EC2: an instance with a public IPv4 address and a security group that permits TCP `8080` from your own public IP.
- Enough memory for MySQL + Java + Nginx. A small EC2 instance may run out of memory; for a classroom test, prefer at least 4 GB RAM where possible.
- A strong MySQL root password and a separate application password. Do not use the example values in a public/production deployment.

**Never publish MySQL port `3306` or the backend port `8080` directly to the internet.** In this design, only Nginx's frontend port is published.

---

# STEP 1 — Install and verify Git

### Windows

Install Git for Windows from [git-scm.com](https://git-scm.com/download/win), then open **PowerShell** or **Git Bash**.

### Ubuntu / Debian

```bash
sudo apt-get update
sudo apt-get install -y git
```

### Verify

```bash
git --version
```

**Expected result:** a Git version number is displayed. If the command is not found, install Git and reopen the terminal.

---

# STEP 2 — Clone the BHARATGO repository

Choose a folder where you want to keep the project, then run:

```bash
git clone https://github.com/chetanraval07/project-BHARATGO-.git
cd project-BHARATGO-
```

### What these commands do

- `git clone` downloads the repository and its current checked-out branch to your computer.
- `cd project-BHARATGO-` moves the terminal into the downloaded project directory.

### Verify the repository

```bash
git status
git remote -v
git branch --show-current
```

**Expected result:** `git status` reports a working tree, and `git remote -v` shows the supplied GitHub URL as `origin`.

If Git reports that the repository cannot be found or access is denied, open the repository URL in your browser and confirm it is public or that your GitHub account has permission.

---

# STEP 3 — Inspect the repository before running Docker commands

Run these commands from the repository root.

### Linux / macOS / EC2

```bash
pwd
ls -la
find . -maxdepth 3 -type f \( -name 'Dockerfile' -o -name 'pom.xml' -o -name 'package.json' -o -name 'nginx.conf' -o -name '*.sql' \)
```

### Windows PowerShell

```powershell
Get-Location
Get-ChildItem -Force
Get-ChildItem -Recurse -File -Include Dockerfile,pom.xml,package.json,nginx.conf,*.sql | Select-Object -ExpandProperty FullName
```

### Confirm these items

- [ ] A backend directory containing a Java project, normally `pom.xml` and `src/`.
- [ ] A frontend directory containing `package.json` and frontend source files.
- [ ] A backend `Dockerfile`.
- [ ] A frontend `Dockerfile`.
- [ ] An Nginx configuration that proxies `/api/` requests to the backend service name.
- [ ] SQL initialization scripts, if the project uses them.

**Do not continue blindly if these files are missing.** Docker can only build a folder that has a valid Dockerfile. Use the actual paths shown by the inspection command. The example commands below assume `backend/Dockerfile`, `frontend/Dockerfile`, and `database/init/` exist.

---

# STEP 4 — Install and verify Docker

## Option A: Local Windows or macOS

1. Install Docker Desktop from [Docker Desktop](https://www.docker.com/products/docker-desktop/).
2. Start Docker Desktop.
3. Wait until Docker Engine is running.

## Option B: Ubuntu EC2 / Ubuntu Linux

A simple package-based installation is:

```bash
sudo apt-get update
sudo apt-get install -y docker.io git unzip
sudo systemctl enable --now docker
```

Verify:

```bash
sudo docker --version
sudo docker info
```

> On EC2, use `sudo docker` if your user does not have permission to access Docker. Do not make the Docker socket world-writable. For production, follow the current official Docker Engine installation instructions for your Linux distribution.

### What does Docker do here?

Docker builds images from Dockerfiles. A container is a running instance of an image. Docker keeps the application processes isolated and lets containers communicate over a Docker network.

**Expected result:** `docker info` prints engine information. If it says it cannot connect to the daemon, start Docker Desktop or the Linux Docker service.

---

# STEP 5 — Confirm the Java 21 backend Dockerfile

The host does **not** need Java or Maven installed if the Dockerfile uses a Java 21/Maven build stage and a Java 21 runtime stage.

Inspect the backend Dockerfile:

```bash
cat backend/Dockerfile
```

In PowerShell:

```powershell
Get-Content .\backend\Dockerfile
```

Look for a Java 21 image, for example a Maven image based on JDK 21 and/or a Java 21 runtime image. Do not assume it is correct merely because the file exists.

If `backend/Dockerfile` is missing, stop and check the repository README or add a suitable Java 21 Dockerfile before proceeding. Do not replace project files without first understanding the existing build.

---

# STEP 6 — Build the backend Docker image

Run from the repository root:

```bash
docker build -t bharatgo-backend:1.0 ./backend
```

On EC2, prefix the command with `sudo` if needed:

```bash
sudo docker build -t bharatgo-backend:1.0 ./backend
```

### Explanation

- `docker build` builds an image.
- `-t bharatgo-backend:1.0` assigns the image name and version tag.
- `./backend` is the build context. The Dockerfile and files needed by the build must be inside this directory or referenced correctly by the Dockerfile.

### Expected result

The build completes without an error and produces an image named `bharatgo-backend` with tag `1.0`.

### If it fails

- `Dockerfile not found`: confirm the actual backend folder and Dockerfile path.
- Maven dependency download fails: check internet access and repository availability.
- Compilation error: read the first Java/Maven error; the source must compile before the image can be built.
- Wrong Java version: inspect the Dockerfile and `pom.xml`; make sure the project is configured for Java 21.

---

# STEP 7 — Build the frontend Docker image

Run from the repository root:

```bash
docker build -t bharatgo-frontend:1.0 ./frontend
```

On EC2:

```bash
sudo docker build -t bharatgo-frontend:1.0 ./frontend
```

The frontend Dockerfile should build the React/Vite application and serve the resulting static files using Nginx.

### Verify the images

```bash
docker images
```

On EC2:

```bash
sudo docker images
```

**Expected result:** `bharatgo-backend:1.0` and `bharatgo-frontend:1.0` appear in the image list.

If the repository is a single frontend-only application and has no Java backend, these two commands will not work as written. Re-check STEP 3 and use the actual architecture present in the repository.

---

# STEP 8 — Download MySQL 8.4 image

```bash
docker pull mysql:8.4
docker images
```

On EC2:

```bash
sudo docker pull mysql:8.4
sudo docker images
```

**Explanation:** `docker pull` downloads an image from the registry. The MySQL container will run the database server. It should not be exposed directly to the public internet.

---

# STEP 9 — Create a Docker network and persistent volume

Create a private network:

```bash
docker network create bharatgo-net
```

Create persistent storage for MySQL:

```bash
docker volume create bharatgo-mysql-data
```

On EC2, use `sudo docker` if required.

### Why do we need these?

- **Docker network:** lets containers find each other by container name. The backend can connect to MySQL using `bharatgo-db:3306`.
- **Docker volume:** keeps database files outside the lifecycle of a particular MySQL container. Removing and recreating the database container does not remove the volume.

Verify:

```bash
docker network ls
docker volume ls
docker network inspect bharatgo-net
docker volume inspect bharatgo-mysql-data
```

If the network or volume already exists, inspect it and reuse it if it belongs to this practical. Do not delete a volume that contains data you need.

---

# STEP 10 — Prepare database passwords and initialization files

Choose two different passwords:

- `MYSQL_ROOT_PASSWORD`: administrative root password for MySQL.
- `MYSQL_PASSWORD`: application password for the `travel_app` database user.

The **same application password** must be supplied to MySQL and the backend.

Do not commit passwords to Git. For a simple classroom practical, environment variables can demonstrate the concept, but they may be visible to privileged Docker users and in shell history. Use a secret manager or a suitable secrets mechanism for production.

### Check the SQL files

The example database command expects:

```text
database/init/001-schema.sql
database/init/002-seed.sql
```

Check that these files actually exist:

Linux/macOS/EC2:
```bash
ls -l database/init/
```

Windows PowerShell:
```powershell
Get-ChildItem .\database\init\
```

If the repository uses a different SQL directory, update the host-side paths in STEP 11. If the repository has no SQL initialization scripts, use its documented database setup method instead; do not create empty or guessed schema files.

---

# STEP 11 — Start the MySQL container

**Run this from the repository root** so the SQL file paths resolve correctly. Replace both password placeholders before running the command.

## Linux / macOS / EC2

```bash
docker run -d \
  --name bharatgo-db \
  --network bharatgo-net \
  --restart unless-stopped \
  -e MYSQL_ROOT_PASSWORD='REPLACE_WITH_STRONG_ROOT_PASSWORD' \
  -e MYSQL_DATABASE='travel_planner' \
  -e MYSQL_USER='travel_app' \
  -e MYSQL_PASSWORD='REPLACE_WITH_STRONG_APP_PASSWORD' \
  -v bharatgo-mysql-data:/var/lib/mysql \
  -v "$PWD/database/init/001-schema.sql:/docker-entrypoint-initdb.d/001-schema.sql:ro" \
  -v "$PWD/database/init/002-seed.sql:/docker-entrypoint-initdb.d/002-seed.sql:ro" \
  --health-cmd='mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' \
  --health-interval=10s \
  --health-timeout=5s \
  --health-retries=10 \
  --health-start-period=30s \
  mysql:8.4
```

## Windows PowerShell

```powershell
docker run -d `
  --name bharatgo-db `
  --network bharatgo-net `
  --restart unless-stopped `
  -e MYSQL_ROOT_PASSWORD='REPLACE_WITH_STRONG_ROOT_PASSWORD' `
  -e MYSQL_DATABASE='travel_planner' `
  -e MYSQL_USER='travel_app' `
  -e MYSQL_PASSWORD='REPLACE_WITH_STRONG_APP_PASSWORD' `
  -v bharatgo-mysql-data:/var/lib/mysql `
  -v "${PWD}/database/init/001-schema.sql:/docker-entrypoint-initdb.d/001-schema.sql:ro" `
  -v "${PWD}/database/init/002-seed.sql:/docker-entrypoint-initdb.d/002-seed.sql:ro" `
  --health-cmd='mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' `
  --health-interval=10s `
  --health-timeout=5s `
  --health-retries=10 `
  --health-start-period=30s `
  mysql:8.4
```

### Manual changes required

1. Replace `REPLACE_WITH_STRONG_ROOT_PASSWORD`.
2. Replace `REPLACE_WITH_STRONG_APP_PASSWORD`.
3. Use the application password again in STEP 12.
4. If SQL files are in a different location, change the two host paths before the colon.
5. Keep the container-side paths `/docker-entrypoint-initdb.d/...` unchanged.

### Important initialization behavior

MySQL runs the initialization SQL scripts only when its data directory is empty. If you already used `bharatgo-mysql-data`, changing the SQL files will not automatically rerun them.

### Verify MySQL

```bash
docker ps -a
docker logs bharatgo-db
docker inspect --format='{{.State.Health.Status}}' bharatgo-db
```

**Expected result:** the database container is running and its health status becomes `healthy`.

If it exits, inspect `docker logs bharatgo-db` before proceeding. Common reasons include wrong SQL file paths, a container name already in use, low disk/memory, or an existing volume initialized with different credentials.

> If the repository does not contain the two SQL files named above, do not run this exact command until you adjust it to match the actual database setup.

---

# STEP 12 — Start the Java 21 / Spring Boot backend container

This command assumes the backend reads the following environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, and `SERVER_PORT`. **Confirm these names in the project's `application.properties`, `application.yml`, or README.** If the application uses different names, use the names expected by that application.

## Linux / macOS / EC2

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
  -e DB_PASSWORD='REPLACE_WITH_THE_SAME_APP_PASSWORD' \
  bharatgo-backend:1.0
```

## Windows PowerShell

```powershell
docker run -d `
  --name bharatgo-backend `
  --network bharatgo-net `
  --restart unless-stopped `
  -e SERVER_PORT=8080 `
  -e DB_HOST=bharatgo-db `
  -e DB_PORT=3306 `
  -e DB_NAME=travel_planner `
  -e DB_USER=travel_app `
  -e DB_PASSWORD='REPLACE_WITH_THE_SAME_APP_PASSWORD' `
  bharatgo-backend:1.0
```

### Manual changes required

- Replace the password placeholder with the exact application password from STEP 11.
- If the backend uses different environment variable names, change the variable names/values to match the code.
- If the backend uses a different internal port, update `SERVER_PORT` and the Nginx upstream configuration accordingly.

### Verify the backend

```bash
docker ps -a
docker logs --tail 200 bharatgo-backend
```

If the app includes health/readiness endpoints, test them from inside the backend container or through the frontend proxy. The example endpoint names below are only correct if the project implements them:

```bash
docker exec bharatgo-backend wget -q -O - http://127.0.0.1:8080/api/health
docker exec bharatgo-backend wget -q -O - http://127.0.0.1:8080/api/ready
```

If `wget` is not installed in the backend image, that command may fail even when the application is running. In that case, inspect the backend logs and test through Nginx in STEP 14.

**Do not proceed until backend logs show the application has started and its database connection is working.**

---

# STEP 13 — Start the Nginx frontend container

This command maps host port `8080` to Nginx container port `80`.

```bash
docker run -d \
  --name bharatgo-frontend \
  --network bharatgo-net \
  --publish 8080:80 \
  --restart unless-stopped \
  bharatgo-frontend:1.0
```

On Windows PowerShell:

```powershell
docker run -d `
  --name bharatgo-frontend `
  --network bharatgo-net `
  --publish 8080:80 `
  --restart unless-stopped `
  bharatgo-frontend:1.0
```

On EC2, prefix the command with `sudo` if needed.

### What is happening?

- `--publish 8080:80` maps port `8080` on the host to port `80` inside the Nginx container.
- `--network bharatgo-net` connects Nginx to the private Docker network.
- Nginx serves the frontend and should proxy `/api/` requests to `bharatgo-backend` on its internal port.

### Verify

```bash
docker ps
docker logs --tail 100 bharatgo-frontend
docker port bharatgo-frontend
```

**Expected result:** the frontend container is running and port mapping shows host `8080` mapped to container `80`.

If port 8080 is already in use, choose a different host port such as `8090` by using `-p 8090:80`. Then use port 8090 in all browser/API URLs and in the EC2 security group.

---

# STEP 14 — Test the application locally

On the machine running Docker, open:

**http://localhost:8080**

Check that:

- [ ] BHARATGO website loads.
- [ ] Destination cards or travel content display.
- [ ] The trips list loads if the database has seed data.
- [ ] You can create a trip.
- [ ] You can edit a trip.
- [ ] You can delete a trip.
- [ ] Success and error messages behave as expected.

Test API routes through Nginx. These are example routes; confirm them against the actual backend source/README.

Linux/macOS:
```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/ready
curl -i http://localhost:8080/api/trips
```

Windows PowerShell:
```powershell
curl.exe -i http://localhost:8080/api/health
curl.exe -i http://localhost:8080/api/ready
curl.exe -i http://localhost:8080/api/trips
```

**Expected:** a successful HTTP response and JSON from the implemented endpoints. If the project does not implement `/api/health` or `/api/ready`, use the routes actually present in the source code.

### CRUD verification

Use the website form first. If you prefer API testing, use the exact HTTP methods and JSON fields implemented by your backend. Do not assume a request body or URL until you have checked the API controller.

---

# STEP 15 — Check logs, containers, network and volume

```bash
docker ps
docker ps -a
docker images
docker network inspect bharatgo-net
docker volume inspect bharatgo-mysql-data
docker logs --tail 100 bharatgo-db
docker logs --tail 100 bharatgo-backend
docker logs --tail 100 bharatgo-frontend
docker stats
```

On EC2, add `sudo` to the Docker commands if required.

### What to verify

- All three containers exist and are running.
- MySQL becomes healthy.
- All three containers are attached to `bharatgo-net`.
- The frontend publishes the chosen web port.
- MySQL does not publish port 3306 to the host.
- The database uses the named volume `bharatgo-mysql-data`.

---

# STEP 16 — Test database persistence

1. Create a test trip in the website.
2. Confirm it appears in the trip list.
3. Remove only the MySQL container:

   ```bash
   docker rm -f bharatgo-db
   ```

4. Recreate MySQL by repeating STEP 11 with the same credentials, same named volume, and correct SQL file mounts.
5. Wait for MySQL to become healthy.
6. Restart the backend:

   ```bash
   docker restart bharatgo-backend
   ```

7. Verify the backend can connect to the database again.
8. Refresh the trip list.

**Success:** the previously saved trip is still present.

**Never run `docker volume rm bharatgo-mysql-data` during this test.** That command deletes the persistent database volume and its saved data.

---

# STEP 17 — Deploy to AWS EC2 and test using its Public IPv4 address

## 17.1 Launch an EC2 instance

In AWS Console:

1. Open **EC2 → Instances → Launch instances**.
2. Name it `bharatgo-server`.
3. Select a supported Ubuntu Server LTS or Amazon Linux AMI.
4. Choose an instance size with sufficient memory. MySQL and Java can be memory intensive; prefer at least 4 GB RAM for a smoother classroom test where possible.
5. Create/select an SSH key pair and keep the private key secure.
6. Ensure the instance has a public IPv4 address, and its subnet has a route to an Internet Gateway.
7. Configure the instance's security group inbound rules:

| Type | Port | Source |
|---|---:|---|
| SSH | 22 | Your public IP `/32` only |
| Custom TCP | 8080 | Your public IP `/32` for a restricted test |

If you intentionally want the website available to everyone, port 8080 can be opened to `0.0.0.0/0`, but that exposes the demo app publicly. Restrict it to your own IP for testing whenever possible.

**Do not open inbound ports 3306 or the backend's internal port.**

8. Launch the instance.
9. Wait for **Instance state: Running** and **Status checks: 2/2 passed**.
10. Copy the **Public IPv4 address** from the EC2 console.

Record these values for the following steps:

- `EC2_PUBLIC_IP`: your actual instance Public IPv4 address.
- `SSH_KEY_PATH`: path to your downloaded `.pem` private key.
- `EC2_USER`: usually `ubuntu` for Ubuntu AMIs, or `ec2-user` for Amazon Linux.

## 17.2 Connect to EC2 over SSH

Ubuntu:

```bash
ssh -i "/path/to/your-key.pem" ubuntu@EC2_PUBLIC_IP
```

Amazon Linux:

```bash
ssh -i "/path/to/your-key.pem" ec2-user@EC2_PUBLIC_IP
```

**Replace both the key path and `EC2_PUBLIC_IP` with your real values.** Do not share your private key.

## 17.3 Install Docker and Git on EC2

For Ubuntu:

```bash
sudo apt-get update
sudo apt-get install -y docker.io git unzip
sudo systemctl enable --now docker
sudo docker --version
sudo docker info
```

For Amazon Linux, install Docker using the current instructions for that Amazon Linux release. Package/service commands differ between Amazon Linux versions.

## 17.4 Clone the repository directly on EC2

After Docker and Git are installed:

```bash
git clone https://github.com/chetanraval07/project-BHARATGO-.git
cd project-BHARATGO-
```

Verify:

```bash
git status
ls -la
```

Check the actual folders and files as in STEP 3.

## 17.5 Build images on EC2

From the repository root, assuming the two Dockerfiles exist at the expected paths:

```bash
sudo docker build -t bharatgo-backend:1.0 ./backend
sudo docker build -t bharatgo-frontend:1.0 ./frontend
sudo docker pull mysql:8.4
sudo docker images
```

## 17.6 Start the EC2 containers

Repeat these steps on EC2 in this order:

1. STEP 9 — create `bharatgo-net` and `bharatgo-mysql-data`.
2. STEP 11 — start MySQL. Use `sudo docker run`, replace the password placeholders, and run from the project root so SQL paths resolve.
3. Wait until MySQL is healthy.
4. STEP 12 — start the Java 21 backend. Use the same application password as MySQL.
5. Confirm backend logs show successful startup and database connectivity.
6. STEP 13 — start the frontend with `-p 8080:80`.
7. Confirm all containers are running.

Example to create network and volume on EC2:

```bash
sudo docker network create bharatgo-net
sudo docker volume create bharatgo-mysql-data
```

If either already exists, inspect/reuse it rather than blindly deleting it.

## 17.7 Test on the EC2 machine itself

Run on the EC2 SSH terminal:

```bash
curl -i http://localhost:8080/
curl -i http://localhost:8080/api/trips
sudo docker ps
sudo docker logs --tail 100 bharatgo-frontend
sudo docker logs --tail 100 bharatgo-backend
sudo docker logs --tail 100 bharatgo-db
```

The `/api/trips` endpoint is an example; use the actual list endpoint implemented in your application.

**First prove the application works from EC2 itself.** Then test the public IP.

## 17.8 Open BHARATGO from your own computer

On your laptop/desktop browser, enter:

```text
http://EC2_PUBLIC_IP:8080
```

Replace `EC2_PUBLIC_IP` with the real public IPv4 address shown in the EC2 console. Do not type the literal words `EC2_PUBLIC_IP`.

Example format only: `http://203.0.113.25:8080`. This example IP is reserved for documentation and will not reach your instance.

### Successful result

- BHARATGO page opens from your own computer.
- The browser address uses your EC2 public IPv4 and port 8080.
- The trips list loads.
- Create/Edit/Delete works.
- API requests through `/api/` succeed.

### If public IP does not open

Check in this order:

1. EC2 instance is running and status checks passed.
2. A public IPv4 address is assigned.
3. Security group allows inbound TCP 8080 from your current public IP.
4. The subnet route table has a route to an Internet Gateway.
5. The network ACL allows the required traffic.
6. On EC2, `sudo docker ps` shows the frontend container running.
7. `sudo docker port bharatgo-frontend` shows host port 8080 mapped to container port 80.
8. `curl -i http://localhost:8080/` works on EC2.
9. Inspect Nginx/backend/database logs.
10. Check disk and memory if containers exit:

```bash
free -h
df -h
sudo docker stats
```

Do not open MySQL or the backend directly to the internet to fix a frontend problem.

> **Security:** This guide uses HTTP on port 8080. It is not encrypted. For a real public production deployment, configure a domain and HTTPS, harden access, use a secret manager, and establish database backups.

---

# STEP 18 — Stop, restart, and clean up

### Restart containers

```bash
docker restart bharatgo-db
docker restart bharatgo-backend
docker restart bharatgo-frontend
```

After restarting MySQL, wait for it to become healthy and verify backend readiness before testing the site.

### Stop containers without deleting them

```bash
docker stop bharatgo-frontend bharatgo-backend bharatgo-db
```

### Start existing containers

```bash
docker start bharatgo-db
docker start bharatgo-backend
docker start bharatgo-frontend
```

Wait for MySQL/backend readiness before opening the site.

### Remove containers but keep database data

```bash
docker rm -f bharatgo-frontend bharatgo-backend bharatgo-db
```

This does not remove the named volume.

### Optional full reset — permanent database data loss

Only if you intentionally want to delete every saved trip:

```bash
docker volume rm bharatgo-mysql-data
```

Do not run this command if you want to preserve data.

---

# STEP 19 — Common errors

| Error | Likely reason | What to do |
|---|---|---|
| `docker: command not found` | Docker is not installed or not in PATH | Install Docker and verify `docker --version` |
| Cannot connect to Docker daemon | Engine is stopped or permission denied | Start Docker; use `sudo docker` on Linux if appropriate |
| Dockerfile not found | Wrong build context/path | Return to STEP 3 and inspect the repository structure |
| Port already allocated | Host port 8080 is in use | Choose another host port such as `8090:80`; update the browser URL and EC2 rule |
| Backend cannot connect to MySQL | Password/hostname/network/readiness issue | Check DB variables, container logs, and `docker network inspect bharatgo-net` |
| Website loads but API fails | Nginx upstream or backend/database issue | Check Nginx proxy target and backend logs |
| SQL table missing | Initialization scripts not mounted or volume already initialized | Verify SQL paths and use a migration/reset only when appropriate |
| Public IP times out | Security group, route, public IP, or port mapping issue | Follow STEP 17.8 |
| Container name already exists | Old container remains | Inspect with `docker ps -a`; remove only the intended old container |
| Container exits due to memory | Instance is too small | Inspect `free -h`, `df -h`, and `docker stats`; consider a larger instance |

---

# STEP 20 — Final success checklist

Tick each item only after you have actually observed the result.

- [ ] Cloned the exact GitHub repository successfully.
- [ ] Confirmed the actual project structure and Dockerfile paths.
- [ ] Docker Engine is running.
- [ ] Backend image built successfully.
- [ ] Frontend image built successfully.
- [ ] MySQL 8.4 image is available.
- [ ] Private Docker network exists.
- [ ] Persistent MySQL volume exists.
- [ ] MySQL container is healthy.
- [ ] Database and expected tables exist.
- [ ] Java 21 backend container is running.
- [ ] Backend can connect to MySQL.
- [ ] Nginx frontend container is running.
- [ ] Website loads at `http://localhost:8080`.
- [ ] API endpoints return successful responses.
- [ ] Create, Read, Update, and Delete workflows work.
- [ ] Database data survives MySQL container recreation using the same volume.
- [ ] On EC2, TCP 8080 is allowed only from the intended source.
- [ ] Website loads from my computer at `http://EC2_PUBLIC_IP:8080`.
- [ ] MySQL port 3306 is not publicly exposed.
- [ ] I have not deleted the database volume unless I intended to reset all data.

**Completion rule:** Do not mark the practical successful just because commands were entered. Confirm the expected outputs, website behavior, API response, and persistence test.

---

## Quick command reference

```bash
# Clone
git clone https://github.com/chetanraval07/project-BHARATGO-.git
cd project-BHARATGO-

# Build
docker build -t bharatgo-backend:1.0 ./backend
docker build -t bharatgo-frontend:1.0 ./frontend
docker pull mysql:8.4

# Network and volume
docker network create bharatgo-net
docker volume create bharatgo-mysql-data

# Inspect
docker images
docker ps -a
docker network inspect bharatgo-net
docker volume inspect bharatgo-mysql-data

# Logs
docker logs --tail 100 bharatgo-db
docker logs --tail 100 bharatgo-backend
docker logs --tail 100 bharatgo-frontend

# Website and API (run on the Docker host)
curl -i http://localhost:8080/
curl -i http://localhost:8080/api/trips

# Stop/remove containers while preserving database volume
docker rm -f bharatgo-frontend bharatgo-backend bharatgo-db
```

**End of BHARATGO Docker practical.**
