import { type DependencyList, useCallback, useEffect, useRef, useState } from "react";
import { AuthRequiredError } from "./api";
import { type AuthFetch, useAuth } from "./auth";

export interface ApiResourceRequestContext {
  authFetch: AuthFetch;
  sequence: number;
  signal: AbortSignal;
}

export interface UseApiResourceOptions<TData> {
  dependencies?: DependencyList;
  enabled?: boolean;
  initialData?: TData | null;
  request: (context: ApiResourceRequestContext) => Promise<TData>;
}

export interface ApiResourceState<TData> {
  data: TData | null;
  error: Error | null;
  loading: boolean;
  reload: () => void;
  sequence: number;
}

/**
 * 인증된 resource read를 위한 공통 hook이다.
 * dependency/token 변경, 부모 선택 변경, unmount 뒤에 도착한 응답은 request sequence로 최신 state를 덮지 못하게 한다.
 */
export function useApiResource<TData>({
  dependencies = [],
  enabled = true,
  initialData = null,
  request,
}: UseApiResourceOptions<TData>): ApiResourceState<TData> {
  const { authFetch, authGeneration, authenticated } = useAuth();
  const [reloadSequence, setReloadSequence] = useState(0);
  const requestSequenceRef = useRef(0);
  const [state, setState] = useState<Omit<ApiResourceState<TData>, "reload">>({
    data: initialData,
    error: null,
    loading: false,
    sequence: 0,
  });

  const reload = useCallback(() => {
    setReloadSequence((current) => current + 1);
  }, []);

  useEffect(() => {
    const sequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = sequence;

    if (!enabled) {
      setState({ data: initialData, error: null, loading: false, sequence });
      return () => {
        requestSequenceRef.current += 1;
      };
    }

    if (!authenticated) {
      setState({
        data: initialData,
        error: new AuthRequiredError(),
        loading: false,
        sequence,
      });
      return () => {
        requestSequenceRef.current += 1;
      };
    }

    const abortController = new AbortController();
    let active = true;

    setState({ data: initialData, error: null, loading: true, sequence });

    request({ authFetch, sequence, signal: abortController.signal })
      .then((data) => {
        if (!active || abortController.signal.aborted || requestSequenceRef.current !== sequence) {
          return;
        }

        setState({ data, error: null, loading: false, sequence });
      })
      .catch((error: unknown) => {
        if (!active || abortController.signal.aborted || requestSequenceRef.current !== sequence) {
          return;
        }

        setState({
          data: initialData,
          error: normalizeResourceError(error),
          loading: false,
          sequence,
        });
      });

    return () => {
      active = false;
      abortController.abort();
      if (requestSequenceRef.current === sequence) {
        requestSequenceRef.current += 1;
      }
    };
  }, [authFetch, authGeneration, authenticated, enabled, initialData, reloadSequence, request, ...dependencies]);

  return {
    ...state,
    reload,
  };
}

function normalizeResourceError(error: unknown): Error {
  return error instanceof Error ? error : new Error("resource_request_failed");
}
