# Code Insight AI — API

API REST (Java 21 + Spring Boot 3 + PostgreSQL) del reto **Code Insight AI: Ingeniería
inversa automatizada de repositorios**. Recibe la URL de un repositorio de GitHub,
obtiene su metadata real (lenguajes, árbol de archivos, README) y usa OpenAI para
inferir su propósito, arquitectura, componentes, riesgos y recomendaciones. Si no hay
IA disponible, el repositorio no es de GitHub, es privado sin acceso, o la llamada a
la IA falla, cae automáticamente a un motor heurístico — la aplicación nunca se rompe.

## Arquitectura

Arquitectura en capas (N-Capas / MVC):

```
web (controllers + DTOs + manejo de errores)
        │
service (orquestación: caché → GitHub → IA → heurística de respaldo)
        │
        ├── github/  (GitHubRepoClient — metadata real del repo)
        ├── ai/      (OpenAiClient — inferencia estructurada)
        │
repository (Spring Data JPA)
        │
domain (entidad Analysis)  ──►  PostgreSQL
```

- **`web/`** — `AnalysisController`, DTOs (`AnalyzeRequest`, `AnalysisResponse`) y
  `GlobalExceptionHandler` (manejo centralizado de errores).
- **`service/`** — `AnalysisService`: si existe un análisis reciente para el mismo
  `repoUrl` lo reutiliza (caché en BD, evita llamadas repetidas y costosas a OpenAI);
  si no, obtiene un snapshot real del repositorio y lo analiza con IA, o cae a
  `infer()` (heurística basada en el texto de la URL) explicando la razón exacta.
- **`github/`** — `GitHubRepoClient`, cliente de solo lectura de la API pública de
  GitHub (metadata, lenguajes, árbol de archivos completo, README).
- **`ai/`** — `OpenAiClient`, llamada a OpenAI Chat Completions con
  `response_format: json_object` para forzar una salida estructurada.
- **`repository/`** — `AnalysisRepository` (Spring Data JPA).
- **`domain/`** — entidad `Analysis` (clave primaria `UUID`, generada con
  `@UuidGenerator(style = TIME)` — ordenable por tiempo, no expone volumen/orden de
  inserción como lo haría un id autoincremental).
- **`config/`** — `RestClientConfig` (construcción de los `RestClient` hacia GitHub y
  OpenAI, inyectable y testeable con `MockRestServiceServer`), `WebConfig` (CORS).

## Tecnologías

Java 21 · Spring Boot 3.3 · Spring Web · Spring Data JPA · Bean Validation · Actuator ·
PostgreSQL · H2 (tests) · JUnit 5 + Mockito + AssertJ · JaCoCo · Maven.

## Endpoints

| Método | Ruta                  | Descripción                                          |
|--------|-----------------------|-------------------------------------------------------|
| POST   | `/api/analyses`       | Analiza un repo `{ "repoUrl": "...", "forceRefresh": false }` |
| GET    | `/api/analyses`       | Historial de análisis (más reciente primero)          |
| GET    | `/api/analyses/{id}`  | Detalle de un análisis (`id` es un `UUID`)            |
| DELETE | `/api/analyses/{id}`  | Elimina un análisis puntual                           |
| DELETE | `/api/analyses`       | Vacía todo el historial                               |
| GET    | `/actuator/health`    | Health check (usado por el Load Balancer)             |

La respuesta incluye `source` (`AI` si el análisis se generó con OpenAI, `HEURISTIC`
si fue por fallback) y `cached` (si vino de un análisis previo guardado en BD).

## Ejecución local

Con Docker (API + PostgreSQL):

```bash
OPENAI_API_KEY=sk-... docker compose up --build
# API en http://localhost:8080 (sin la key, corre igual en modo heurístico)
curl -X POST http://localhost:8080/api/analyses \
  -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/spring-projects/spring-petclinic"}'
```

Sólo la app (requiere un PostgreSQL local o variables apuntando a uno):

```bash
mvn spring-boot:run
```

Pruebas + cobertura (JaCoCo, usa H2, no requiere PostgreSQL ni llamadas reales a
GitHub/OpenAI — los clientes HTTP se prueban con `MockRestServiceServer`):

```bash
mvn clean verify
# Reporte: target/site/jacoco/index.html
```

## Variables de entorno

| Variable                      | Requerida | Default (local)                                   |
|-------------------------------|-----------|------------------------------------------------------|
| `SPRING_DATASOURCE_URL`       | No        | `jdbc:postgresql://localhost:5432/codeinsight`       |
| `SPRING_DATASOURCE_USERNAME`  | No        | `codeinsight`                                        |
| `SPRING_DATASOURCE_PASSWORD`  | No        | `codeinsight`                                        |
| `APP_CORS_ALLOWED_ORIGINS`    | No        | `*`                                                  |
| `SERVER_PORT`                 | No        | `8080`                                               |
| `OPENAI_API_KEY`              | No        | — (sin ella, cae a modo heurístico automáticamente) |
| `OPENAI_MODEL`                | No        | `gpt-5.6-luna`                                       |
| `GITHUB_TOKEN`                | No        | — (sin él, límite de 60 req/hora a GitHub y sin acceso a repos privados) |

## CI/CD

`.github/workflows/ci-cd.yml` invoca el reusable
`my-banking-app/ci-templates/.github/workflows/aws-java-ecs-ci-cd.yml`:

1. **En cada PR a `main`**: build + tests + análisis SonarCloud + Quality Gate
   (cobertura mínima 80% en código nuevo).
2. **En push/merge a `main`**: build de imagen → Amazon ECR → despliegue en
   **Amazon ECS Fargate**, detrás de un **Application Load Balancer** (DNS fijo —
   la IP pública de una tarea Fargate es efímera y cambia en cada redeploy). La base
   de datos es **Amazon RDS (PostgreSQL)**.

La configuración de AWS/GitHub está documentada en el repo `ci-templates`.

## Supuestos

- El análisis usa OpenAI cuando está disponible y el repositorio es público (o
  privado con un token con acceso); si no, cae a una heurística explícita basada en
  el texto de la URL — nunca falla silenciosamente ni finge haber usado IA.
- El esquema de base de datos se crea/actualiza automáticamente
  (`spring.jpa.hibernate.ddl-auto=update`).
- No se versionan credenciales: la contraseña de la BD y las API keys se inyectan
  como secrets de GitHub Actions en el pipeline de despliegue (variables de entorno
  del contenedor), nunca se escriben en el repositorio.
- La entrada admitida es URL de repositorio Git (opción 1 del alcance mínimo); no se
  implementó la carga de archivo `.zip` (opción 2) — el enunciado pide una de las dos.
