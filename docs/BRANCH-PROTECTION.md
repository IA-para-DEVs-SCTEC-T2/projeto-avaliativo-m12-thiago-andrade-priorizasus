# PRIORIZASUS — Branch Protection Rules

> **These rules must be configured manually in GitHub UI** (Settings → Branches → Add Rule).
> They cannot be automated via code; this document serves as the canonical configuration reference.

## Protected Branch: `main`

### Rule Configuration

| Setting | Value | Rationale |
|---------|-------|-----------|
| **Branch name pattern** | `main` | Only the production branch is protected |
| **Require a pull request before merging** | ✅ Enabled | All changes go through PR review |
| **Require approvals** | ✅ 1 approval | At least one reviewer must approve |
| **Dismiss stale reviews** | ✅ Enabled | New commits invalidate old approvals |
| **Require conversation resolution** | ✅ Enabled | All review threads must be resolved |
| **Require status checks to pass** | ✅ Enabled | CI gates must be green |

### Required Status Checks

| Check Name | Pipeline | Description |
|-----------|----------|-------------|
| `build-gate` | `ai-build.yml` | Compile, tests, spotless, coverage |
| `plan-gate` | `ai-plan.yml` | Spec validation (structure, REQ-ID, cross-refs) |
| `spec-drift-gate` | `spec-drift-check.yml` | Spec↔code semantic alignment |
| `review-gate` | `ai-review.yml` | Architecture, intent-compliance, doc-coverage |
| `evidence-collect` | `ai-pipeline.yml` | Evidence collection for approval |

### Additional Settings

| Setting | Value | Rationale |
|---------|-------|-----------|
| **Require linear history** | ✅ Enabled | Clean git history, no merge commits on main |
| **Do not allow bypassing** | ✅ Include administrators | Even admins follow the rules |
| **Restrict push access** | Only specified teams | `@PRIORIZASUS/developers` can push to PR branches |

## Configuration Steps (GitHub UI)

1. Go to **Settings** → **Branches** → **Add branch protection rule**
2. Enter `main` as the branch name pattern
3. Check: **Require a pull request before merging**
   - Check: **Require approvals** (set to 1)
   - Check: **Dismiss stale pull request approvals when new commits are pushed**
   - Check: **Require conversation resolution before merging**
4. Check: **Require status checks to pass before merging**
   - Search and add each required check from the table above
5. Check: **Require linear history**
6. Check: **Do not allow bypassing the above settings** (Include administrators)
7. Click **Create** or **Save changes**

## Verification

After configuration, create a test PR to `main` and verify:

- [ ] PR cannot be merged without 1 approval
- [ ] PR cannot be merged if any status check fails
- [ ] New commits dismiss stale approvals
- [ ] All conversations must be resolved before merge
- [ ] No direct pushes to `main` are possible
