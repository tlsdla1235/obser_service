package com.observation.smoke.startertosnapshot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * HTTP server observation을 만들기 위한 smoke app의 최소 endpoint다.
 *
 * <p>응답에는 project key, token, raw ingest payload 같은 secret 성격의 값을 담지 않는다.</p>
 */
@RestController
public class SmokePingController {

    /**
     * starter HTTP observation capture 경로를 자극하기 위한 가벼운 ping 응답을 반환한다.
     */
    @GetMapping("/smoke/ping")
    public Map<String, String> ping() {
        return Map.of(
                "status", "ok",
                "observedAt", Instant.now().toString());
    }
}
