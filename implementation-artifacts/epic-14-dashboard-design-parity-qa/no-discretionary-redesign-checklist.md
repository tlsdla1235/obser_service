# No Discretionary Redesign Checklist

Epic 14는 "대충 비슷한 디자인"이나 "더 예쁜 재해석" story가 아니다. Tailwind/shadcn/Radix idiom으로 옮기는 과정에서도 HTML mockup conformance가 우선이다.

## 금지되는 임의 변경

- [ ] "더 예쁘게" 보이도록 layout hierarchy를 바꾸지 않는다.
- [ ] "더 현대적으로" 보이도록 density를 낮추거나 넓은 whitespace/card layout으로 바꾸지 않는다.
- [ ] "더 카드스럽게" 보이도록 panel 안에 panel/card를 중첩하지 않는다.
- [ ] "더 마케팅스럽게" 보이도록 hero, gradient, decorative visual을 추가하지 않는다.
- [ ] Project rail 또는 Application rail을 application 판단 surface처럼 만들지 않는다.
- [ ] Main surface의 context/read semantics bar를 아래로 밀지 않는다.
- [ ] Metric visualization을 lifecycle/data quality/direct reasons보다 먼저 지배적으로 만들지 않는다.
- [ ] Snapshot/History를 raw snapshot explorer, endpoint timeseries, arbitrary query UI로 바꾸지 않는다.
- [ ] Snapshot markerBucket을 state/evidence source로 보이게 하지 않는다.
- [ ] Snapshot detail을 current metric으로 보정하지 않는다.
- [ ] Instance live/snapshot detail을 narrow Sheet 중심으로 유지하지 않는다.
- [ ] Instance Summary에 Stored trend/projection trend/`InstanceTrendView` 진입점을 되살리거나 current state, health score, root cause timeline처럼 보이게 하지 않는다.
- [ ] Retention expired/source absence에 live/current fallback CTA를 넣지 않는다.

## Prototype-only 제외 항목

아래 mockup 요소는 production requirement가 아니다. 제외 자체는 allowed deviation category 3으로 기록할 수 있다.

- [ ] `.prototype-controls`
- [ ] `#scenarioSelect`
- [ ] `data-prototype-mode` buttons
- [ ] hard-coded `scenarios` JS data
- [ ] temporary `state` object와 mockup-only render runtime
- [ ] normalized endpoint table demo `endpointSort` / `endpointLimit` controls
- [ ] mockup-only generated scenario labels 또는 demo snapshot ids

## 허용할 수 있는 보강

- [ ] Keyboard focus ring, focus trap, ESC close, aria labeling은 시각 구조를 해치지 않는 한 allowed category 4로 기록할 수 있다.
- [ ] Mobile에서 3-column을 물리적으로 유지할 수 없으면 순서 보존 stack adaptation을 allowed category 2로 기록할 수 있다.
- [ ] Production read model field가 mockup demo copy와 다르면 source semantics를 우선하고 category 1로 기록한다.

## Review Gate

후속 story reviewer는 아래 질문에 모두 답해야 한다.

- [ ] 이 차이는 `deviation-log.md`에 있는가?
- [ ] allowed category 1~4 중 하나인가?
- [ ] reviewer decision이 Approved인가?
- [ ] follow-up owner가 있는가?
- [ ] mockup과 다른 구조/밀도/순서/문법을 "디자인 선택"으로 넘기지 않았는가?
