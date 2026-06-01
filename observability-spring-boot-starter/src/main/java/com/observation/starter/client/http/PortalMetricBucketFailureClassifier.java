package com.observation.starter.client.http;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Locale;

/**
 * JDK HTTP client에서 발생한 bucket ingest 실패를 raw secret 없이 안정적인 category로 정규화한다.
 */
final class PortalMetricBucketFailureClassifier {

    private PortalMetricBucketFailureClassifier() {
    }

    /**
     * HTTP status code를 bucket ingest 실패 category로 분류한다.
     */
    static PortalMetricBucketFailureCategory classifyStatus(int statusCode) {
        if (statusCode == 401) {
            return PortalMetricBucketFailureCategory.UNAUTHORIZED;
        }
        if (statusCode >= 500 && statusCode <= 599) {
            return PortalMetricBucketFailureCategory.SERVER_5XX;
        }
        if (statusCode >= 400 && statusCode <= 499) {
            return PortalMetricBucketFailureCategory.CLIENT_4XX;
        }
        return PortalMetricBucketFailureCategory.UNKNOWN;
    }

    /**
     * exception chain을 따라가며 network/transport 실패 원인을 category로 분류한다.
     */
    static PortalMetricBucketFailureCategory classify(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof PortalMetricBucketException exception) {
                return exception.failureCategory();
            }
            if (current instanceof UnknownHostException || current instanceof UnresolvedAddressException) {
                return PortalMetricBucketFailureCategory.DNS;
            }
            if (current instanceof HttpConnectTimeoutException) {
                return PortalMetricBucketFailureCategory.CONNECT_TIMEOUT;
            }
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return PortalMetricBucketFailureCategory.READ_TIMEOUT;
            }
            if (current instanceof ConnectException exception) {
                return classifyConnectException(exception);
            }
            if (current instanceof SSLException) {
                return PortalMetricBucketFailureCategory.TLS;
            }
        }
        return PortalMetricBucketFailureCategory.UNKNOWN;
    }

    private static PortalMetricBucketFailureCategory classifyConnectException(ConnectException exception) {
        String message = exception.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
            return PortalMetricBucketFailureCategory.CONNECT_TIMEOUT;
        }
        return PortalMetricBucketFailureCategory.CONNECTION_REFUSED;
    }
}
