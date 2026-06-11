# Side-by-Side Note - Desktop 1440x1000

## Evidence

- Current Vite: `baseline-desktop-1440x1000.png`
- Mockup reference: `mockup-reference-desktop-1440x1000.png`
- Mockup snapshot picker: `mockup-reference-desktop-snapshot-picker-1440x1000.png`
- Mockup instance modal: `mockup-reference-desktop-instance-modal-1440x1000.png`

## Current Observation

`/dashboard` returned HTTP 200, but no access token/authenticated fixture was available. The visible UI stops at:

- Project rail: `인증 필요`, `GitHub 로그인 후 사용할 수 있습니다.`
- Application rail: `Project 선택 대기`
- Main surface: `Project를 선택하세요`

Authenticated Project rail, Application rail, Main live surface, Snapshot picker, Snapshot detail, Instance modal, retention expired/source absence were not proven in browser.

## Desktop Judgment Fields

| Area | Judgment | Note |
|---|---|---|
| Project rail | coverage gap | Auth error only; authenticated rail density/order unverified |
| Application rail | coverage gap | Project not selected; authenticated application row unverified |
| Main surface | coverage gap | No dashboard data rendered |
| Snapshot picker | coverage gap | Mockup reference captured; Vite authenticated picker unverified |
| Snapshot detail | coverage gap | Mockup reference captured through live/snapshot reference; Vite unverified |
| Instance wide modal | coverage gap | Mockup reference captured; Vite wide modal unverified |
| Retention/source absence | coverage gap | Vite authenticated expired/source absence unverified |

## Follow-up

14.2~14.4 must add authenticated or fixture-backed desktop screenshots. If fixture remains unavailable, completion notes must keep this as a known gap and must not claim desktop conformance.
