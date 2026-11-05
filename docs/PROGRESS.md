# Progress log

The latest verified checkpoint is the most recent milestone below marked **Done** with recorded results and a matching commit on `main` (CLAUDE.md §1).

| Milestone | Status | Date | Commit |
|---|---|---|---|
| M0.1 Environment inspection | Done | 2026-11-05 | `3f44318` (recorded in the initial commit) |
| M0.2 Repositories | Done | 2026-11-05 | `3f44318` app repo, `a346654` config repo; results recorded in the next commit |
| M0.3 Confirm decisions and assumptions | Done (merged via pull request) | 2026-11-05 | branch `m0.3-confirm-assumptions` |

---

## M0.1 Environment inspection — Done (2026-11-05)

Read-only checks; no project files changed.

| Check | Command | Result |
|---|---|---|
| Java | `java -version` | OpenJDK 21.0.12.1 (Homebrew) |
| Java used by Maven | `mvn -v` | Initially Java 27 (`JAVA_HOME` unset). Fixed by the owner: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` in `~/.zshrc`; re-checked in a fresh shell → **21.0.12.1** |
| Maven | `mvn -v` | 3.10.0 (project will use the wrapper `./mvnw`) |
| Node / npm | `node -v`, `npm -v` | 26.11.1 / 12.2.0 |
| Git | `git --version` | 2.56.0 |
| GitHub CLI | `gh --version`, `gh auth status` | 2.103.3, logged in as `chhavinath-bit` (scopes `repo`, `workflow`, `gist`, `read:org`) |
| Docker | `docker --version`, `docker compose version`, `docker info` | 29.8.2, Compose v5.5.1, daemon running, 10 CPUs, 7.7 GB memory for containers |
| PostgreSQL image | `docker run --rm postgres:16 postgres --version` | PostgreSQL 16.15 |
| Host | `sw_vers`, `sysctl` | macOS 27.2, 16 GB RAM, 10 CPUs, 344 GB free disk |
| Ports | `lsof -iTCP:<port> -sTCP:LISTEN` | 5432, 5173, 6379, 8080–8085, 8761, 8888 all free |
| Git location | `git rev-parse --show-toplevel` | Folder not inside any Git repository → plain `git init` (B-1) |

Open issues: none.

---

## M0.2 Repositories — Done (2026-11-05)

What was done:
- `git init -b main` in `~/Developer/project/GridReserve`; added `.gitignore`, `.editorconfig`, `.gitattributes`, `LICENSE` (MIT), `README.md`, `infra/.env.example` (placeholders only), this log; committed the planning docs unchanged.
- Created the sibling folder `~/Developer/project/gridreserve-config` with its own `git init -b main`, `README.md`, `LICENSE`, `.gitignore`, `.gitattributes`.
- Created both public repositories with `gh repo create … --public --source=. --push`.
- Bootstrap commits went directly to `main` (an empty repository has no base for a pull request). From M0.3 on, every milestone uses a `m<phase>.<n>-<name>` branch and a pull request.
- Commit author: Chhavi Nath with the personal Gmail address (owner's choice); no AI attribution lines.

Verification (actual results):

| Check | Command | Result |
|---|---|---|
| `.env` files ignored | `git check-ignore -v .env infra/.env` | both matched by `.gitignore:19` |
| IDE and OS files ignored | `git check-ignore -v .idea .DS_Store` | matched by `.gitignore:26` and `:32` |
| Env template tracked | `git ls-files --error-unmatch infra/.env.example` | tracked |
| Secret scan, app repo | `git grep --cached -nIE "<private-key / AWS / GitHub / Slack / Stripe token patterns>"` | no matches (exit 1) |
| Password-like assignments | `git grep --cached -nIE "(PASSWORD\|SECRET\|KEY)[A-Z_]*="` | only `change-me` placeholders in `infra/.env.example` |
| Secret scan, config repo | same token patterns | no matches (exit 1) |
| Remote state | `gh repo view chhavinath-bit/<repo>` | both PUBLIC, default branch `main`, license detected as MIT |
| Remote contents | `gh api repos/chhavinath-bit/<repo>/contents` | app repo: docs, hygiene files, `docs/`, `infra/`; config repo: README, LICENSE, `.gitignore`, `.gitattributes` |
| Local state | `git status -sb` | both `## main...origin/main`, clean |
| No AI attribution | `git log --all --format=%B \| grep -ci "co-authored\|generated with"` | 0 in both repositories |

Note: the first secret-scan command had a wrong argument order (`--cached` after the pattern) and errored; it was re-run correctly with the results above.

Links: https://github.com/chhavinath-bit/GridReserve · https://github.com/chhavinath-bit/gridreserve-config

## M0.3 Confirm decisions and assumptions — Done (2026-11-05)

Owner review in three batches. Recorded in REQUIREMENTS.md v0.4 (§2.3, §6, §17), ARCHITECTURE.md (§5.3 schema, §19 D-03 and D-32, §20), CLAUDE.md §15, IMPLEMENTATION_PLAN.md (M1.2, M1.4, M1.7, P3, P7) and DESIGN_SYSTEM.md (`DateStrip`).

| Item | Decision |
|---|---|
| A-23 Library seats | Confirmed: 60-minute grid, up to 4 h |
| A-06 Opening hours | **Changed**: per-weekday hours from Phase 1 (`venue_opening_hours`, optional `bookable_opening_hours`; no row = closed); still no booking across local midnight |
| A-07 Booking horizon | **Changed**: 7 days (was 14) |
| A-08 Gym Trainer | **Changed**: 15-minute grid, 45-minute sessions, 15-minute buffer (one client per hour) |
| A-02 / A-24 Tokens | Confirmed: access 15 min, refresh 7 days; cookie HttpOnly, Secure (prod), `SameSite=Strict`, path `/api/v1/auth`; Origin + `X-GridReserve-Client` checks |
| A-03 Other member's booking | Confirmed: 404 |
| A-27 Key rotation | Confirmed: manual, with `kid` |
| A-25 Resilience defaults | Confirmed as proposed |
| A-28 Memory | Confirmed: 256 MB heap per service |
| A-05 / A-09 / A-12 | Confirmed: expiry job 30 s; limit counts HELD + CONFIRMED not yet ended; idempotency keys 24 h |
| 2026-11-04 doc fixes (B-2) incl. D-03 | Confirmed; D-03 (promotion inside the releasing transaction) now decided |

Verification (actual results):

| Check | Command | Result |
|---|---|---|
| Every edit applied exactly once | Python replace script asserting one match per change | "all edits applied" |
| No stale values left | `grep -n "14 days\|default 14\|DEFAULT 14\|60 / 60 / 60 / 15\|Trainer 60\|confirm at P3\|confirm D-03\|same every day" *.md` | one match, the intentional "resolved" note in ARCHITECTURE.md §20 |

## Open issues

- None.
