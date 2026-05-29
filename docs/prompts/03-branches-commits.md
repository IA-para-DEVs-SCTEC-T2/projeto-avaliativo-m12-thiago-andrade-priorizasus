# Prompts: Branches, Commits e Merge

Prompt 1 — Criar branches descritivas a partir de `develop`
---
Contexto: Convenção de branches: `develop` para integração, `main` protegida. Branches de feature saem de `develop`.

Instrução para o assistente IA:
- Forneça exemplos e comandos para criar branches no formato `feat/PM-001-descricao` ou `fix/BK-002-corrige-validacao`. Explique fluxo: `develop` → `feat/...` → PR → revisão → merge em `develop` → release → merge em `main`.

Saída esperada:
- Comandos git e exemplos de nomes de branch.

---

Prompt 2 — Mensagens de commit claras e incrementais
---
Contexto: Mínimo 8 commits com mensagens claras, preferencialmente com REQ-ID.

Instrução para o assistente IA:
- Gere exemplos de mensagens de commit no formato `feat(PM-001): adiciona endpoint de cadastro de paciente` e `fix(BK-002): valida horario de slot`. Forneça dicas para mensagens incrementais e como dividir mudanças em commits pequenos e atômicos.

Saída esperada:
- 10 exemplos de mensagens de commit e regras práticas.

---

Prompt 3 — Merge final na `main`
---
Contexto: Após concluir o desenvolvimento, tudo deve ser mergeado em `main` com evidências de CI verde e evidência de aprovação.

Instrução para o assistente IA:
- Descreva o procedimento de merge para `main`: preparar release branch a partir de `develop`, rodar CI, criar PR para `main`, preencher template de PR com checklist de `SpecDriftDetectionTest` e `Evidence log`, e comandos para criar tag e push.

Saída esperada:
- Checklist para merge com comandos git e template de PR.
