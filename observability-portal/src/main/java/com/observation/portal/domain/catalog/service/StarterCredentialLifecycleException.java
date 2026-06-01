package com.observation.portal.domain.catalog.service;

/**
 * starter credential lifecycle API가 resource existence를 숨겨야 할 때 사용하는 예외다.
 */
public class StarterCredentialLifecycleException extends RuntimeException {

    private StarterCredentialLifecycleException(String message) {
        super(message);
    }

    /**
     * project가 없거나 lifecycle API에서 노출할 수 없는 상태일 때 404로 매핑한다.
     */
    public static StarterCredentialLifecycleException notFound() {
        return new StarterCredentialLifecycleException("starter credential resource not found");
    }
}
