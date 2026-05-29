#!/bin/bash
# =============================================================================
# PRIORIZASUS — GitHub Kanban Automation Script
# Cria 19 issues + GitHub Project V2 em uma única execução via gh CLI.
#
# Pré-requisitos:
#   gh CLI (gh auth login) com scopes: repo, project, read:org
#   jq (winget install jqlang.jq)
#
# Uso:   bash create-kanban.sh
# =============================================================================
set -euo pipefail

# ── Config ──────────────────────────────────────────────────────────────────
REPO="IA-para-DEVs-SCTEC-T2/projeto-avaliativo-m12-thiago-andrade-priorizasus"
ORG="IA-para-DEVs-SCTEC-T2"
PROJECT_TITLE="PRIORIZASUS — Kanban M12"
PROJECT_DESC="Quadro Kanban do projeto avaliativo M12 — Thiago Andrade"

# Cores ANSI
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'
BOLD='\033[1m'

# ── Funções Auxiliares ──────────────────────────────────────────────────────
log_step()  { echo -e "\n${BLUE}${BOLD}═══ $1 ═══${NC}"; }
log_ok()    { echo -e "  ${GREEN}✓${NC} $1"; }
log_warn()  { echo -e "  ${YELLOW}⚠${NC} $1"; }
log_err()   { echo -e "  ${RED}✗${NC} $1"; }
log_info()  { echo -e "  → $1"; }

# Constrói o corpo da issue usando o template padrão
make_body() {
  local titulo="$1"
  local objetivo="$2"
  local criterios="$3"
  local contexto="$4"
  local entregaveis="$5"
  local referencias="$6"
  local prioridade="$7"
  local estimativa="$8"

  cat <<BODYEOF
## 📋 Tarefa: ${titulo}

**Status**: ⬜ Todo | **Prioridade**: ${prioridade} | **Estimativa**: ${estimativa}

---

### 🎯 Objetivo

${objetivo}

---

### 📋 Critérios de Aceitação

${criterios}

---

### 🔧 Contexto do Projeto (PRIORIZASUS)

${contexto}

---

### 📝 Entregáveis

${entregaveis}

---

### 💡 Referências

${referencias}

---

### 🔗 Links Relacionados

- 📦 Repositório: https://github.com/${REPO}
- 📖 CONTEXT.md: \`CONTEXT.md\` — glossário canônico
- 🏗️ Arquitetura: \`docs/adr/\` — 5 ADRs documentadas
- 📐 Specs: \`.specs/features/\` — 5 features Phase 1
- ⚙️ CI/CD: \`.github/workflows/\` — 6 pipelines
- 🧪 Harness: \`docs/HARNESS.md\`
BODYEOF
}

# Cria uma issue e retorna o número
create_one() {
  local titulo="$1"; shift
  local labels="$1"; shift
  local body="$1"; shift

  local result
  result=$(gh issue create \
    --repo "$REPO" \
    --title "$titulo" \
    --body "$body" \
    --label "$labels" \
    2>&1) || { log_err "Falha ao criar: $titulo"; echo "$result"; return 1; }

  # Extrai o número da issue da URL de saída
  local issue_number
  issue_number=$(echo "$result" | grep -oP 'https://github\.com/[^/]+/[^/]+/issues/\K\d+' || echo "?")
  echo "$issue_number"
}

# Adiciona uma issue ao Project V2
add_to_project() {
  local issue_url="$1"
  local project_num="$2"

  gh project item-add "$project_num" \
    --owner "$ORG" \
    --url "$issue_url" \
    >/dev/null 2>&1 || log_warn "Não foi possível adicionar ao Project: $issue_url"
}

# ── Início ──────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}${BOLD}║   PRIORIZASUS — Kanban Automation via gh CLI             ║${NC}"
echo -e "${GREEN}${BOLD}║   Repo: ${REPO}  ║${NC}"
echo -e "${GREEN}${BOLD}╚══════════════════════════════════════════════════════════╝${NC}"

# ── 1. Pré-requisitos ──────────────────────────────────────────────────────
log_step "1. Verificando pré-requisitos"

command -v gh >/dev/null 2>&1 || { log_err "gh CLI não encontrado. Instale: winget install GitHub.cli"; exit 1; }
log_ok "gh CLI encontrado: $(gh --version | head -1)"

command -v jq >/dev/null 2>&1 || { log_err "jq não encontrado. Instale: winget install jqlang.jq"; exit 1; }
log_ok "jq encontrado: $(jq --version)"

if gh auth status --hostname github.com >/dev/null 2>&1; then
  log_ok "gh autenticado no github.com"
else
  log_err "gh não autenticado. Execute: gh auth login --scopes repo,project,read:org"
  exit 1
fi

# Verifica acesso ao repo
if gh repo view "$REPO" --json name >/dev/null 2>&1; then
  log_ok "Repositório acessível: $REPO"
else
  log_err "Repositório inacessível: $REPO"
  exit 1
fi

# ── 2. Criar Labels ────────────────────────────────────────────────────────
log_step "2. Criando labels"

declare -A LABELS=(
  ["setup"]="0052cc"
  ["ia-ciclo-1"]="006b75"
  ["ia-ciclo-2"]="0e8a16"
  ["ia-ciclo-3"]="1d76db"
  ["refatoracao"]="d4c5f9"
  ["testes"]="c5def5"
  ["documentacao"]="fef2c0"
  ["ci-cd"]="d93f0b"
  ["evidencias"]="fbca04"
  ["alta"]="d73a4a"
  ["media"]="a2eeef"
  ["baixa"]="c2e0c6"
)

for label in "${!LABELS[@]}"; do
  color="${LABELS[$label]}"
  gh label create "$label" --repo "$REPO" --color "$color" --force 2>/dev/null && \
    log_ok "Label: $label (#$color)" || \
    log_warn "Label já existe: $label"
done

# ── 3. Criar GitHub Project V2 ─────────────────────────────────────────────
log_step "3. Criando GitHub Project V2"

# Verifica se já existe um Project com o mesmo nome
EXISTING_PROJECT=$(gh project list --owner "$ORG" --format json 2>/dev/null | \
  jq -r --arg title "$PROJECT_TITLE" '.projects[]? | select(.title == $title) | .number' 2>/dev/null || echo "")

if [[ -n "$EXISTING_PROJECT" ]]; then
  log_warn "Project '$PROJECT_TITLE' já existe (número: $EXISTING_PROJECT)"
  PROJECT_NUM="$EXISTING_PROJECT"
else
  PROJECT_RESPONSE=$(gh project create --owner "$ORG" --title "$PROJECT_TITLE" --format json 2>&1)
  PROJECT_NUM=$(echo "$PROJECT_RESPONSE" | jq -r '.number')
  PROJECT_URL=$(echo "$PROJECT_RESPONSE" | jq -r '.url')
  log_ok "Project criado: $PROJECT_URL"
fi

log_info "Project número: $PROJECT_NUM"

# ── 4. Criar 19 Issues ─────────────────────────────────────────────────────
log_step "4. Criando 19 Issues com template PRIORIZASUS"

declare -a ISSUE_URLS=()
declare -a ISSUE_TITLES=()

create_issue() {
  local num="$1"; local titulo="$2"; local labels="$3"; local objetivo="$4"
  local criterios="$5"; local contexto="$6"; local entregaveis="$7"
  local referencias="$8"; local prioridade="$9"; local estimativa="${10}"

  local body
  body=$(make_body "$titulo" "$objetivo" "$criterios" "$contexto" "$entregaveis" "$referencias" "$prioridade" "$estimativa")

  log_info "[#$num] Criando: $titulo"
  local issue_number
  issue_number=$(create_one "$titulo [$num/19]" "$labels" "$body")
  if [[ "$issue_number" =~ ^[0-9]+$ ]]; then
    local url="https://github.com/$REPO/issues/$issue_number"
    ISSUE_URLS+=("$url")
    ISSUE_TITLES+=("$titulo")
    log_ok "Issue #$issue_number: $titulo"
  else
    log_err "Falha ao extrair número da issue para: $titulo"
    ISSUE_URLS+=("")
  fi
}

# ─────── FASE 1: Setup & Planejamento ───────

create_issue 1 \
  "Criar repositório privado no GitHub e adicionar colaboradores" \
  "setup,alta" \
  "Confirmar que o repositório \`projeto-avaliativo-m12-thiago-andrade-priorizasus\` está configurado como **privado** e adicionar os colaboradores obrigatórios da disciplina (professor + equipe SCTEC) com permissões adequadas." \
  "- [ ] Repositório visivelmente **privado** (cadeado no GitHub)
- [ ] Colaboradores adicionados com permissão \`push\` ou \`admin\`
- [ ] Branch \`main\` protegida (requer PR + CI passando)
- [ ] \`CODEOWNERS\` configurado em \`.github/CODEOWNERS\`" \
  "O **PRIORIZASUS** é um sistema de agendamento justo para clínicas **ESF (Estratégia Saúde da Família)** que prioriza pacientes por urgência clínica e dias de atraso usando um algoritmo de scoring (Score = soma dos pesos das Categorias + dias de atraso × 10). O repositório contém o código Spring Boot + PostgreSQL com arquitetura em camadas e 6 pipelines de CI/CD. Esta é a tarefa de setup inicial do projeto avaliativo M12." \
  "- \`.github/CODEOWNERS\` — arquivo de code owners" \
  "- \`CONTEXT.md\` — termo canônico: **Patient**, **Category**, **Slot**, **Score**
- \`docs/BRANCH-PROTECTION.md\` — guia de proteção de branch
- \`docs/adr/0004-phase1-scope-boundary.md\` — escopo Phase 1" \
  "Alta" "1h"

create_issue 2 \
  "Definir domínio e escopo da aplicação" \
  "setup,alta" \
  "Documentar formalmente o **domínio** (clínicas ESF com agendamento por prioridade clínica) e o **escopo Phase 1**: Patient Master, Capacity Model, Scoring Algorithm, Booking System, Staff Dashboard. Excluir explicitamente: home visits, no-show penalties, multi-user auth, mobile app." \
  "- [ ] \`CONTEXT.md\` revisado com glossário canônico completo (~25 termos)
- [ ] Escopo documentado: 5 features IN, 4 features OUT
- [ ] Termos \"Avoid\" removidos do código (ex: \`Client\` → \`Patient\`, \`Priority\` → \`Score\`)
- [ ] Domínio validado com as regras de negócio reais de uma UBS" \
  "O \`CONTEXT.md\` já define o glossário canônico do projeto com termos como **Patient** (ACTIVE/INACTIVE/SUSPENDED), **Category** (PRENATAL/CHILD/CHRONIC), **Slot** (30 min, 40/semana), **Score** (weight + daysOverdue × 10, cap 500), **Weekly Selection** (segunda 7h, top 40), **Booking** (FOR UPDATE NOWAIT). A **ADR #0004** define o Phase 1 scope boundary. Esta tarefa formaliza o que já existe e garante que não há termos inconsistentes." \
  "- \`CONTEXT.md\` — glossário canônico final
- \`docs/adr/0004-phase1-scope-boundary.md\` — revisado" \
  "- \`CONTEXT.md\` — **Category**, **Score**, **Weekly Selection**, **Booking**
- \`docs/adr/0004-phase1-scope-boundary.md\`
- \`docs/adr/0002-slot-capacity-split.md\` — 40 BATCH slots/semana" \
  "Alta" "3h"

create_issue 3 \
  "Planejar arquitetura com suporte de IA e documentar decisões" \
  "setup,alta" \
  "Usar IA (Copilot/ChatGPT) para **revisar e refinar** a arquitetura em camadas do PRIORIZASUS e documentar **5 ADRs** (Architecture Decision Records). Validar stack, padrões, e restrições com auxílio de IA." \
  "- [ ] Stack validada: Spring Boot 4.0.6, Java 22, PostgreSQL 16, Docker
- [ ] 5 ADRs revisadas e consistentes entre si
- [ ] Arquitetura em camadas documentada: controller → service → repository → entity
- [ ] Prompt de revisão arquitetural documentado em \`docs/prompts/\`" \
  "A arquitetura do PRIORIZASUS segue o padrão **Spring Boot em camadas** com 7 services, 16 entities, 8 controllers. As 5 ADRs documentam decisões críticas: **#0001** (pessimistic locking NOWAIT — sem fila, fail-fast), **#0002** (40 BATCH slots + capacidade dividida), **#0003** (UTC no banco, America/Sao_Paulo na tela), **#0004** (Phase 1 = 5 features), **#0005** (snapshot de elegibilidade no momento da seleção). O \`.github/copilot-instructions.md\` define regras rígidas: @ReqId em métodos públicos, @Transactional só em services, locks só em repositories." \
  "- \`docs/adr/0001\` a \`0005\` — ADRs revisadas
- \`DESIGN.md\` — atualizado com decisões
- \`docs/GRILL-DECISIONS.md\` — log de decisões" \
  "- \`DESIGN.md\` — stack e padrões
- \`docs/ADR-FORMAT.md\` — template de ADR
- \`docs/GRILL-DECISIONS.md\` — decisões grilladas
- \`.github/copilot-instructions.md\` — regras de implementação" \
  "Alta" "4h"

create_issue 4 \
  "Criar estrutura inicial do projeto e README.md" \
  "setup,alta" \
  "Garantir que a estrutura de pacotes Spring Boot está correta e criar um **README.md inicial** com badges, descrição, stack, e instruções de setup via Docker Compose." \
  "- [ ] Estrutura de pacotes validada: \`controller/\`, \`service/\`, \`repository/\`, \`entity/\`, \`dto/\`, \`config/\`, \`annotation/\`
- [ ] README.md criado com: descrição do projeto, badges (Java 22, Spring Boot 4.0.6, coverage), instruções Docker
- [ ] \`docker-compose up\` sobe PostgreSQL + app sem erros
- [ ] \`mvnw clean compile\` compila sem erros" \
  "O PRIORIZASUS é um projeto **Maven** com Spring Boot 4.0.6, Java 22, PostgreSQL 16 (Alpine), Spotless para formatação, JaCoCo para cobertura. O Docker Compose orquestra 2 serviços: \`postgres\` (healthcheck + pg_isready) e \`app\` (depende do postgres saudável). O Dockerfile usa multi-stage build: \`maven:3.9.9-eclipse-temurin-22\` → \`eclipse-temurin:22-jre-jammy\`. Não existe README.md atualmente — será criado do zero." \
  "- \`README.md\` — inicial com badges + instruções
- Estrutura de pacotes em \`src/main/java/com/priorizasus/priorizasus/\`" \
  "- \`pom.xml\` — dependências e plugins
- \`docker-compose.yml\` — orquestração
- \`Dockerfile\` — build multi-stage
- \`docs/HARNESS.md\` — estrutura do projeto" \
  "Alta" "2h"

# ─────── FASE 2: Geração de Código com IA ───────

create_issue 5 \
  "Gerar código das funcionalidades principais com IA — Ciclo 1" \
  "ia-ciclo-1,alta" \
  "Usar IA (Copilot/ChatGPT) para **gerar o código das 5 features do Phase 1**: Patient Master, Capacity Model, Scoring Algorithm, Booking System, Staff Dashboard. Documentar prompts, saídas e ajustes necessários." \
  "- [ ] 7 Services gerados: PatientService, CapacityService, ScoringService, BookingService, AppointmentService, AuditLogService, EmailService
- [ ] 16 Entities geradas com JPA annotations e enums
- [ ] 8 Controllers REST + Thymeleaf gerados
- [ ] Todos os métodos públicos anotados com \`@ReqId\`
- [ ] Código compila com \`mvnw clean compile\`
- [ ] Prompts do ciclo 1 salvos em \`docs/prompts/01-geracao-codigo/\`" \
  "O código do PRIORIZASUS já foi majoritariamente gerado e está em \`src/main/java/com/priorizasus/priorizasus/\`. Esta tarefa **documenta o ciclo 1**: quais prompts foram usados, quais arquivos foram gerados, quais ajustes manuais foram necessários. Features geradas: **Patient Master** (CRUD pacientes + categorias PRENATAL/CHILD/CHRONIC), **Capacity Model** (criação semanal de 40 BATCH slots de 30min), **Scoring Algorithm** (Score = weight + daysOverdue × 10, cap 500, ranking, top 40), **Booking System** (reserva com FOR UPDATE NOWAIT, 1 consulta/semana/paciente), **Staff Dashboard** (Thymeleaf, occupancy, overrides, audit trail)." \
  "- Código em \`src/main/java/com/priorizasus/priorizasus/\`
- Prompts em \`docs/prompts/01-geracao-codigo/\`" \
  "- \`.specs/features/patient-master/spec.md\`
- \`.specs/features/capacity-model/spec.md\`
- \`.specs/features/scoring-algorithm/spec.md\`
- \`.specs/features/booking-system/spec.md\`
- \`.specs/features/staff-dashboard/spec.md\`
- \`CONTEXT.md\` — canonical terms para nomes de classes/métodos" \
  "Alta" "6h"

create_issue 6 \
  "Avaliar saída da IA e aplicar refinamento — Ciclo 2" \
  "ia-ciclo-2,media" \
  "Revisar criticamente o código gerado no ciclo 1. Verificar: **terminologia canônica**, **regras de negócio**, **ADR compliance**, e **layered architecture**. Aplicar correções e documentar o que foi melhorado." \
  "- [ ] \`SpecDriftDetectionTest\` executado e passando (spec↔code alignment)
- [ ] \`ArchitectureTest\` (ArchUnit) executado e passando
- [ ] Terminologia revisada: zero ocorrências de termos \"Avoid\" no código
- [ ] Regras de negócio validadas: score formula, 500 cap, tie-breaking, slot expiry
- [ ] Relatório de avaliação documentado" \
  "O **Semantic Fix Loop** (definido em \`docs/HARNESS.md\`) guia este ciclo: (1) edit, (2) annotate @ReqId, (3) spotless:apply, (4) test, (5) drift-check, (6) semantic validation, (7) fix (max 3 retries). O \`SpecDriftDetectionTest\` valida: REQ-ID → Code (todo REQ-ID tem @ReqId), Code → REQ-ID (métodos públicos têm anotação), Semantic Rules (FOR UPDATE NOWAIT, UTC timezone). O \`ArchitectureTest\` valida: business logic proibida em controllers, @Transactional proibido em controllers, controllers não acessam repositories diretamente." \
  "- Relatório de avaliação do ciclo 2
- Código refinado com correções
- Prompts do ciclo 2 em \`docs/prompts/02-refinamento/\`" \
  "- \`docs/HARNESS.md\` — Semantic Fix Loop
- \`.github/workflows/spec-drift-check.yml\`
- \`.github/workflows/ai-review.yml\`
- \`CONTEXT.md\` — termos canônicos vs. Avoid" \
  "Média" "4h"

create_issue 7 \
  "Implementar terceiro ciclo com padrão de prompting diferente — Ciclo 3" \
  "ia-ciclo-3,media" \
  "Experimentar um **padrão de prompting diferente** do ciclo 1 (ex: spec-driven com REQ-ID explícito, chain-of-thought, ou role-based \"you are a Spring Boot architect\"). **Comparar** qualidade, completude e aderência às ADRs entre os ciclos." \
  "- [ ] Novo padrão de prompting definido e documentado
- [ ] Código gerado com o novo padrão para pelo menos 2 features
- [ ] Comparativo ciclo 1 vs. ciclo 3 documentado (tabela: critério, ciclo 1, ciclo 3, vencedor)
- [ ] Conclusão: qual padrão produziu código mais alinhado com ADRs e CONTEXT.md?" \
  "Sugestões de features para o ciclo 3: (a) Refinar o **ScoringService** extraindo \`CategoryWeightCalculator\` e \`DaysOverdueCalculator\`, (b) Implementar **BookingToken** (tokenized booking links via email) com token UUID + expiração 48h, (c) Refinar **AuditLog** com queries de export CSV. Sugestões de prompting: **Spec-Driven** (fornecer spec.md completo como contexto), **Chain-of-Thought** (pedir para IA raciocinar passo a passo), **Role-Based** (\"You are a senior Spring Boot architect at a Brazilian health-tech startup\")." \
  "- Código do ciclo 3 em branch separada ou em \`src/\`
- \`docs/comparativo-ciclos-ia.md\` — tabela comparativa
- Prompts do ciclo 3 em \`docs/prompts/03-ciclo-3/\`" \
  "- \`.specs/codebase/STACK.md\` — convenções de código
- \`CONTEXT.md\` — canonical terms para prompting consistente
- \`docs/adr/\` — constraints para o prompt (FOR UPDATE NOWAIT, UTC, Phase 1)" \
  "Média" "3h"

# ─────── FASE 3: Qualidade & Refatoração ───────

create_issue 8 \
  "Refatorar código com suporte de IA — Clean Code / SOLID" \
  "refatoracao,alta" \
  "Aplicar princípios **SOLID** com auxílio de IA. Extrair responsabilidades do \`ScoringService\` (200+ linhas) em classes menores com responsabilidade única, mantendo rastreabilidade com \`@ReqId\`." \
  "- [ ] \`ScoringService\` refatorado: SRP aplicado (extrair calculadoras, ranker, orquestrador)
- [ ] Novas classes extraídas com \`@ReqId\` annotations
- [ ] Testes existentes continuam passando
- [ ] \`mvnw spotless:check\` passa
- [ ] Cobertura JaCoCo mantida ≥ 80%" \
  "O \`ScoringService\` atual concentra múltiplas responsabilidades: (1) verificar elegibilidade do paciente (ACTIVE, com categoria), (2) calcular Score (weight + daysOverdue × 10), (3) ranquear pacientes (tie-breaking: targetDate, registration), (4) executar Weekly Selection (top 40, FOR UPDATE NOWAIT), (5) agendar (@Scheduled, segunda 7h). Refatoração sugerida: \`PatientEligibilityFilter\`, \`ScoreCalculator\`, \`DaysOverdueCalculator\`, \`CategoryWeightResolver\`, \`PatientRanker\`, \`WeeklySelectionExecutor\`, \`WeeklySelectionScheduler\`. Serviços devem ter uma única razão para mudar (SRP)." \
  "- \`ScoringService\` refatorado + novas classes extraídas
- Testes atualizados" \
  "- \`CONTEXT.md\` — Scoring & Selection terms
- \`docs/adr/0001-pessimistic-locking-nowait.md\` — lock só no repository
- \`docs/adr/0003-utc-storage-local-display.md\` — cálculos com LocalDate
- Martin Fowler — Refactoring, SOLID Principles" \
  "Alta" "5h"

create_issue 9 \
  "Documentar refatoração com comparativo antes/depois e prompt" \
  "refatoracao,media" \
  "Criar documento Markdown mostrando o **antes/depois** da refatoração do \`ScoringService\`, incluindo o **prompt usado** para a IA sugerir a refatoração e a análise crítica do resultado." \
  "- [ ] Documento com estrutura: Prompt → Código Antes → Código Depois → Análise
- [ ] Antes: \`ScoringService\` monolítico (200+ linhas) com todas as responsabilidades
- [ ] Depois: 6 classes extraídas com SRP aplicado
- [ ] Métricas comparativas: linhas por classe, complexidade ciclomática, cobertura de testes
- [ ] Lições aprendidas documentadas" \
  "Exemplo concreto a ser documentado: \`ScoringService.executeWeeklySelection()\` (~150 linhas) → \`WeeklySelectionOrchestrator\` (~30 linhas) + \`PatientEligibilityFilter\` (~20 linhas) + \`ScoreCalculator\` (~40 linhas) + \`DaysOverdueCalculator\` (~25 linhas) + \`PatientRanker\` (~35 linhas) + \`SlotAllocator\` (~50 linhas). O prompt deve ser transcrito na íntegra, e o documento deve analisar se a IA entendeu corretamente as regras de negócio (score formula, cap 500, tie-breaking, NOWAIT)." \
  "- \`docs/refatoracao-scoring-service.md\` — documento completo" \
  "- \`CONTEXT.md\` — **Score**, **Ranking**, **Weekly Selection**
- \`docs/adr/0001-pessimistic-locking-nowait.md\`
- \`.specs/features/scoring-algorithm/spec.md\`
- Robert C. Martin — Clean Code" \
  "Média" "2h"

# ─────── FASE 4: Testes & Documentação ───────

create_issue 10 \
  "Gerar suíte de testes com suporte de IA" \
  "testes,alta" \
  "Usar IA para gerar **testes unitários** (JUnit 5 + Mockito) para todos os 7 services, **testes de integração** para repositories (@DataJpaTest), e **testes harness** (ArchitectureTest, SpecConsistencyTest, SpecDriftDetectionTest)." \
  "- [ ] Testes unitários para 7 services com cobertura de happy path + edge cases
- [ ] Testes de integração para 6 repositories com @DataJpaTest
- [ ] Testes harness: ArchitectureTest, SpecConsistencyTest, SpecDriftDetectionTest
- [ ] Cenários críticos cobertos: paciente ineligible, slot locked (NOWAIT), <40 elegíveis, score cap 500
- [ ] JaCoCo coverage ≥ 80% geral" \
  "Stack de teste: **JUnit 5**, **Mockito**, **Spring Boot Test**, **H2** (test profile) ou PostgreSQL real. Testes harness definidos em \`docs/HARNESS.md\`: \`SpecConsistencyTest\` (spec file existence, REQ-ID uniqueness, cross-references), \`ArchitectureTest\` (ArchUnit: controller não acessa repository, @Transactional só em service, locks só em repository), \`SpecDriftDetectionTest\` (spec↔code alignment). Cenários de negócio a cobrir: (1) scoring com múltiplas categorias, (2) daysOverdue cap 500 por categoria, (3) tie-breaking por targetDate, (4) slot expiry sexta 17h, (5) FOR UPDATE NOWAIT timeout." \
  "- Testes em \`src/test/java/com/priorizasus/priorizasus/\`
- Relatório JaCoCo em \`target/site/jacoco/index.html\`" \
  "- \`docs/HARNESS.md\` — Test Harness Classes
- \`pom.xml\` — JUnit 5, Mockito, JaCoCo, H2
- \`.specs/features/*/spec.md\` — acceptance criteria viram testes
- \`application-test.properties\`" \
  "Alta" "5h"

create_issue 11 \
  "Validar e ajustar testes gerados" \
  "testes,media" \
  "Executar \`mvn verify\`, analisar relatório **JaCoCo**, corrigir testes quebrados, garantir cobertura de **todos os cenários de negócio** (inclusive semânticos, não só sintáticos)." \
  "- [ ] \`mvnw verify\` passa (Spotless + compile + test + JaCoCo)
- [ ] JaCoCo ≥ 80% de cobertura de linha
- [ ] SpecDriftDetectionTest passa (spec↔code)
- [ ] Todos os cenários da spec cobertos por pelo menos 1 teste
- [ ] Testes de concorrência (NOWAIT) passam" \
  "Validar se os testes realmente verificam as **regras de negócio**, não apenas se o código compila. Rodar \`mvnw verify\` que inclui: Spotless format check → compile → test → JaCoCo report → harness tests. Casos específicos a validar: (1) \`ScoringServiceTest\` — score com PRENATAL 36+ semanas (weight 1000) + 10 dias de atraso = 1100, (2) \`BookingServiceTest\` — duas threads tentando reservar o mesmo slot (NOWAIT deve rejeitar a segunda), (3) \`CapacityServiceTest\` — criar slots para uma semana com feriado na quarta (deve pular)." \
  "- Relatório JaCoCo (\`target/site/jacoco/index.html\`)
- Todos os testes passando (\`mvnw test\` 100% pass)" \
  "- \`docs/HARNESS.md\` — Guardrails
- \`target/surefire-reports/\` — relatórios de teste
- \`.github/workflows/ai-build.yml\` — CI compila e testa" \
  "Média" "3h"

create_issue 12 \
  "Gerar documentação automática com IA — docstrings, Swagger ou README" \
  "documentacao,media" \
  "Usar IA para adicionar **Javadoc com @ReqId** em todos os métodos públicos, configurar **SpringDoc OpenAPI** (Swagger UI), e gerar documentação de API." \
  "- [ ] Javadoc com @ReqId em todos os métodos públicos dos 7 services
- [ ] SpringDoc OpenAPI configurado (Swagger UI em \`/swagger-ui.html\`)
- [ ] Endpoints REST documentados com exemplos de request/response
- [ ] \`mvnw compile\` passa sem warnings de Javadoc" \
  "O PRIORIZASUS expõe endpoints REST e páginas Thymeleaf. Principais endpoints: \`/api/patients\` (CRUD), \`/api/slots\` (disponibilidade), \`/api/scoring/selection\` (resultado da seleção semanal), \`/api/bookings\` (reserva/confirmação). Staff Dashboard em Thymeleaf com \`/staff/dashboard\`, \`/staff/patients\`, \`/staff/slots\`. Swagger deve documentar: parâmetros, responses, error codes, exemplos curl. Javadoc deve referenciar REQ-IDs das specs (ex: \`@ReqId(\"SA-002\")\` no \`calculateScore()\`)." \
  "- Javadoc completo em \`src/main/java/\`
- Swagger UI acessível em \`http://localhost:8080/swagger-ui.html\`
- \`springdoc-openapi\` dependency no \`pom.xml\`" \
  "- \`.specs/features/*/spec.md\` — fonte dos @ReqId
- \`CONTEXT.md\` — descrições canônicas para Javadoc
- SpringDoc OpenAPI 2.x docs" \
  "Média" "3h"

# ─────── FASE 5: CI/CD & Automação ───────

create_issue 13 \
  "Configurar pipeline de CI/CD com GitHub Actions via IA" \
  "ci-cd,alta" \
  "Usar IA para **revisar e aprimorar** os 6 workflows existentes. Garantir cobertura completa: format, compile, test, coverage, spec drift, architecture validation, intent review, evidence logging, approval gate." \
  "- [ ] 6 workflows revisados e funcionais em \`.github/workflows/\`
- [ ] \`ai-pipeline.yml\` orquestra os 5 gates: Plan → Build → Drift → Review → Evidence
- [ ] \`ai-build.yml\` inclui Spotless + compile + test + JaCoCo
- [ ] \`spec-drift-check.yml\` executa SpecDriftDetectionTest
- [ ] \`evidence-log.yml\` gera \`docs/TRACEABILITY.md\` atualizado" \
  "O PRIORIZASUS já possui 6 workflows no GitHub Actions. Pipeline completo: \`ai-plan.yml\` (valida specs, REQ-IDs, cross-references), \`ai-build.yml\` (Spotless format, compile, test, JaCoCo coverage), \`ai-review.yml\` (ArchUnit layered architecture, REQ-ID traceability, intent compliance), \`spec-drift-check.yml\` (spec↔code semantic alignment), \`evidence-log.yml\` (extrai REQ-IDs dos commits, registra em TRACEABILITY.md), \`ai-pipeline.yml\` (unified pipeline: Plan → Build → Drift → Review → Evidence → Approval Gate). Esta tarefa revisa se estão completos e funcionais." \
  "- \`.github/workflows/*.yml\` — 6 pipelines revisados
- \`docs/HARNESS.md\` — CI/CD Pipelines table" \
  "- \`docs/HARNESS.md\` — Execution Flow + CI/CD Pipelines
- \`docs/TRACEABILITY.md\` — gerado pelo evidence-log.yml
- \`.github/copilot-instructions.md\` — rules enforced by CI" \
  "Alta" "4h"

create_issue 14 \
  "Testar e validar pipeline" \
  "ci-cd,media" \
  "Fazer push para branch, abrir PR para \`main\`, e verificar se o \`ai-pipeline.yml\` executa **todas as 5 fases** com sucesso. Documentar evidências com screenshots." \
  "- [ ] Branch criada, push feito, PR aberto para \`main\`
- [ ] Plan Gate passa (specs válidos, REQ-IDs únicos)
- [ ] Build Gate passa (Spotless, compile, test, JaCoCo)
- [ ] Drift Gate passa (spec↔code alignment)
- [ ] Review Gate passa (architecture rules, REQ-ID traceability)
- [ ] Evidence Gate coleta dados e atualiza TRACEABILITY.md
- [ ] Screenshots de cada gate verde salvos em \`docs/evidencias-pipeline/\`" \
  "O pipeline unificado \`ai-pipeline.yml\` roda em PR para \`main\` e em \`workflow_dispatch\`. Sequência: (1) Plan — valida \`.specs/\`, \`CONTEXT.md\`, ADRs, (2) Build — Spotless + compile + test + JaCoCo, (3) Spec Drift — SpecDriftDetectionTest, (4) Review — ArchUnit + @ReqId traceability, (5) Evidence — coleta gate statuses, gera TRACEABILITY.md. Se qualquer gate falhar, o PR não pode ser mergeado (branch protection)." \
  "- Screenshots dos 5 gates em \`docs/evidencias-pipeline/\`
- Log de execução do GitHub Actions" \
  "- \`.github/workflows/ai-pipeline.yml\`
- \`docs/HARNESS.md\` — Execution Flow
- \`docs/BRANCH-PROTECTION.md\`" \
  "Média" "2h"

# ─────── FASE 6: Evidências & Entrega ───────

create_issue 15 \
  "Salvar todos os prompts utilizados em docs/prompts/" \
  "evidencias,media" \
  "Coletar e organizar **todos os prompts** usados nos ciclos 1, 2, 3 e nas demais tarefas. Categorizar por fase em subpastas, com template padronizado: prompt completo, saída resumida, avaliação da qualidade." \
  "- [ ] Diretório \`docs/prompts/\` criado com 5 subpastas
- [ ] Cada prompt em arquivo \`.md\` separado com: data, ferramenta IA usada, prompt completo, saída resumida, avaliação (1-5 estrelas), lições
- [ ] Pelo menos 3 prompts por fase documentados
- [ ] \`docs/prompts/README.md\` com índice de todos os prompts" \
  "Estrutura sugerida: \`docs/prompts/01-geracao-codigo/\` (ciclo 1 — prompts que geraram PatientService, ScoringService, etc.), \`docs/prompts/02-refinamento/\` (ciclo 2 — prompts de correção e ajuste), \`docs/prompts/03-ciclo-3/\` (ciclo 3 — prompts com padrão diferente), \`docs/prompts/04-testes/\` (prompts que geraram suites de teste), \`docs/prompts/05-ci-cd-docs/\` (prompts para pipeline, Javadoc, Swagger). Template do arquivo de prompt: título, data, ferramenta, prompt (na íntegra, em code block), saída resumida, avaliação, lições." \
  "- \`docs/prompts/\` com subpastas e \`.md\` files
- \`docs/prompts/README.md\` — índice" \
  "- \`.specs/features/\` — fonte dos prompts spec-driven
- \`CONTEXT.md\` — terminologia usada nos prompts
- \`docs/refatoracao-scoring-service.md\` — exemplo de prompt documentado" \
  "Média" "2h"

create_issue 16 \
  "Documentar caso de análise crítica de saída incorreta da IA" \
  "evidencias,media" \
  "Documentar um **caso real** onde a IA gerou código errado para o PRIORIZASUS. Explicar: o prompt usado, a saída incorreta, **por que** estava errado (confrontando com ADRs/CONTEXT.md), e como foi corrigido." \
  "- [ ] Documento com estrutura: Prompt → Saída Incorreta → Diagnóstico → Correção → Lição
- [ ] Pelo menos 1 caso documentado em profundidade
- [ ] Erro conectado a uma ADR ou regra do CONTEXT.md
- [ ] Análise: por que a IA cometeu esse erro? (viés, falta de contexto, ambiguidade?)" \
  "Sugestões de casos reais para documentar: **(A) Optimistic vs. Pessimistic Locking** — IA sugeriu \`@Version\` (optimistic) no Slot, conflitando com ADR #0001 que exige \`SELECT ... FOR UPDATE NOWAIT\`. Correção: substituir por \`@Query\` com \`FOR UPDATE NOWAIT\` no \`SlotRepository\`. **(B) Timezone** — IA usou \`LocalDateTime.now()\` (sem UTC), conflitando com ADR #0003. Correção: \`Instant.now()\` no service, \`ZoneId.of(\"America/Sao_Paulo\")\` só no controller. **(C) Business Logic no Controller** — IA colocou cálculo de score no \`ScoringController\`, violando arquitetura em camadas. Correção: mover para \`ScoringService\`." \
  "- \`docs/analise-critica-ia.md\` — documento completo" \
  "- \`docs/adr/0001-pessimistic-locking-nowait.md\`
- \`docs/adr/0003-utc-storage-local-display.md\`
- \`.github/copilot-instructions.md\` — Layered Architecture rules
- \`CONTEXT.md\` — canonical vs. Avoid terms" \
  "Média" "2h"

create_issue 17 \
  "Atualizar README.md com diagrama, instruções e evidências" \
  "documentacao,media" \
  "Atualizar o README.md com: (a) descrição completa do PRIORIZASUS, (b) **diagrama de arquitetura** (Mermaid), (c) instruções de setup (Docker, Maven), (d) badges, (e) evidências do pipeline e cobertura." \
  "- [ ] README.md com descrição do projeto e contexto ESF
- [ ] Diagrama Mermaid: arquitetura em camadas + fluxo de scoring/booking
- [ ] Badges: Java 22, Spring Boot 4.0.6, Build (passing), Coverage (≥80%)
- [ ] Instruções: \`docker compose up\`, \`mvnw spring-boot:run\`, acesso \`localhost:8080\`
- [ ] Evidências: screenshot do pipeline verde, cobertura JaCoCo" \
  "O README.md deve conter: (1) **Sobre** — PRIORIZASUS é um sistema de agendamento justo para ESF que substitui a fila das 4h por algoritmo de priorização, (2) **Diagrama** — Mermaid mostrando Patient → Category → Scoring → Weekly Selection → Slot → Booking → Appointment, (3) **Stack** — Spring Boot 4.0.6, Java 22, PostgreSQL 16, Docker, Thymeleaf, GitHub Actions, (4) **Setup** — \`docker compose up -d\` + \`mvnw spring-boot:run\`, (5) **Pipeline** — badges + link para Actions, (6) **Evidências** — screenshots, link do vídeo demo." \
  "- \`README.md\` — completo e atualizado" \
  "- \`DESIGN.md\` — referência para o diagrama
- \`docker-compose.yml\` — instruções de setup
- \`docs/HARNESS.md\` — estrutura do projeto
- \`docs/evidencias-pipeline/\` — screenshots" \
  "Média" "3h"

create_issue 18 \
  "Gravar vídeo de demonstração e publicar no YouTube como não listado" \
  "evidencias,baixa" \
  "Gravar vídeo (5–10 min) demonstrando o PRIORIZASUS em funcionamento e publicar no YouTube como **não listado**. Roteiro: setup, cadastro, scoring, booking, dashboard, pipeline." \
  "- [ ] Vídeo com 5–10 minutos de duração
- [ ] Roteiro coberto: (1) Docker Compose subindo, (2) cadastro de pacientes com categorias, (3) execução do Weekly Selection, (4) booking por paciente, (5) Staff Dashboard com occupancy, (6) pipeline CI/CD no GitHub Actions
- [ ] Publicado no YouTube como **não listado**
- [ ] Link adicionado ao README.md" \
  "Roteiro detalhado: **(1) Setup** — \`docker compose up -d\`, mostrar containers saudáveis, acessar \`localhost:8080\`. **(2) Cadastro** — cadastrar 3 pacientes: Maria (PRENATAL 36 semanas, weight 1000), João (CHILD 2 meses, weight 700), Ana (CHRONIC, weight 200, 30 dias de atraso = +300). **(3) Weekly Selection** — mostrar execução, ranking (Maria 1000, Ana 500, João 700? — esperado Maria > João > Ana ou similar), top 40. **(4) Booking** — paciente confirma reserva, Appointment criado, Slot muda para BOOKED. **(5) Dashboard** — occupancy por categoria, audit trail. **(6) Pipeline** — mostrar GitHub Actions com 5 gates verdes." \
  "- Link do YouTube (não listado) adicionado ao README" \
  "- \`CONTEXT.md\` — roteiro cobre os fluxos principais
- \`docs/adr/0004-phase1-scope-boundary.md\` — features a demonstrar
- \`.github/workflows/ai-pipeline.yml\` — mostrar no vídeo" \
  "Baixa" "3h"

create_issue 19 \
  "Revisar checklist final e submeter links no AVA" \
  "evidencias,alta" \
  "Revisar **checklist completo** das 19 tarefas, verificar se todos os artefatos estão no repositório e funcionando, coletar todos os links necessários, e submeter no **AVA** da disciplina." \
  "- [ ] 19 tarefas concluídas e evidenciadas no repositório
- [ ] Repositório privado com colaboradores
- [ ] README.md completo com diagrama, badges, instruções
- [ ] Pipeline CI/CD passando (5 gates verdes)
- [ ] Cobertura de testes ≥ 80%
- [ ] \`docs/prompts/\` populado com todos os prompts
- [ ] \`docs/analise-critica-ia.md\` documentado
- [ ] Vídeo demo publicado (não listado)
- [ ] Links coletados: repo, Kanban, YouTube, pipeline
- [ ] Submissão no AVA com todos os links" \
  "Checklist final do projeto avaliativo M12. Verificar cada item: (1) Repo privado? (2) Colaboradores? (3) CONTEXT.md completo? (4) 5 ADRs? (5) README com diagrama? (6) Pipeline passando? (7) Cobertura ≥ 80%? (8) Prompts documentados? (9) Análise crítica? (10) Vídeo publicado? (11) Kanban populado? Coletar links e submeter no AVA conforme instruções da disciplina SCTEC." \
  "- Checklist preenchido
- Submissão no AVA com todos os links" \
  "- Este Kanban (GitHub Project) — visão geral das 19 tarefas
- \`docs/TRACEABILITY.md\` — rastreabilidade REQ-ID
- \`docs/HARNESS.md\` — visão geral do harness" \
  "Alta" "1h"

# ── 5. Adicionar Issues ao Project ──────────────────────────────────────────
log_step "5. Adicionando issues ao GitHub Project V2"

ADDED=0
for url in "${ISSUE_URLS[@]}"; do
  if [[ -n "$url" ]]; then
    add_to_project "$url" "$PROJECT_NUM"
    log_ok "Adicionada ao Project: $url"
    ((ADDED++))
  fi
done

# ── 6. Resumo Final ────────────────────────────────────────────────────────
log_step "6. Resumo Final"

TOTAL_CREATED=0
for url in "${ISSUE_URLS[@]}"; do
  [[ -n "$url" ]] && ((TOTAL_CREATED++))
done

echo ""
echo -e "${GREEN}${BOLD}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}${BOLD}║          KANBAN CRIADO COM SUCESSO!                      ║${NC}"
echo -e "${GREEN}${BOLD}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${BOLD}Repo:${NC}     https://github.com/${REPO}"
echo -e "  ${BOLD}Project:${NC}  https://github.com/orgs/${ORG}/projects/${PROJECT_NUM}"
echo -e "  ${BOLD}Issues:${NC}   ${TOTAL_CREATED}/19 criadas"
echo -e "  ${BOLD}Project:${NC}  ${ADDED}/19 adicionadas ao quadro"
echo ""

if [[ $TOTAL_CREATED -eq 19 ]]; then
  echo -e "  ${GREEN}Todas as 19 issues foram criadas com sucesso!${NC}"
else
  echo -e "  ${YELLOW}Atenção: apenas ${TOTAL_CREATED}/19 issues foram criadas.${NC}"
  echo -e "  ${YELLOW}Verifique os erros acima.${NC}"
fi

echo ""
echo -e "  ${BOLD}Próximos Passos:${NC}"
echo -e "  1. Acesse o Project: https://github.com/orgs/${ORG}/projects/${PROJECT_NUM}"
echo -e "  2. Configure as colunas: Todo, In Progress, Done"
echo -e "  3. Comece pela Fase 1 (Setup): Issues #1 a #4"
echo -e "  4. Rode o script novamente se precisar recriar (labels usam --force)"
echo ""
