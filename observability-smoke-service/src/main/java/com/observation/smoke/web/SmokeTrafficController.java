package com.observation.smoke.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * local smoke traffic을 만들기 위한 최소 HTTP endpoint controller다.
 *
 * <p>controller는 Spring MVC request handling만 수행하며, starter의 portal client, envelope builder,
 * queue flush 경계를 직접 호출하지 않는다.</p>
 */
@RestController
@RequestMapping("/smoke")
public class SmokeTrafficController {

    private static final long SLOW_ENDPOINT_DELAY_MILLIS = 150L;

    /**
     * primary green path에서 사용할 빠른 200 OK 요청 traffic을 만든다.
     */
    @GetMapping("/ok")
    public SmokeTrafficResponse ok() {
        return new SmokeTrafficResponse("ok", "/smoke/ok", "green_path_candidate");
    }

    /**
     * troubleshooting 후보용으로 bounded delay 후 200 OK를 반환한다.
     */
    @GetMapping("/slow")
    public SmokeTrafficResponse slow() throws InterruptedException {
        Thread.sleep(SLOW_ENDPOINT_DELAY_MILLIS);
        return new SmokeTrafficResponse("slow", "/smoke/slow", "bounded_delay_candidate");
    }

    /**
     * primary completion path와 분리된 intentional 5xx 후보 traffic을 만든다.
     */
    @GetMapping("/error-candidate")
    public ResponseEntity<SmokeTrafficResponse> errorCandidate() {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new SmokeTrafficResponse(
                        "error_candidate",
                        "/smoke/error-candidate",
                        "intentional_error_candidate"));
    }

    /**
     * smoke endpoint 응답의 bounded shape다. Secret이나 runtime 설정값은 포함하지 않는다.
     */
    public record SmokeTrafficResponse(String status, String path, String meaning) {
    }
}
