/**
 * 프론트 인증/fetch foundation에서 공유하는 API 경계와 안전한 fetch option이다.
 * 후속 story의 read model adapter가 이 파일에 섞이지 않도록 endpoint 상수와 공통 에러만 둔다.
 */

export const AUTH_ENDPOINTS = {
  githubAuthorize: "/api/auth/github/authorize",
  githubCallbackTokens: "/api/auth/github/callback/tokens",
} as const;

export const READ_MODEL_ENDPOINTS = {
  projects: "/api/projects",
} as const;

export const GITHUB_OAUTH_CALLBACK_MESSAGE = "observation-portal.github-oauth-complete";
export const GITHUB_OAUTH_CALLBACK_RELAY_META = "observation-github-callback-relay-id";

export const JSON_ACCEPT_HEADERS: HeadersInit = {
  Accept: "application/json",
};

export const JSON_BODY_HEADERS: HeadersInit = {
  Accept: "application/json",
  "Content-Type": "application/json",
};

export const NO_STORE_REQUEST_OPTIONS = {
  cache: "no-store",
} as const satisfies Pick<RequestInit, "cache">;

export const CALLBACK_RELAY_REQUEST_OPTIONS = {
  cache: "no-store",
  referrerPolicy: "no-referrer",
} as const satisfies Pick<RequestInit, "cache" | "referrerPolicy">;

export const SECRET_BEARING_REQUEST_OPTIONS = NO_STORE_REQUEST_OPTIONS;
export const CREDENTIAL_LIFECYCLE_REQUEST_OPTIONS = NO_STORE_REQUEST_OPTIONS;

/**
 * 여러 header source를 순서대로 합쳐 뒤쪽 값이 앞쪽 값을 덮게 한다.
 * Authorization header는 authFetch가 마지막에 붙여 caller override를 막는다.
 */
export function mergeHeaders(...headerSources: Array<HeadersInit | undefined>): Headers {
  const headers = new Headers();

  for (const source of headerSources) {
    if (!source) {
      continue;
    }

    new Headers(source).forEach((value, key) => {
      headers.set(key, value);
    });
  }

  return headers;
}

export function normalizeRequiredText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

export class ApiRequestError extends Error {
  readonly status?: number;

  constructor(message = "api_request_failed", status?: number) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
  }
}

export class AuthRequiredError extends Error {
  constructor(message = "GitHub 로그인 후 사용할 수 있습니다.") {
    super(message);
    this.name = "AuthRequiredError";
  }
}

export class AuthorizationLostError extends AuthRequiredError {
  constructor(message = "인증이 만료되었습니다. 다시 로그인해 주세요.") {
    super(message);
    this.name = "AuthorizationLostError";
  }
}

/**
 * read-only resource 응답을 JSON으로 파싱하고 HTTP status를 보존한 안전한 에러로 변환한다.
 * backend payload나 token 값을 에러 메시지로 노출하지 않기 위해 고정된 내부 code만 사용한다.
 */
export async function readJsonResource<TData>(response: Response): Promise<TData> {
  if (!response.ok) {
    throw new ApiRequestError("api_request_failed", response.status);
  }

  try {
    return (await response.json()) as TData;
  } catch {
    throw new ApiRequestError("api_response_malformed", response.status);
  }
}
