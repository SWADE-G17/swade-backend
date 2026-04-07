# swade-backend

Spring Boot 3.4.1 backend for the SWADE MRI processing platform.

## Stack

- **Spring Boot 3.4.1** — Web, Security, JPA
- **Supabase Postgres** — Database (estudios, resultados, usuarios)
- **Supabase Auth** — JWT-based authentication (frontend-managed)
- **RabbitMQ** — Async MRI processing queue
- **MinIO** — Object storage for MRI NIfTI files
- **SpringDoc OpenAPI** — Swagger UI with Bearer token support

## Authentication Architecture

Authentication is handled **entirely in the frontend** using Supabase Auth.
The backend does **not** expose `/auth/login`, `/auth/logout`, or `/auth/change-password` endpoints.

### Frontend responsibilities

| Action          | How                                          |
|-----------------|----------------------------------------------|
| Login           | `supabase.auth.signInWithPassword()`         |
| Logout          | `supabase.auth.signOut()`                    |
| Change password | `supabase.auth.updateUser({ password })`     |
| API calls       | Send `Authorization: Bearer <access_token>`  |

### Backend responsibilities

| Concern              | Implementation                                          |
|----------------------|---------------------------------------------------------|
| Verify JWT           | Spring Security OAuth2 Resource Server (HS256)          |
| Identify user        | Read `sub` claim from JWT → UUID                        |
| Authorize access     | Each user can only see/modify their own studies         |
| Ownership violation  | Returns `404` (avoids leaking resource existence)       |

## Environment Variables

| Variable               | Description                                          | Example                                                            |
|------------------------|------------------------------------------------------|--------------------------------------------------------------------|
| `SUPABASE_DB_URL`      | JDBC connection string for Supabase Postgres         | `jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require` |
| `SUPABASE_DB_USERNAME` | Database username                                    | `postgres`                                                         |
| `SUPABASE_DB_PASSWORD` | Database password                                    | `your-db-password`                                                 |
| `SUPABASE_JWT_SECRET`  | JWT secret from Supabase Dashboard → Settings → API  | `your-jwt-secret`                                                  |
| `SUPABASE_JWT_ISSUER`  | JWT issuer URI                                       | `https://xxx.supabase.co/auth/v1`                                  |
| `MINIO_ENDPOINT`       | MinIO server URL                                     | `http://localhost:9000`                                            |
| `MINIO_ACCESS_KEY`     | MinIO access key                                     | `minio`                                                            |
| `MINIO_SECRET_KEY`     | MinIO secret key                                     | `minio123`                                                         |
| `MINIO_BUCKET_NAME`    | MinIO bucket for MRI files                           | `mri-files`                                                        |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed CORS origins         | `http://localhost:3000`                                            |

## Running

```bash
# Set environment variables (or use a .env file with your IDE)
export SUPABASE_DB_URL=jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require
export SUPABASE_DB_USERNAME=postgres
export SUPABASE_DB_PASSWORD=your-db-password
export SUPABASE_JWT_SECRET=your-jwt-secret
export SUPABASE_JWT_ISSUER=https://xxx.supabase.co/auth/v1

./mvnw spring-boot:run
```

## SQL Migration

Before starting the backend, run the migration in Supabase SQL Editor:

```
sql/2026-04-01_estudio_auth_integration.sql
```

## API Usage (with auth)

```bash
# Get a token from your frontend (Supabase Auth)
TOKEN="your-supabase-access-token"

# Create a study
curl -X POST http://localhost:8080/estudios \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@brain_scan.nii.gz"

# List your studies
curl http://localhost:8080/estudios \
  -H "Authorization: Bearer $TOKEN"

# Get study detail
curl http://localhost:8080/estudios/1 \
  -H "Authorization: Bearer $TOKEN"

# Get result (returns 409 if not completed yet)
curl http://localhost:8080/estudios/1/resultado \
  -H "Authorization: Bearer $TOKEN"

# Download heatmap
curl http://localhost:8080/estudios/1/resultado/heatmap \
  -H "Authorization: Bearer $TOKEN" -o heatmap.nii

# Download report PDF
curl http://localhost:8080/estudios/1/reporte \
  -H "Authorization: Bearer $TOKEN" -o report.pdf
```

## Swagger UI

Open `http://localhost:8080/swagger-ui.html`, click **Authorize**, and paste your Supabase `access_token` as the Bearer token.
