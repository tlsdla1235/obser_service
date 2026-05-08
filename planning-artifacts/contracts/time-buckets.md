---
artifactType: contract
name: time-buckets
architectureStyle: Lightweight Hexagonal
status: party-mode-fixes-applied
date: 2026-05-08
---

# Contract - Time Buckets

## 1. 고정값

| 항목 | 값 |
|---|---|
| starter flush cadence | 30초 |
| bucket duration | 30초 |
| current window | 최근 15분 |
| baseline window | current 직전 15분 |
| time zone | UTC |
| stale 후보 | 최근 accepted bucket이 90초 이상 없음 |
| down 후보 | 최근 accepted bucket이 180초 이상 없음 |

## 2. Bucket Boundary

모든 bucket은 UTC 30초 boundary에 정렬한다.

예시:

- `01:00:00Z` to `01:00:30Z`
- `01:00:30Z` to `01:01:00Z`

boundary에 맞지 않는 bucket은 ingest validation에서 거부한다.

## 3. Window Semantics

`current`는 dashboard query 시점 기준 최근 15분 accepted bucket 묶음이다.

`baseline`은 current 바로 이전 15분 accepted bucket 묶음이다.

baseline이 충분하지 않으면 변화율 기반 rule은 꺼지고, absolute threshold 기반 rule만 허용한다.

## 4. Freshness Source

freshness는 starter가 주장하는 현재 시간이 아니라, portal이 수용한 마지막 bucket의 `endUtc` 기준으로 판단한다.

## 5. Hexagonal Boundary

- time boundary 계산은 domain/application에서 수행한다.
- system clock은 `ClockPort` 뒤에 둔다.
- persistence adapter는 timestamp를 저장하되 freshness 의미를 판단하지 않는다.
