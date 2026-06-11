# Epic 14 Dashboard Design Parity QA

이 디렉터리는 Epic 14 후속 구현이 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`을 기준으로 strict mockup conformance를 검증하기 위한 비-production QA 산출물이다. Production code, frontend implementation, backend code/test, migration/schema, Source of Truth HTML은 이 story에서 수정하지 않는다.

## 기준

- 기준 Source of Truth는 `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`이다.
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`는 기준이 아니다.
- Pixel-perfect DOM/CSS byte-level clone은 non-goal이다.
- 그러나 IA, layout hierarchy, visual density, spacing rhythm, neutral panel grammar, information ordering, wide modal, Snapshot/History picker, retention expired/source absence state는 strict conformance target이다.
- Mockup과 다른 layout, density, color/visual grammar, ordering, modal/surface form, Snapshot/History interaction은 design choice가 아니라 deviation이다.

## Artifact Index

| Artifact | 용도 |
|---|---|
| `conformance-checklist.md` | Mockup에서 추출한 strict target과 viewport별 판정 기준 |
| `deviation-log.md` | 허용 deviation 1~4 category, reviewer decision, follow-up owner 기록 |
| `no-discretionary-redesign-checklist.md` | 임의 개선/재해석 금지 checklist |
| `mockup-principles-and-gap-map.md` | Project rail부터 retention/source absence까지 영역별 gap map |
| `handoff-gates.md` | 14.2, 14.3, 14.4 completion gate |
| `side-by-side-desktop-1440x1000.md` | desktop 비교 note와 screenshot reference |
| `side-by-side-tablet-1024x900.md` | tablet 비교 note와 screenshot reference |
| `side-by-side-mobile-390x844.md` | mobile 비교 note와 screenshot reference |
| `browser-baseline-observations.json` | Playwright로 관찰한 current `/dashboard` DOM 요약 |
| `browser-14-4-instance-wide-modal-and-end-to-end-visual-qa-observations.json` | 14.4 desktop/tablet/mobile auth-blocked browser QA와 full-path coverage gap 기록 |
| `source-semantics-sentinel-review.md` | 기존 frontend guard/static sentinel coverage와 후속 후보 |
| `guard-14-1-20260611-1716.md` | 14.1 guard/typecheck/build/YAML parse command result |
| `guard-14-4-instance-wide-modal-and-end-to-end-visual-qa-20260611-2000.md` | 14.4 guard/typecheck/build/static grep/browser scope evidence |

## Baseline Evidence

Current Vite app은 `cd frontend && npm run dev -- --host 127.0.0.1`로 실행했고, `/dashboard`는 세 viewport 모두 HTTP 200으로 열렸다. 단, 인증 token/fixture가 없어 실제 authenticated Project -> Application -> Dashboard data path는 열지 못했다.

| Viewport | Current Vite screenshot | Mockup reference screenshot | Browser 판정 |
|---|---|---|---|
| desktop `1440x1000` | `baseline-desktop-1440x1000.png` | `mockup-reference-desktop-1440x1000.png` | 인증 필요 상태. Project rail auth error, Application rail 대기, Main project 선택 대기까지만 관찰 |
| tablet `1024x900` | `baseline-tablet-1024x900.png` | `mockup-reference-tablet-1024x900.png` | 인증 필요 상태. authenticated dashboard sections 미검증 |
| mobile `390x844` | `baseline-mobile-390x844.png` | `mockup-reference-mobile-390x844.png` | 인증 필요 상태. `body.scrollWidth=504`, `viewportWidth=390` horizontal overflow 관찰. authenticated dashboard overflow와 동일하다고 단정하지 않음 |

Mockup interaction reference:

- `mockup-reference-desktop-snapshot-picker-1440x1000.png`
- `mockup-reference-desktop-instance-modal-1440x1000.png`

## Initial Git Status 기록

Story 14.1 시작 직후 실행한 `git status --short --branch --untracked-files=all` 결과:

```text
## codex/dashboard-sot-realignment-roadmap...origin/codex/dashboard-sot-realignment-roadmap
 M implementation-artifacts/sprint-status.yaml
 M planning-artifacts/epics.md
 D planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png
?? dbml-error.log
?? planning-artifacts/epic-14-dashboard-mockup-design-parity.md
?? planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md
?? planning-artifacts/stories/14-1-design-parity-baseline-and-visual-guardrails.md
```

보호 대상:

- `dbml-error.log`: 수정, 삭제, stage 금지.
- `planning-artifacts/stories/13-ui-dashboard-source-of-truth-surface-realignment.md`: read-only seed/handoff 자료.
- 완료된 Epic 13 story 본문/status: 되돌리거나 재작성하지 않음.
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`: read-only Source of Truth.
- `planning-artifacts/source-of-truth/source-of-truth-dashboard-snapshot-picker.png`: Source of Truth 기준 아님. 기존 삭제 상태를 되돌리거나 재생성하지 않음.

## Known Gap

Full authenticated browser fixture/runbook이 없어 `Project -> Application -> Dashboard -> Snapshot -> Instance -> retention expired` path를 하나의 browser smoke로 닫은 evidence는 없다. 14.1 산출물은 이 gap을 닫았다고 주장하지 않는다. 후속 14.2~14.4는 가능한 authenticated fixture가 생기면 같은 naming convention과 checklist로 evidence를 추가해야 한다.

14.4 final QA 기준으로도 `.private/smoke-auth.env` access token fixture가 없어 full authenticated path는 닫지 않았다. `current-14-4-dashboard-auth-blocked-{desktop|tablet|mobile}-*.png`와 `browser-14-4-instance-wide-modal-and-end-to-end-visual-qa-observations.json`은 `/dashboard` auth-blocked shell, no horizontal overflow, code/static guard evidence 범위만 증명한다.
