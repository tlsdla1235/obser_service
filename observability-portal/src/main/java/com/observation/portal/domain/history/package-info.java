/**
 * Stored dashboard snapshot을 operational event history API skeleton으로 노출하는 feature package다.
 *
 * <p>이 package는 current dashboard 재계산이나 별도 event store가 아니라 `dashboard_snapshots` source boundary 위에서
 * compact response shape와 5.9-b 확장 지점을 제공한다.</p>
 */
package com.observation.portal.domain.history;
