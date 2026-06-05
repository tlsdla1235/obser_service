package com.observation.portal.domain.ingest.queue;

/**
 * ingest request path가 DB direct insert를 수행할지 queue enqueue boundary로 갈지 결정하는 닫힌 mode 목록이다.
 */
public enum IngestBufferMode {
    DIRECT,
    FAKE,
    SQS;

    /**
     * 기존 repository insert path를 그대로 사용하는 rollback/default mode인지 확인한다.
     */
    public boolean isDirect() {
        return this == DIRECT;
    }

    /**
     * request thread에서 persistence를 수행하지 않는 enqueue mode인지 확인한다.
     */
    public boolean isQueueMode() {
        return this == FAKE || this == SQS;
    }
}
