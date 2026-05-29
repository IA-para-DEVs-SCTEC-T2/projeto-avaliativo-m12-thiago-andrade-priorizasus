# Prompts: Definição do Domínio e Escopo

Prompt 1 — Documentar domínio do PRIORIZASUS no `README.md`
---
Contexto: É necessário definir e documentar as entidades canônicas e escopo no README do projeto.

Instrução para o assistente IA:
- Gere um texto de 3–6 parágrafos para inserir no `README.md` que explique: propósito do PRIORIZASUS, principais entidades (`Patient`, `Category`, `Slot`, `Selection`, `Appointment`), regras de negócio críticas (1 Appointment por semana, Score formula, semanas/`weekStart`), e link para `CONTEXT.md` e `.specs/`.

Saída esperada:
- Bloco pronto para colar no `README.md`, em português, com referências aos REQ-IDs das specs.

---

Prompt 2 — Definir escopo Phase 1
---
Contexto: Phase 1 tem features limitadas (ver `.specs/` e `docs/adr/0004`).

Instrução para o assistente IA:
- Produza um parágrafo que liste o que está dentro do Phase 1 (patient-master, capacity-model, scoring-algorithm, booking-system, staff-dashboard) e o que está explicitamente fora (home visits, no-show penalties, notificações, multi-user auth, mobile app). Inclua rationale curto.

Saída esperada:
- Texto pronto para README ou `PROJECT.md`.
