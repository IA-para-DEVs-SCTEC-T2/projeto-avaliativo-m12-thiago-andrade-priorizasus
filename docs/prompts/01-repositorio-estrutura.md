# Prompts: Repositório e Estrutura do Projeto

Prompt 1 — Criar repositório privado no GitHub com colaboradores
---
Contexto: Projeto PRIORIZASUS (sistema de agendamento ESF). O repositório deve ser privado e incluir os colaboradores obrigatórios do curso.

Instrução para o assistente IA:
- Crie um passo a passo para criar um repositório GitHub privado chamado `priorizasus` incluindo: criar repositório, adicionar colaboradores (lista de e-mails/nicks), configurar branch protection para `main` e `develop`, habilitar Copilot Code Review e GitHub Actions. Forneça comandos git CLI para inicializar localmente, adicionar remote e push inicial.

Saída esperada:
- Checklist de ações no GitHub (UI) e comandos git exatos.

---

Prompt 2 — Criar estrutura de pastas e arquivos iniciais
---
Contexto: Estrutura mínima exigida pelo checklist: `README.md`, `docs/prompts/`, `.env.example`.

Instrução para o assistente IA:
- Gere comandos e conteúdo inicial para: criar pastas, criar `README.md` com visão breve do projeto (descrição, stack, como rodar), criar `.env.example` com variáveis obrigatórias (DB_URL, DB_USER, DB_PASSWORD, SPRING_PROFILES_ACTIVE, TZ=UTC). Incluir `.gitignore` recomendado para Java/Maven.

Saída esperada:
- Script de shell (bash/powershell) com comandos `mkdir`, `echo`/`cat` para criar arquivos, e exemplo de conteúdo para cada arquivo.

---

Prompt 3 — Verificação final da organização do repositório
---
Contexto: Antes de submeter, validar presença dos itens obrigatórios e conformidade com o checklist.

Instrução para o assistente IA:
- Rode uma verificação simulada (lista de checagens) que verifica: repositório privado, existência de `README.md`, `docs/prompts/`, `.env.example`, presença de `mvnw` e `pom.xml`. Produza um relatório em formato de checklist com status (OK / FALTANDO) e comandos de correção sugeridos.

Saída esperada:
- Checklist marcado com instruções de correção rápida.
