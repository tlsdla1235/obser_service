package com.observation.starter.client;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Locale;

/**
 * JDK HTTP client와 service boundary에서 받은 실패를 heartbeat 계약의 category로 정규화한다.
 */
public final class HeartbeatFailureClassifier {

    private HeartbeatFailureClassifier() {
    }

    /**
     * HTTP status code를 heartbeat failure category로 분류한다.
     */
    public static HeartbeatFailureCategory classifyStatus(int statusCode) {
        if (statusCode == 401) {
            return HeartbeatFailureCategory.UNAUTHORIZED;
        }
        if (statusCode >= 500 && statusCode <= 599) {
            return HeartbeatFailureCategory.SERVER_5XX;
        }
        if (statusCode >= 400 && statusCode <= 499) {
            return HeartbeatFailureCategory.CLIENT_4XX;
        }
        return HeartbeatFailureCategory.UNKNOWN;
    }

    /**
     * exception chain을 따라가며 portal/network 실패 원인을 안정적인 category로 분류한다.
     */
    public static HeartbeatFailureCategory classify(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof PortalHeartbeatException exception) {
                return exception.failureCategory();
            }
            if (current instanceof UnknownHostException || current instanceof UnresolvedAddressException) {
                return HeartbeatFailureCategory.DNS;
            }
            if (current instanceof HttpConnectTimeoutException) {
                return HeartbeatFailureCategory.CONNECT_TIMEOUT;
            }
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return HeartbeatFailureCategory.READ_TIMEOUT;
            }
            if (current instanceof ConnectException exception) {
                return classifyConnectException(exception);
            }
            if (current instanceof SSLException) {
                return HeartbeatFailureCategory.TLS;
            }
        }
        return HeartbeatFailureCategory.UNKNOWN;
    }

    private static HeartbeatFailureCategory classifyConnectException(ConnectException exception) {
        String message = exception.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
            return HeartbeatFailureCategory.CONNECT_TIMEOUT;
        }
        return HeartbeatFailureCategory.CONNECTION_REFUSED;
    }
}
