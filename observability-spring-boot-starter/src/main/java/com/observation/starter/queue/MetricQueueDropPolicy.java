package com.observation.starter.queue;

/**
 * bounded metric queue가 가득 찼을 때 적용할 drop policy다.
 *
 * <p>MVP는 durable delivery보다 host application request safety를 우선하므로, 어떤 정책이든
 * enqueue 호출자는 대기하거나 예외를 받지 않는다.</p>
 */
public enum MetricQueueDropPolicy {

    /**
     * 이미 queue에 있던 bucket은 유지하고 새로 들어온 bucket을 버린다.
     */
    DROP_NEWEST,

    /**
     * 가장 오래된 bucket을 버리고 새 bucket을 queue에 넣는다.
     */
    DROP_OLDEST
}
