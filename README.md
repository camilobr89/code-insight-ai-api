# Code Insight AI — API

API REST (Java 21 + Spring Boot 3 + PostgreSQL) del reto **Code Insight AI: Ingeniería
inversa automatizada de repositorios**. Recibe una URL de repositorio Git e infiere de
forma heurística el lenguaje, framework, arquitectura y hallazgos del proyecto.

## Arquitectura

Arquitectura en capas (N-Capas / MVC):

```
web (controllers + DTOs + manejo de errores)
        │
service (lógica de inferencia — Code Insight)
        │
repository (Spring Data JPA)
        │
domain (entidad Analysis)  ──►  PostgreSQL
```

- **`web/`** — `AnalysisController`, DTOs (`AnalyzeRequest`, `AnalysisResponse`) y
  `GlobalExceptionHandler` (manejo centralizado de errores).
- **`service/`** — `AnalysisService`, motor de inferencia (función pura + persistencia).
- **`repository/`** — `AnalysisRepository` (Spring Data JPA).
- **`domain/`** — entidad `Analysis`.
- **`config/`** — CORS.

## Tecnologías

Java 21 · Spring Boot 3.3 · Spring Web · Spring Data JPA · Bean Validation · Actuator ·
PostgreSQL · H2 (tests) · JaCoCo · Maven.

## Endpoints

| Método | Ruta                  | Descripción                              |
|--------|-----------------------|------------------------------------------|
| POST   | `/api/analyses`       | Analiza un repo `{ "repoUrl": "..." }`   |
| GET    | `/api/analyses`       | Historial de análisis                    |
| GET    | `/api/analyses/{id}`  | Detalle de un análisis                   |
| GET    | `/actuator/health`    | Health check (usado por ECS)             |

## Ejecución local

Con Docker (API + PostgreSQL):

```bash
docker compose up --build
# API en http://localhost:8080
curl -X POST http://localhost:8080/api/analyses \
  -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/spring-projects/spring-petclinic"}'
```

Sólo la app (requiere un PostgreSQL local o variables apuntando a uno):

```bash
mvn spring-boot:run
```

Pruebas + cobertura (JaCoCo, usa H2, no requiere PostgreSQL):

```bash
mvn clean verify
# Reporte: target/site/jacoco/index.html
```

## Variables de entorno

| Variable                      | Default (local)                                   |
|-------------------------------|---------------------------------------------------|
| `SPRING_DATASOURCE_URL`       | `jdbc:postgresql://localhost:5432/codeinsight`    |
| `SPRING_DATASOURCE_USERNAME`  | `codeinsight`                                      |
| `SPRING_DATASOURCE_PASSWORD`  | `codeinsight`                                      |
| `APP_CORS_ALLOWED_ORIGINS`    | `*`                                                |
| `SERVER_PORT`                 | `8080`                                             |

## CI/CD

`.github/workflows/ci-cd.yml` invoca el reusable
`my-banking-app/ci-templates/.github/workflows/aws-java-ecs-ci-cd.yml`:

1. **En cada PR a `main`**: build + tests + análisis SonarCloud + Quality Gate.
2. **En push/merge a `main`**: build de imagen → Amazon ECR → despliegue en
   **Amazon ECS Fargate**; la base de datos es **Amazon RDS (PostgreSQL)**.

La configuración de AWS/GitHub está documentada en el repo `ci-templates`.

## Supuestos

- El "análisis" del MVP es heurístico a partir de la URL (no clona el repositorio);
  la arquitectura de CI/CD y despliegue es el foco del reto.
- El esquema de base de datos se crea automáticamente (`ddl-auto=update`).
- No se versionan credenciales: en producción la contraseña de la BD se lee desde
  AWS SSM Parameter Store (ver `task-definition.json`).
