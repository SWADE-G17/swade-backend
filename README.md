# SWADE: Backend Service

Este repositorio contiene la API y los servicios de backend para el proyecto SWADE, proporcionando la infraestructura lógica, la gestión de datos y la integración con los modelos de aprendizaje profundo para el soporte en el diagnóstico de la enfermedad de Alzheimer.

El componente se encarga de centralizar las peticiones de la interfaz de usuario, coordinar el flujo de procesamiento de imágenes y asegurar la persistencia de la información.

## Características principales

* **Arquitectura de API:** Implementación de servicios web estructurados para gestionar las peticiones de los clientes, asegurando transferencias de datos eficientes y manejo controlado de errores.
* **Gestión y persistencia de datos:** Integración con sistemas de bases de datos relacionales para el almacenamiento, consulta y administración del historial de evaluaciones y datos del sistema.
* **Orquestación del pipeline médico:** Control del flujo de trabajo que recibe los archivos de neuroimagen, gestiona su almacenamiento temporal y coordina la comunicación con el módulo de inferencia del modelo.
* **Seguridad y autenticación:** Incorporación de mecanismos para el control de acceso, verificación de identidad y protección de las rutas y recursos de la aplicación.

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
| `MINIO_HEATMAP_BUCKET` | MinIO bucket where the Python worker stores `<id>_heatmap.nii.gz` and `<id>_orig.mgz` (must match the worker's value) | `heatmaps`                                                         |
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

## SQL Migrations

Before starting the backend, run the migrations in Supabase SQL Editor in chronological order:

```
sql/2026-04-01_estudio_auth_integration.sql
sql/2026-04-29_resultado_orig_path.sql
```

The `2026-04-29` migration adds the `orig_path` column to `resultado`, populated by the
Python worker with the path to the FastSurfer-conformed T1 (`<id>_orig.mgz`) so the backend
can stream it as the Niivue base image alongside the Grad-CAM overlay.

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
# Response: { prediction, heatmapUrl, origUrl, reportPath }
# heatmapUrl/origUrl are absolute URLs that point at the proxy endpoints below.
curl http://localhost:8080/estudios/1/resultado \
  -H "Authorization: Bearer $TOKEN"

# Stream Grad-CAM heatmap (.nii.gz) — proxied from MinIO, application/gzip
curl http://localhost:8080/estudios/1/heatmap \
  -H "Authorization: Bearer $TOKEN" -o heatmap.nii.gz

# Stream FastSurfer-conformed T1 (.mgz) — proxied from MinIO, application/gzip
curl http://localhost:8080/estudios/1/orig \
  -H "Authorization: Bearer $TOKEN" -o orig.mgz

# Download report PDF
curl http://localhost:8080/estudios/1/reporte \
  -H "Authorization: Bearer $TOKEN" -o report.pdf
```

### Niivue integration

The frontend never talks to MinIO directly — it consumes the `heatmapUrl` / `origUrl`
URLs returned by `GET /estudios/{id}/resultado`. Both URLs hit the backend, which
re-checks the user's ownership of the study and streams the volume from MinIO with
`Content-Type: application/gzip`:

```js
const r = await fetch(`/estudios/${id}/resultado`, { headers: authHeader });
const { heatmapUrl, origUrl } = await r.json();
nv.loadVolumes([
  { url: origUrl,    headers: authHeader },
  { url: heatmapUrl, headers: authHeader,
    colormap: "warm", opacity: 0.5, cal_min: 0, cal_max: 1 }
]);
```

### Volume streaming: Range + ETag

Both `/estudios/{id}/heatmap` and `/estudios/{id}/orig` advertise:

| Header           | Value example                       | Meaning                                                  |
|------------------|-------------------------------------|----------------------------------------------------------|
| `Accept-Ranges`  | `bytes`                             | The endpoint serves byte ranges.                         |
| `ETag`           | `"abc123…"` (MinIO MD5 / multipart) | Identifies the exact stored object version.              |
| `Cache-Control`  | `private, max-age=86400, immutable` | Safe to cache per-user for 24h; objects never mutate.    |

The endpoints honor:

- `Range: bytes=<start>-<end>` / `bytes=<start>-` / `bytes=-<suffix>` → `206 Partial Content`
  with `Content-Range: bytes <start>-<end>/<total>`. Niivue uses this for progressive
  loading: the volume header arrives first and the rest streams in the background.
- `If-None-Match: "<etag>"` (or `*`) → `304 Not Modified` with no body when the cached
  copy in the browser still matches. Re-opening the same study costs nothing.
- An out-of-range or malformed `Range` → `416 Range Not Satisfiable` with
  `Content-Range: bytes */<total>`.

## Swagger UI

Open `http://localhost:8080/swagger-ui.html`, click **Authorize**, and paste your Supabase `access_token` as the Bearer token.
