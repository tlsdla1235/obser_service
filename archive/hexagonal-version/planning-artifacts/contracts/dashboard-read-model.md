---
artifactType: contract
name: dashboard-read-model
architectureStyle: Lightweight Hexagonal
status: superseded-by-read-model-contract
date: 2026-05-08
---

# Contract - Dashboard Read Model

## 1. 역할

이 파일은 기존 이름과의 연결을 위한 compatibility note다.

first-screen UI의 단일 구현 계약은 `planning-artifacts/contracts/read-model-contract.md`다.

## 2. 사용 규칙

- 새 구현과 테스트는 `read-model-contract.md`를 참조한다.
- 이 파일에 별도 response shape를 두지 않는다.
- 중복 계약을 만들지 않는다.
