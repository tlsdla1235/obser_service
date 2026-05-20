package com.observation.portal.domain.ingest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * validation을 통과한 ingest request model의 deterministic payload hash를 계산한다.
 *
 * <p>Jackson request model에 남은 지원 field만 직렬화하므로 unknown field는 hash와 persistence 후보에 반영되지 않는다.</p>
 */
@Component
public class IngestPayloadHasher {

    private final ObjectMapper objectMapper;

    /**
     * canonical 직렬화에 사용할 ObjectMapper를 주입한다.
     */
    public IngestPayloadHasher(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 지원 ingest field만 기준으로 SHA-256 hex payload hash를 반환한다.
     */
    public String sha256(IngestEnvelopeRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            byte[] payload = objectMapper.writeValueAsBytes(request);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(payload);
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("ingest payload canonicalization failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
