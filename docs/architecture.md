# Architecture — LabelMate

> **Status:** Day 3 — July 31, 2026 · Initial architecture foundation
> **Stack:** Java 21 · Spring Boot · MySQL 8 · Next.js · Spring AI
> **Rule:** AI is assistive, not authoritative — human review is the source of truth.

---

## 1. Architecture Overview

LabelMate is a **monolithic Spring Boot backend + Next.js frontend + MySQL** product that implements a single workflow:

```
Dataset → Project → Task → Annotation → AI Suggestion → Human Review → Final Label → Export
```

The backend owns all business rules, authentication, file validation, and AI orchestration. The frontend is a thin React UI that calls the backend REST API. The AI layer is isolated behind a replaceable service interface so the provider can change without touching core annotation logic.

Design goals for this milestone: **understandable, secure, testable** — no premature microservices, CQRS, or Kubernetes.

```mermaid
flowchart LR
    U[User] --> F[Next.js Frontend<br/>React + TS + Tailwind]
    F -- "REST / JSON<br/>JWT Bearer" --> B[Spring Boot Backend<br/>Java 21]
    B --> DB[(MySQL 8<br/>JPA / Hibernate)]
    B --> AI[AI Service Layer<br/>Spring AI]
    AI --> P[AI Provider<br/>OpenRouter-compatible]
    B --> FS[(Uploads /<br/>Object Storage)]
    style B fill:#1e1b4b,stroke:#818cf8,color:#eef2ff
    style F fill:#0f172a,stroke:#34d399,color:#d1fae5
    style DB fill:#78350f,stroke:#f59e0b,color:#fef3c7
    style AI fill:#312e81,stroke:#c084fc,color:#f5f3ff
```

`*` Local filesystem in development; replaceable with cloud object storage later.

---

## 2. System Context

Actors and system boundaries. The backend is the only component that talks to the database and the AI provider.

```mermaid
flowchart TB
    User([User<br/>Dataset Owner / Annotator / Admin])

    subgraph Frontend[Next.js Frontend]
        Pages[Pages & Components]
        ApiClient[API Client<br/>Axios]
    end

    subgraph Backend[Spring Boot Backend]
        Auth[Authentication<br/>Spring Security + JWT]
        Dataset[Dataset Management]
        Project[Project Management]
        Task[Task Management]
        Annotation[Annotation / Review]
        AIService[AI Service<br/>Isolated]
        SpringAI[Spring AI]
    end

    AIProvider[(AI Provider<br/>OpenRouter)]
    MySQL[(MySQL 8<br/>Spring Data JPA)]

    User --> Pages
    Pages --> ApiClient
    ApiClient -- "REST: /api/**<br/>JWT" --> Auth
    Auth --> Dataset
    Auth --> Project
    Auth --> Task
    Auth --> Annotation
    Annotation --> AIService
    AIService --> SpringAI --> AIProvider
    Dataset --> MySQL
    Project --> MySQL
    Task --> MySQL
    Annotation --> MySQL

    style Backend fill:#111a33,stroke:#6366f1,color:#eef2ff
    style Frontend fill:#0f172a,stroke:#34d399,color:#d1fae5
    style MySQL fill:#78350f,stroke:#f59e0b,color:#fef3c7
    style AIProvider fill:#312e81,stroke:#c084fc,color:#f5f3ff
```

**Boundaries:**
- Frontend never talks directly to MySQL or the AI provider.
- Every request passes the authentication boundary.
- All persistence goes through `repository` → MySQL.
- All AI calls go through `AI Service` → `Spring AI` → provider.

---

## 3. High-Level Component Architecture

Backend package structure per AGENTS.md. Frontend keeps API calls separate from presentation.

```mermaid
flowchart TB
    subgraph Client[client/ — Next.js]
        PG[Pages / Components]
        AC[API Client<br/>centralized Axios]
        CTX[AuthContext<br/>JWT storage]
    end

    subgraph Server[server/ — com.labelmate.labelmate]
        CTRL[controller<br/>HTTP boundary]
        DTO[dto<br/>Request / Response]
        SVC[service<br/>business logic]
        SEC[security<br/>JWT filter, config]
        CFG[config<br/>CORS, OpenAPI]
        REP[repository<br/>Spring Data JPA]
        MOD[model<br/>JPA entities]
        EXC[exception<br/>RestControllerAdvice]
        AI2[AI Service<br/>AiSuggestionService]
    end

    DB[(MySQL 8)]
    PROV[(AI Provider)]

    PG --> AC --> CTRL
    CTX -. token .-> AC
    CTRL --> DTO --> SVC
    SVC --> REP --> MOD --> DB
    SVC --> SEC
    SVC --> AI2 --> PROV
    CTRL --> EXC
    CFG -. cross-cutting .-> CTRL

    style Server fill:#1e1b4b,stroke:#818cf8,color:#eef2ff
    style Client fill:#0f172a,stroke:#34d399,color:#d1fae5
```

**Request flow (preferred):**
```
HTTP Request → Controller → DTO + Validation → Service → Repository → Database → Service → DTO → Controller → HTTP Response
```
Controllers never touch repositories. Repositories never contain business rules.

---

## 4. Core Labeling Workflow

The product workflow is a state machine. Task status transitions are deliberate, and the final label is always human-approved.

```mermaid
stateDiagram-v2
    [*] --> Dataset: Upload
    Dataset --> Project: Create project<br/>link dataset
    Project --> Task: Generate tasks<br/>1 per item
    Task --> Annotation: Annotate
    Annotation --> AI_Suggestion: Optional<br/>pre-fill + confidence
    AI_Suggestion --> Annotation: Human corrects<br/>or accepts
    Annotation --> Review: Submit
    Review --> Approved: Human approves
    Review --> Rejected: Human rejects<br/>or requests edit
    Rejected --> Annotation: Re-annotate
    Approved --> FinalLabel: Verified
    FinalLabel --> Export: Export dataset
    Export --> [*]

    note right of AI_Suggestion
      AI suggestion is stored
      with confidence + source.
      Never auto-approves.
    end note
```

```mermaid
flowchart LR
    DS[Dataset<br/>owner, items] --> PR[Project<br/>dataset + labels + instructions]
    PR --> TK[Task<br/>item + status]
    TK --> AN[Annotation<br/>label + source + confidence]
    AN --> AI[AI Suggestion<br/>label + confidence<br/>source=AI]
    AI -. "pre-fill" .-> AN
    AN --> RV{ Human Review }
    RV -->|Approve| FL[Final Label<br/>verified]
    RV -->|Reject / Edit| AN
    FL --> EX[Export<br/>JSON / CSV]

    style RV fill:#111a33,stroke:#f59e0b,color:#fef3c7
    style FL fill:#78350f,stroke:#f59e0b,color:#fef3c7
```

**State rules:**
- No arbitrary status jumps — service checks current status before transition.
- `HUMAN`, `AI`, and `HUMAN_APPROVED` are distinguishable in storage (source + review state + confidence retained).

---

## 5. Authentication Boundary

Stateless JWT. The filter runs before any controller logic. Server-side authorization enforces ownership.

```mermaid
sequenceDiagram
    participant U as User
    participant F as Next.js<br/>Axios
    participant S as Security Filter<br/>JwtAuthFilter
    participant C as Controller
    participant SV as Service
    participant R as Repository

    U->>F: Login (email, password)
    F->>S: POST /api/auth/login
    S->>SV: AuthService authenticates
    SV->>R: findByEmail
    R-->>SV: User + BCrypt hash
    SV-->>S: Verify password
    S-->>F: JWT (signed, exp)
    F->>F: Store token<br/>in memory / storage

    U->>F: GET /api/datasets/42
    F->>S: GET /api/datasets/42<br/>Authorization: Bearer JWT
    S->>S: Validate signature + expiry<br/>Load user → SecurityContext
    alt valid
        S->>C: Authenticated request<br/>(user in context)
        C->>SV: findDatasetById(42, currentUser)
        SV->>R: findByIdAndOwner
        R-->>SV: owned? yes
        SV-->>C: DatasetResponse DTO
        C-->>F: 200 OK + data
    else invalid / expired
        S-->>F: 401 Unauthorized
    end
```

```mermaid
flowchart TB
    REQ[Request] --> FILTER[JwtAuthFilter<br/>OncePerRequestFilter]
    FILTER --> VALID{Token valid?}
    VALID -->|Yes| CTX[Set SecurityContext<br/>Authentication]
    VALID -->|No| CTX2[Context empty<br/>→ 401]
    CTX --> SEC["SecurityConfig<br/>permit /api/auth/**<br/>otherwise authenticated"]
    CTX2 --> SEC
    SEC --> CTRL[Controller<br/>reads current user]
    CTRL --> SVC[Service<br/>ownership check]

    style FILTER fill:#312e81,stroke:#c084fc,color:#f5f3ff
    style SEC fill:#1e1b4b,stroke:#818cf8,color:#eef2ff
```

**Rules:**
- Passwords are BCrypt-hashed, never stored or logged in plaintext.
- `JWT_SECRET`, `OPENROUTER_API_KEY`, `DATABASE_PASSWORD` come from environment — never committed.
- Frontend guards are UX only. Authorization is server-side on every service method (e.g., `validateDatasetOwnership`).

---

## 6. AI Integration Boundary

AI is a replaceable module. Direct provider calls are forbidden outside the AI service.

```mermaid
flowchart TB
    subgraph App[Application Layer]
        CTR[AnnotationController]
        ANS[AnnotationService]
        AIS[AiSuggestionService<br/>interface + impl]
    end

    subgraph AIInfra[AI Infrastructure]
        SAI[Spring AI<br/>ChatClient / Provider]
        CFG2[AI Config<br/>endpoint, key, model]
    end

    PROV2[(AI Provider<br/>OpenRouter)]

    CTR --> ANS --> AIS --> SAI --> CFG2 --> PROV2
    SAI -. replaceable .-> PROV2

    style AIS fill:#312e81,stroke:#c084fc,color:#f5f3ff
    style SAI fill:#1e1b4b,stroke:#818cf8,color:#eef2ff
```

```mermaid
sequenceDiagram
    participant C as Controller
    participant S as AnnotationService
    participant AI as AiSuggestionService
    participant SA as Spring AI
    participant P as AI Provider

    C->>S: generateSuggestion(taskId)
    S->>AI: suggestLabel(itemContent, labelScheme)
    AI->>SA: prompt + context<br/>(provider-agnostic)
    SA->>P: Model call<br/>(OpenRouter API)
    P-->>SA: label + confidence + metadata
    SA-->>AI: Suggestion DTO
    AI-->>S: Stored as<br/>AI suggestion<br/>(not final)
    S-->>C: SuggestionResponse<br/>with confidence

    Note over S,P: Failure never blocks core flow.<br/>If AI is down, task remains<br/>available for manual annotation.
```

**Safety rules:**
- Suggestion retains `source=AI`, `confidence`, and `reviewState=PENDING`.
- A new AI result never overwrites a `HUMAN_APPROVED` label.
- The provider is configured via environment; swapping it requires only changing `AI Service` + config.

---

## 7. Data Flow

Uploads are untrusted and validated. Every flow returns DTOs, never entities.

```mermaid
flowchart LR
    subgraph Ingest[Dataset Ingest]
        UL[Upload File] --> VAL[Validate<br/>type, size, name<br/>no path traversal]
        VAL --> STO[Store<br/>sanitized path<br/>checksum]
        STO --> META[Create Dataset<br/>metadata + owner]
        META --> ITEM[Generate Items<br/>1 per record/image]
        ITEM --> TASK2[Generate Tasks<br/>status=PENDING]
    end

    subgraph Label[Labeling]
        TASK2 --> ASSIGN[Assign Task]
        ASSIGN --> ANNOT[Human Annotation]
        ANNOT --> SUGG[AI Suggestion<br/>optional]
        SUGG --> REV[Human Review<br/>approve / edit / reject]
        REV --> VER[Verified Annotation]
    end

    VER --> PROG[Progress<br/>counts]
    VER --> EXP2[Export<br/>JSON / CSV]

    style VAL fill:#78350f,stroke:#f59e0b,color:#fef3c7
    style REV fill:#111a33,stroke:#f59e0b,color:#fef3c7
```

**Upload validation (per File Upload Rules):**
- Allowed extensions and MIME types, size limits, filename sanitization, path-traversal guard (`targetPath.startsWith(uploadDir)`), ownership check, and no direct use of the original filename for storage.

---

## 8. Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| Backend | Java 21, Spring Boot (Web, Data JPA, Security, Validation) | REST API, business logic, validation |
| Database | MySQL 8, Hibernate / Spring Data JPA | Persistence, FK constraints |
| Frontend | Next.js, React, TypeScript, Tailwind CSS | UI, type-safe pages |
| HTTP | Axios (centralized client) | API calls from frontend |
| Auth | Spring Security, BCrypt, JWT | Stateless authentication |
| AI | Spring AI, OpenRouter-compatible | Replaceable AI suggestions |
| Docs | OpenAPI / Swagger | API documentation when required |
| Build | Maven | Backend build |
| Testing | JUnit 5, Mockito, MockMvc | Service + controller tests |
| DevOps (progressive) | Git, GitHub, GitHub Actions, Docker, Docker Compose | CI, containerization, deployment later |

All choices are standard, explainable to a Semester-5 student. No microservices, CQRS, event sourcing, or Kubernetes at this stage.

---

## 9. Architectural Responsibilities

Backend packages (`com.labelmate.labelmate`):

| Package | Owns | Does not own |
|---|---|---|
| `config` | CORS, OpenAPI, bean configuration | Business logic |
| `controller` | HTTP boundary, DTO binding, validation trigger, call service | Queries, AI calls, large validation |
| `dto` | `LoginRequest`, `DatasetResponse`, `AnnotationRequest` etc. | Sensitive entity fields |
| `exception` | Custom exceptions, `@RestControllerAdvice`, safe error responses | Stack traces to clients |
| `model` | JPA entities: `User`, `Dataset`, `Project`, `Task`, `Annotation`, `Review` | API formatting |
| `repository` | `JpaRepository` interfaces, persistence only | Workflow rules |
| `security` | JWT issue/validate, filter, password encoder, current-user resolution | Scattered auth checks |
| `service` | Validation, ownership, state transitions, AI orchestration, transactions | HTTP details |

Frontend (`client/`):

| Layer | Responsibility |
|---|---|
| `Page / Component` | Render UI, call API client |
| `API Client` | Centralized base URL, JWT header, response unwrapping |
| `Spring Boot API` | Source of truth — no fake data after backend is connected |

Cross-cutting: validation at the API boundary (Jakarta Bean Validation on DTOs, backend is authoritative), centralized error handling, environment-based secrets.

---

## 10. Initial Architecture Decisions

| Decision | Choice | Why | Trade-off |
|---|---|---|---|
| Backend style | Monolithic Spring Boot | Simplest to understand, test, and deploy for a solo student; one codebase, one DB, one deployment | Not independently scalable per domain — acceptable until proven bottleneck |
| Database | MySQL 8 + JPA | Relational, matches domain relationships (Dataset→Project→Task→Annotation), familiar tooling | Requires careful schema design; no document-store flexibility at this stage |
| Auth | Stateless JWT + BCrypt | Stateless, no server session, works with REST and Next.js; BCrypt is standard | Token revocation needs extra handling if required later |
| Package layout | `config, controller, dto, exception, model, repository, security, service` | Conventional, matches Spring guides, maps cleanly to test layers | Avoids over-abstracted layers (`helper`, `manager`) |
| Validation | Jakarta Bean Validation at DTO boundary | Frontend validation is UX only; backend is authoritative | Duplicate validation messages if not coordinated |
| AI | Isolated `AiSuggestionService` → Spring AI → provider | Core app works without AI; provider is replaceable without touching controllers | Extra interface is overhead until AI feature ships — justified as boundary |
| File storage | Local `uploads/` now, abstraction later | Fast for MVP, path-traversal guarded, not committed to Git | Needs cloud object storage before large-scale datasets |
| Testing | JUnit 5 + Mockito + MockMvc | Service mocks repos, controller tests verify HTTP + auth | Integration tests deferred until DB is present |
| DTOs | Not exposing entities | Prevents password leakage, circular JSON, over-fetching | Mapping code is required |
| DI | Constructor injection (`@RequiredArgsConstructor`) | Immutable, visible dependencies, testable | Field injection is forbidden |

All decisions favor clarity over cleverness and keep the system teachable.

---

## 11. Current Scope and Future Scope

### Current scope (Day 3 — architecture foundation)

- Problem and project context defined (commits 01–02).
- Architecture document (`docs/architecture.md`) with System Context, component, workflow, auth, AI, and data-flow views.
- No source code, no database schema, no AI implementation, no Docker/CI yet.

### Next scope (per capstone progression)

- Backend initialization (`chore(backend): initialize spring boot application`)
- Database configuration (MySQL, JPA entities, migrations)
- Authentication foundation (User, JWT, security filter)
- Dataset → Project → Task → Annotation workflow, iteratively, with tests

### Future scope (not in this commit)

- ER diagram and class/module diagram that match the implemented schema.
- API documentation via OpenAPI/Swagger.
- Containerization (Docker, Compose) and CI/CD (GitHub Actions).
- Deployment, health checks, and logging.
- AI suggestions in production with confidence and review enforcement.
- Dataset export and progress tracking.

This document will be updated when implementation diverges from the diagram. Diagram ≠ Code is not allowed.
