import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  ApiRequestError,
  AUTH_ENDPOINTS,
  AuthRequiredError,
  AuthorizationLostError,
  CALLBACK_RELAY_REQUEST_OPTIONS,
  GITHUB_OAUTH_CALLBACK_MESSAGE,
  GITHUB_OAUTH_CALLBACK_RELAY_META,
  JSON_ACCEPT_HEADERS,
  JSON_BODY_HEADERS,
  mergeHeaders,
  NO_STORE_REQUEST_OPTIONS,
  normalizeRequiredText,
} from "./api";

const GITHUB_OAUTH_POPUP_NAME = "observationPortalGithubOAuth";
const GITHUB_OAUTH_POPUP_FEATURES = "width=520,height=720";
const GITHUB_OAUTH_WATCH_INTERVAL_MS = 500;
const GITHUB_OAUTH_WATCH_TIMEOUT_MS = 120000;
const AUTH_REQUIRED_MESSAGE = "GitHub 로그인 후 사용할 수 있습니다.";
const AUTH_LOST_MESSAGE = "인증이 만료되었습니다. 다시 로그인해 주세요.";

type AuthStatus = "authenticated" | "authenticating" | "unauthenticated";

interface TokenMemory {
  accessToken: string | null;
  generation: number;
}

interface GithubAuthorizeResponse {
  authorizationUrl?: unknown;
}

interface GithubCallbackSessionResponse {
  accessToken?: unknown;
}

export type AuthFetch = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export interface AuthContextValue {
  authenticated: boolean;
  authFetch: AuthFetch;
  authGeneration: number;
  clearAccessToken: () => void;
  errorMessage: string | null;
  githubLoginLabel: string;
  loginInProgress: boolean;
  startGithubLogin: () => Promise<void>;
  status: AuthStatus;
  statusMessage: string;
}

declare global {
  interface Window {
    observationPortalAuth?: Readonly<{
      clearAccessToken: () => void;
      setAccessToken: (accessToken: string) => void;
    }>;
  }
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * GitHub OAuth relay와 service access token memory state를 앱 전체에 제공한다.
 * token은 URL, cookie, browser storage에 쓰지 않고 이 provider의 React memory 안에서만 유지한다.
 */
export function AuthProvider({ children }: PropsWithChildren) {
  const tokenMemoryRef = useRef<TokenMemory>({ accessToken: null, generation: 0 });
  const [tokenMemory, setTokenMemory] = useState<TokenMemory>(tokenMemoryRef.current);
  const [loginInProgress, setLoginInProgress] = useState(false);
  const [statusMessage, setStatusMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loginInProgressRef = useRef(false);
  const authWindowRef = useRef<Window | null>(null);
  const authWatchTimerRef = useRef<number | null>(null);
  const authWatchStartedAtRef = useRef(0);
  const relayInFlightRef = useRef(false);
  const relayConsumeAttemptedRef = useRef<Set<string>>(new Set());

  const setLoginProgress = useCallback((nextLoginInProgress: boolean) => {
    loginInProgressRef.current = nextLoginInProgress;
    setLoginInProgress(nextLoginInProgress);
  }, []);

  const commitAccessToken = useCallback(
    (rawAccessToken: unknown, nextStatusMessage: string, nextErrorMessage: string | null = null) => {
      const normalizedAccessToken = normalizeAccessToken(rawAccessToken);
      const nextMemory = {
        accessToken: normalizedAccessToken || null,
        generation: tokenMemoryRef.current.generation + 1,
      };

      tokenMemoryRef.current = nextMemory;
      setTokenMemory(nextMemory);
      setStatusMessage(nextStatusMessage);
      setErrorMessage(nextErrorMessage);
    },
    [],
  );

  const clearAccessTokenInternal = useCallback(
    (options: { errorMessage?: string | null; expectedGeneration?: number; statusMessage?: string } = {}) => {
      if (
        typeof options.expectedGeneration === "number" &&
        tokenMemoryRef.current.generation !== options.expectedGeneration
      ) {
        return;
      }

      const nextMemory = {
        accessToken: null,
        generation: tokenMemoryRef.current.generation + 1,
      };

      tokenMemoryRef.current = nextMemory;
      setTokenMemory(nextMemory);
      setStatusMessage(options.statusMessage ?? "GitHub 로그인이 해제되었습니다.");
      setErrorMessage(options.errorMessage ?? null);
    },
    [],
  );

  const clearAccessToken = useCallback(() => {
    clearAccessTokenInternal();
  }, [clearAccessTokenInternal]);

  const clearGithubAuthWatcher = useCallback(() => {
    if (authWatchTimerRef.current !== null) {
      window.clearInterval(authWatchTimerRef.current);
      authWatchTimerRef.current = null;
    }
    authWatchStartedAtRef.current = 0;
  }, []);

  const closeGithubAuthWindow = useCallback(
    (authWindow: Window | null) => {
      try {
        if (authWindow && !authWindow.closed) {
          authWindow.close();
        }
      } catch {
        // 브라우저가 popup 제어를 막아도 auth state 정리는 계속 진행한다.
      }

      if (authWindowRef.current === authWindow) {
        authWindowRef.current = null;
      }
      clearGithubAuthWatcher();
    },
    [clearGithubAuthWatcher],
  );

  const consumeGithubCallbackRelay = useCallback(
    async (relayIdCandidate: unknown) => {
      const relayId = normalizeRelayId(relayIdCandidate);
      if (!relayId || relayInFlightRef.current || relayConsumeAttemptedRef.current.has(relayId)) {
        return;
      }

      relayInFlightRef.current = true;
      relayConsumeAttemptedRef.current.add(relayId);
      setLoginProgress(true);
      setStatusMessage("GitHub 인증 결과를 확인하는 중입니다.");
      setErrorMessage(null);

      try {
        const response = await fetch(AUTH_ENDPOINTS.githubCallbackTokens, {
          ...CALLBACK_RELAY_REQUEST_OPTIONS,
          method: "POST",
          headers: JSON_BODY_HEADERS,
          body: JSON.stringify({ relayId }),
        });

        if (!response.ok) {
          throw new ApiRequestError("github_callback_relay_failed", response.status);
        }

        const data = (await response.json()) as GithubCallbackSessionResponse;
        const normalizedAccessToken = normalizeAccessToken(data.accessToken);
        if (!normalizedAccessToken) {
          throw new ApiRequestError("github_callback_relay_malformed");
        }

        commitAccessToken(normalizedAccessToken, "GitHub 로그인이 완료되었습니다.");
        setLoginProgress(false);
        closeGithubAuthWindow(authWindowRef.current);
      } catch {
        setStatusMessage("GitHub 로그인을 완료할 수 없습니다. 다시 시도해 주세요.");
        setErrorMessage("GitHub 로그인을 완료할 수 없습니다. 다시 시도해 주세요.");
        setLoginProgress(false);
      } finally {
        relayInFlightRef.current = false;
      }
    },
    [closeGithubAuthWindow, commitAccessToken, setLoginProgress],
  );

  const watchGithubAuthWindow = useCallback(
    (authWindow: Window) => {
      clearGithubAuthWatcher();
      authWatchStartedAtRef.current = Date.now();
      authWatchTimerRef.current = window.setInterval(() => {
        if (relayInFlightRef.current) {
          return;
        }

        if (!authWindow || authWindow.closed) {
          clearGithubAuthWatcher();
          if (!tokenMemoryRef.current.accessToken) {
            setStatusMessage("GitHub 인증 창이 닫혔습니다. 로그인을 다시 시도해 주세요.");
            setErrorMessage("GitHub 인증 창이 닫혔습니다. 로그인을 다시 시도해 주세요.");
            setLoginProgress(false);
          }
          return;
        }

        const relayId = githubCallbackRelayIdFromWindow(authWindow);
        if (relayId) {
          void consumeGithubCallbackRelay(relayId);
          return;
        }

        if (Date.now() - authWatchStartedAtRef.current > GITHUB_OAUTH_WATCH_TIMEOUT_MS) {
          clearGithubAuthWatcher();
          setStatusMessage("GitHub 로그인 완료를 확인하지 못했습니다. 다시 시도해 주세요.");
          setErrorMessage("GitHub 로그인 완료를 확인하지 못했습니다. 다시 시도해 주세요.");
          setLoginProgress(false);
        }
      }, GITHUB_OAUTH_WATCH_INTERVAL_MS);
    },
    [clearGithubAuthWatcher, consumeGithubCallbackRelay, setLoginProgress],
  );

  const openGithubAuthWindow = useCallback(() => {
    if (typeof window.open !== "function") {
      return null;
    }

    const authWindow = window.open("", GITHUB_OAUTH_POPUP_NAME, GITHUB_OAUTH_POPUP_FEATURES);
    if (authWindow) {
      authWindowRef.current = authWindow;
    }

    return authWindow;
  }, []);

  const startGithubLogin = useCallback(async () => {
    if (loginInProgressRef.current || relayInFlightRef.current) {
      return;
    }

    setLoginProgress(true);
    setStatusMessage("");
    setErrorMessage(null);

    const authWindow = openGithubAuthWindow();
    let waitingForCallback = false;

    try {
      const response = await fetch(AUTH_ENDPOINTS.githubAuthorize, {
        ...NO_STORE_REQUEST_OPTIONS,
        headers: JSON_ACCEPT_HEADERS,
      });

      if (!response.ok) {
        throw new ApiRequestError("github_authorize_failed", response.status);
      }

      const data = (await response.json()) as GithubAuthorizeResponse;
      const authorizationUrl = normalizeAuthorizationUrl(data.authorizationUrl);
      if (!authorizationUrl) {
        throw new ApiRequestError("github_authorize_malformed");
      }

      if (authWindow && shouldUsePopupAuthFlow(authorizationUrl)) {
        authWindow.location.assign(authorizationUrl);
        watchGithubAuthWindow(authWindow);
        setStatusMessage("GitHub 인증 창에서 로그인을 완료해 주세요.");
        waitingForCallback = true;
        return;
      }

      closeGithubAuthWindow(authWindow);
      setStatusMessage("GitHub 인증 화면으로 이동합니다.");
      waitingForCallback = true;
      window.location.assign(authorizationUrl);
    } catch {
      closeGithubAuthWindow(authWindow);
      setStatusMessage("GitHub 로그인을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요.");
      setErrorMessage("GitHub 로그인을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      if (!waitingForCallback) {
        setLoginProgress(false);
      }
    }
  }, [closeGithubAuthWindow, openGithubAuthWindow, setLoginProgress, watchGithubAuthWindow]);

  const authFetch = useCallback<AuthFetch>(
    async (input, init = {}) => {
      const requestTokenMemory = tokenMemoryRef.current;
      if (!requestTokenMemory.accessToken) {
        throw new AuthRequiredError(AUTH_REQUIRED_MESSAGE);
      }

      const response = await fetch(input, {
        ...init,
        headers: mergeHeaders(JSON_ACCEPT_HEADERS, init.headers, {
          Authorization: `Bearer ${requestTokenMemory.accessToken}`,
        }),
      });

      if (response.status === 401) {
        clearAccessTokenInternal({
          expectedGeneration: requestTokenMemory.generation,
          statusMessage: AUTH_LOST_MESSAGE,
          errorMessage: AUTH_LOST_MESSAGE,
        });
        throw new AuthorizationLostError(AUTH_LOST_MESSAGE);
      }

      return response;
    },
    [clearAccessTokenInternal],
  );

  useEffect(() => {
    const handleGithubOAuthMessage = (event: MessageEvent) => {
      if (!isTrustedGithubOAuthMessage(event)) {
        return;
      }

      void consumeGithubCallbackRelay(event.data.relayId);
    };

    window.addEventListener("message", handleGithubOAuthMessage);
    return () => window.removeEventListener("message", handleGithubOAuthMessage);
  }, [consumeGithubCallbackRelay]);

  useEffect(() => {
    const bridge = Object.freeze({
      clearAccessToken,
      setAccessToken: (nextAccessToken: string) => {
        const normalizedAccessToken = normalizeAccessToken(nextAccessToken);
        if (!normalizedAccessToken) {
          clearAccessTokenInternal({ statusMessage: AUTH_REQUIRED_MESSAGE });
          return;
        }

        commitAccessToken(normalizedAccessToken, "GitHub 로그인이 완료되었습니다.");
      },
    });

    window.observationPortalAuth = bridge;
    return () => {
      if (window.observationPortalAuth === bridge) {
        delete window.observationPortalAuth;
      }
    };
  }, [clearAccessToken, clearAccessTokenInternal, commitAccessToken]);

  useEffect(() => {
    return () => {
      clearGithubAuthWatcher();
      closeGithubAuthWindow(authWindowRef.current);
    };
  }, [clearGithubAuthWatcher, closeGithubAuthWindow]);

  const authenticated = Boolean(tokenMemory.accessToken);
  const status: AuthStatus = loginInProgress ? "authenticating" : authenticated ? "authenticated" : "unauthenticated";
  const githubLoginLabel = loginInProgress ? "로그인 중..." : authenticated ? "GitHub 로그인됨" : "GitHub 로그인";

  const value = useMemo<AuthContextValue>(
    () => ({
      authenticated,
      authFetch,
      authGeneration: tokenMemory.generation,
      clearAccessToken,
      errorMessage,
      githubLoginLabel,
      loginInProgress,
      startGithubLogin,
      status,
      statusMessage,
    }),
    [
      authenticated,
      authFetch,
      clearAccessToken,
      errorMessage,
      githubLoginLabel,
      loginInProgress,
      startGithubLogin,
      status,
      statusMessage,
      tokenMemory.generation,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("useAuth는 AuthProvider 안에서만 사용할 수 있습니다.");
  }
  return value;
}

function normalizeAccessToken(value: unknown): string {
  return normalizeRequiredText(value);
}

function normalizeRelayId(value: unknown): string {
  return normalizeRequiredText(value);
}

function normalizeAuthorizationUrl(value: unknown): string {
  const text = normalizeRequiredText(value);
  if (!text) {
    return "";
  }

  try {
    const url = new URL(text, window.location.origin);
    return url.protocol === "http:" || url.protocol === "https:" ? url.href : "";
  } catch {
    return "";
  }
}

function shouldUsePopupAuthFlow(authorizationUrl: string): boolean {
  const redirectOrigin = authorizationRedirectOrigin(authorizationUrl);
  return redirectOrigin.length === 0 || redirectOrigin === dashboardOrigin();
}

function authorizationRedirectOrigin(authorizationUrl: string): string {
  try {
    const redirectUri = new URL(authorizationUrl).searchParams.get("redirect_uri");
    return redirectUri ? new URL(redirectUri).origin : "";
  } catch {
    return "";
  }
}

function dashboardOrigin(): string {
  return window.location?.origin ?? "";
}

function isTrustedGithubOAuthMessage(event: MessageEvent): event is MessageEvent<{ relayId: string; type: string }> {
  return Boolean(
    event.origin === dashboardOrigin() &&
      isObjectValue(event.data) &&
      event.data.type === GITHUB_OAUTH_CALLBACK_MESSAGE &&
      normalizeRelayId(event.data.relayId),
  );
}

function githubCallbackRelayIdFromWindow(authWindow: Window): string | null {
  try {
    const marker = authWindow.document?.querySelector(`meta[name="${GITHUB_OAUTH_CALLBACK_RELAY_META}"]`);
    return marker ? normalizeRelayId(marker.getAttribute("content")) || null : null;
  } catch {
    return null;
  }
}

function isObjectValue(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
