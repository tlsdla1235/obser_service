package com.observation.starter.client;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeartbeatFailureClassifierTest {

    @Test
    void classifiesNetworkFailuresIntoStableHeartbeatCategories() {
        assertEquals(HeartbeatFailureCategory.DNS,
                HeartbeatFailureClassifier.classify(new RuntimeException(new UnknownHostException("portal.local"))));
        assertEquals(HeartbeatFailureCategory.CONNECT_TIMEOUT,
                HeartbeatFailureClassifier.classify(new RuntimeException(new HttpConnectTimeoutException("timeout"))));
        assertEquals(HeartbeatFailureCategory.READ_TIMEOUT,
                HeartbeatFailureClassifier.classify(new RuntimeException(new HttpTimeoutException("timeout"))));
        assertEquals(HeartbeatFailureCategory.READ_TIMEOUT,
                HeartbeatFailureClassifier.classify(new RuntimeException(new SocketTimeoutException("timeout"))));
        assertEquals(HeartbeatFailureCategory.CONNECTION_REFUSED,
                HeartbeatFailureClassifier.classify(new RuntimeException(new ConnectException("Connection refused"))));
        assertEquals(HeartbeatFailureCategory.TLS,
                HeartbeatFailureClassifier.classify(new RuntimeException(new SSLException("handshake failed"))));
    }

    @Test
    void preservesCategoryFromPortalHeartbeatException() {
        PortalHeartbeatException exception = PortalHeartbeatException.forStatus(401);

        assertEquals(HeartbeatFailureCategory.UNAUTHORIZED, HeartbeatFailureClassifier.classify(exception));
    }
}
