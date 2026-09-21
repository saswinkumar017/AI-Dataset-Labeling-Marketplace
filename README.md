# LabelMate — AI Dataset Labeling Marketplace

A collaborative platform for creating, managing, annotating, reviewing, and exporting AI datasets.

## Stack

- Java + Spring Boot
- Next.js
- MySQL 8
- JWT authentication
- JUnit 5
- GitHub Actions

## Repository Structure

```
labelmate/
├── client/
├── server/
├── docs/
└── README.md
```

## Branches

- `main` is the stable branch.
- Development uses coherent feature branches.

## Environment Setup

Each app owns its environment. No secrets are hardcoded — secrets are
**required** and the app fails fast at startup when one is missing.

| Run mode | Files to create | Source |
| --- | --- | --- |
| Backend (local) | `server/.env` | copy `server/.env.example` |
| Frontend (local) | `client/.env.local` | copy `client/.env.example` |
| Docker Compose | `.env` (repo root) | copy `.env.example` |

```bash
cp server/.env.example server/.env
cp client/.env.example client/.env.local
cp .env.example .env
```

Then set the secrets (`DATABASE_PASSWORD`, `JWT_SECRET` — at least 32
characters, e.g. `openssl rand -base64 48`). The `*.example` files are
committed templates; the real `.env` files are gitignored and never
committed. Keep the shared values (database, JWT, AI) identical across
all three files.

`mvn spring-boot:run` (from `server/`) loads `server/.env` automatically;
missing secrets fail startup with a message telling you exactly which
file to create. Docker Compose reads the root `.env` instead.

## Running Locally (backend + MySQL)

```bash
# 1. start MySQL 8 on localhost:3306 (from the repo root)
docker compose up -d mysql

# 2. run the backend (from server/) — needs the DB above
cd server && mvn spring-boot:run  # :8080
```

`server/.env` already points at the compose database
(`labelmate` user on `localhost:3306`); keep those values identical to
the root `.env` (`DB_*`). If you use your own MySQL instead, create the
`labelmate` database/user yourself or adjust `server/.env` to match.
