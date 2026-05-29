# Prompts: Planejar Arquitetura e Documentar Decisões

Prompt 1 — Planejar arquitetura com IA (camadas, DB, locks)
---
Contexto: Projeto Spring Boot com regras de concorrência e timezone específicas (Pessimistic locking, UTC storage).

Instrução para o assistente IA:
- Gere um checklist detalhado e um diagrama textual (texto/ASCII) da arquitetura em camadas: controller → service → repository → entity; banco PostgreSQL, H2 para testes; regras de locking (`SELECT ... FOR UPDATE NOWAIT`), tempo em UTC, conversão para `America/Sao_Paulo` na apresentação. Indique onde registrar ADRs em `docs/adr/` e quais decisões já existem (`0001–0005`).

Saída esperada:
- Checklist e diagrama textual pronto para `README.md` e `docs/adr/`.

---

Prompt 2 — Documentar decisões arquiteturais no README e ADRs
---
Contexto: As ADRs existentes cobrem locking, capacity split, timezone, snapshot eligibility, scope.

Instrução para o assistente IA:
- Forneça instrução para resumir as ADRs existentes em um parágrafo de 4–6 linhas no `README.md` e um template para adicionar novas ADRs em `docs/adr/` seguindo o formato `NNNN-name.md` com seções Context, Decision, Consequences.

Saída esperada:
- Texto resumo para README e template ADR preenchível.
