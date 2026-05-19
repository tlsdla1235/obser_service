---
artifactType: contract
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: active
date: 2026-05-19
---

# Account Auth Policy - GitHub OAuth MVP

## 1. 목적과 경계

이 문서는 MVP의 사용자 계정 생성, 로그인, API 인증 token 정책을 고정한다.

이 정책은 starter가 portal ingest API에 사용하는 `X-OBS-Project-Key` 인증과 별개다. Project key는 host app metric ingest 권한 경계이고, 이 문서의 account auth는 portal 사용자 계정과 control-plane API 접근 경계다.

## 2. 계정 생성과 로그인 정책

- Account signup은 **GitHub OAuth only**다.
- Login도 MVP에서는 GitHub OAuth로 생성되었거나 연결된 계정에만 열린다.
- 사용자는 GitHub OAuth 인증을 성공적으로 완료해야 내부 `user/account` row를 만들거나 기존 계정과 연결할 수 있다.
- GitHub user id 또는 GitHub OAuth provider subject를 외부 identity의 stable key로 사용한다.
- Email, display name, avatar 같은 GitHub profile field는 보조 profile metadata일 뿐 계정의 stable identity key가 아니다.

MVP에서 제외한다.

- email/password signup
- local account registration
- local password
- password reset
- email verification required for signup
- magic link signup/login
- multiple OAuth providers
- Google/Kakao/Naver OAuth
- anonymous user flow

## 3. OAuth Provider Token 정책

GitHub OAuth token과 우리 서비스의 access token/refresh token은 서로 다른 token이다.

- MVP에서 GitHub API 호출이 필요 없다면 GitHub OAuth access token/refresh token은 저장하지 않는다.
- GitHub API 호출이 필요해 token을 저장해야 한다면 암호화, 최소 scope, 만료/회전, 폐기 기준을 함께 명세한 뒤에만 저장한다.
- Controller/API response, log, error에는 GitHub OAuth token, provider raw payload, secret을 노출하지 않는다.
- 가입 실패, 취소, 거부 사유는 사용자에게 과도한 provider 내부 정보를 드러내지 않고 일반화된 메시지로 처리한다.

## 4. Service Token과 Session 정책

- MVP 인증은 cookie 기반 server session을 사용하지 않는다.
- API 요청 인증은 `Authorization: Bearer <access_token>` header를 사용한다.
- Access Token은 stateless하게 검증 가능한 짧은 만료 JWT로 둔다.
- Refresh Token은 Bearer token으로 사용하되, rotation, 만료, revoke, reuse detection 기준을 반드시 둔다.
- Refresh Token 저장소는 특정 인프라에 고정하지 않고 `token store` 추상 기준으로 문서화한다.
- 초기 구현 후보는 RDBMS에 hashed refresh token 또는 token family metadata를 저장하는 방식이다.
- Redis 사용 여부는 아직 결정하지 않는다. Redis는 고성능 revoke list, distributed token state, reuse detection 최적화가 필요해질 때 후속 선택지로 둔다.
- `fully stateless refresh token`을 전제로 로그아웃, 강제 만료, 탈취 대응을 약화시키지 않는다.
- 일반 resource API response, log, error에는 access token, refresh token, GitHub OAuth token, provider raw payload, secret을 노출하지 않는다.

Token issuance/refresh response에서 우리 서비스 access token/refresh token을 어떤 channel로 전달할지는 구현 story에서 별도 승인 기준으로 닫는다. Cookie 기반 server session은 선택지가 아니며, 어떤 방식을 선택하더라도 provider token/raw payload/secret은 response/log/error surface에 노출하지 않는다.

## 5. Acceptance Criteria

1. Given 신규 사용자가 가입하려 할 때, When GitHub OAuth 인증이 성공하면, Then 내부 계정이 생성되거나 기존 GitHub identity와 연결된다.
2. Given GitHub OAuth가 실패하거나 취소될 때, Then 계정은 생성되지 않는다.
3. Given 사용자가 email/password 또는 다른 provider로 가입하려 할 때, Then MVP에서는 지원하지 않는다.
4. Given 일반 resource API response/log/error가 생성될 때, Then access token, refresh token, GitHub OAuth token, provider raw payload, secret은 노출되지 않는다.
5. Given auth token issuance/refresh response를 설계할 때, Then access token/refresh token 전달 channel은 별도 승인 기준으로 닫기 전까지 구현하지 않는다.
