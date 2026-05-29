# Prompts: Quadro Kanban e Cards

Prompt 1 — Criar quadro Kanban no GitHub com 6 colunas obrigatórias
---
Contexto: O curso exige um quadro Kanban com 6 colunas padrão para acompanhamento do desenvolvimento.

Instrução para o assistente IA:
- Forneça instruções passo a passo (UI e via API/CLI se aplicável) para criar um Project no GitHub com as 6 colunas: Backlog, To Do, In Progress, In Review, Done, Done/Release (ou equivalente). Inclua sugestões de automação básica (mover cards em PR merge) e como conectar issues/pull requests ao quadro.

Saída esperada:
- Lista de ações e trechos de API/gh-cli para criar o projeto e colunas.

---

Prompt 2 — Criar cards iniciais com descrição clara para cada etapa
---
Contexto: É necessário criar cards (`issues` ou `project notes`) para cada etapa de desenvolvimento das features Phase 1.

Instrução para o assistente IA:
- Gere templates para 6–10 issues iniciais cobrindo: inicialização do projeto, implementar `patient-master`, implementar `capacity-model`, implementar `scoring-algorithm`, implementar `booking-system`, implementar `staff-dashboard`, testes, documentação, CI/CD. Cada template deve ter: título, descrição, checklist de subtarefas, labels sugeridos, estimativa (horas) e link para REQ-IDs relevantes.

Saída esperada:
- 6–10 blocos prontos para colar como issues no GitHub.

---

Prompt 3 — Manter cards atualizados
---
Contexto: Durante o desenvolvimento, os cards devem ser atualizados periodicamente.

Instrução para o assistente IA:
- Forneça um fluxo de trabalho (passos) para atualizar cards: quando abrir PR, ao aceitar revisão, ao mergear, e sugerir mensagens padrão para comentários de progresso. Inclua um pequeno script gh-cli para mover card entre colunas quando PR é aberto/merged.

Saída esperada:
- Fluxo de trabalho e snippets `gh` CLI para automação.
