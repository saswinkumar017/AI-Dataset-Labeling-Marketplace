# PROJECT.md — LabelMate

## 1. Project Name

**LabelMate — AI Dataset Labeling Marketplace**

## 2. Project Purpose

To make high-quality dataset creation accessible, efficient, and trustworthy by combining human expertise with assistive AI in a structured labeling workflow.

## 3. Product Description

LabelMate is a platform for creating, managing, annotating, reviewing, and exporting datasets through a collaborative labeling workflow.

Dataset Owners upload raw data, create annotation projects with a label scheme and instructions, and generate annotation tasks. Work can be done by human annotators, assisted by AI suggestions, or both. Every annotation passes through human review before becoming a verified final label that can be exported.

AI accelerates repetitive work but never silently becomes the source of truth.

## 4. Primary Users / Actors

| Actor | Description |
|---|---|
| **Dataset Owner** | Uploads datasets, creates and manages annotation projects, defines labels and instructions, monitors progress, reviews annotations, and exports verified datasets. |
| **Annotator** | Views assigned or available tasks, reads instructions and AI suggestions, creates or corrects annotations, and submits them for review. |
| **Administrator** | Manages users and platform oversight at the platform level. Minimal scope in the initial phase. |

## 5. Core Workflow

```
Dataset
   ↓
Project
   ↓
Task
   ↓
Annotation
   ↓
AI Suggestion
   ↓
Human Review
   ↓
Final Label
   ↓
Export
```

Rules:

- A Dataset contains items that become Tasks.
- A Project configures annotation over a Dataset (owner, labels, instructions).
- A Task is a single unit of annotation work.
- An Annotation is the label produced for a Task, with source and review state.
- AI may produce a suggestion with confidence.
- Human Review approves, edits, or rejects. Only human-approved annotations become Final Labels.
- Export produces the labeled dataset.

Fundamental product rule: **AI is assistive, not authoritative. Human review is the source of truth.**

## 6. Main Capabilities (Initial Scope)

- Authentication and role distinction
- Dataset management (create, view, ownership)
- Annotation project management (link dataset, define labels and instructions)
- Task workflow (generate tasks per dataset item, assign, track status)
- Annotation workflow (create and submit annotation for a task)
- AI suggestion capability (pre-fill with confidence, retained as suggestion)
- Human review (approve / edit / reject, distinguish suggestion from final label)
- Export of verified annotations
- Progress visibility for projects and tasks
- REST API, validation, and server-side authorization

## 7. Initial Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot (Web, Data JPA, Security, Validation) |
| Database | MySQL 8 |
| Frontend | Next.js, React, TypeScript, Tailwind CSS, Axios |
| Authentication | Spring Security + JWT |
| Testing | JUnit 5, Mockito, MockMvc |
| AI | Spring AI with OpenRouter-compatible provider (isolated service layer) |
| Documentation | OpenAPI / Swagger where required |
| Build | Maven |
| DevOps (progressive) | Git, GitHub, GitHub Actions, Docker, Docker Compose |

Stack aligns with AGENTS.md. No unnecessary complexity (no microservices, CQRS, or Kubernetes at this stage).

## 8. High-Level Project Boundaries

**In scope for the capstone:**

- Structured dataset → project → task → annotation → review → export flow
- Ownership and server-side authorization
- AI assistance behind an isolated, replaceable service interface
- File handling treated as untrusted input with validation

**Out of scope for initial phase:**

- Training proprietary foundation models
- Large-scale distributed training or Kubernetes infrastructure
- Real-time collaborative editing of the same annotation
- Complex billing, cryptocurrency, or native mobile apps
- Supporting every dataset and annotation format on day one

Detailed architecture, database tables, and API contracts are defined in later commits, not in this context phase.

## 9. Current Project Phase

- **Day 2 — July 30, 2026** (planned milestone: problem and context definition)
- Status: Problem statement and project context are defined. Architecture documentation, database design, and implementation are intentionally deferred.
- Branch strategy: `main` is stable; development uses coherent feature branches.
- Next step: architecture documentation before backend initialization, per AGENTS.md documentation progression.
