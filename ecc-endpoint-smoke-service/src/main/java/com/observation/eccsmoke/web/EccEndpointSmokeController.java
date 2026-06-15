package com.observation.eccsmoke.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.List;

/**
 * DevelopYour/ECC-back의 주요 controller route와 비슷한 shape의 응답을 반환하는 smoke controller다.
 *
 * <p>각 mapping은 Spring MVC가 route template을 인식하도록 명시적으로 선언하고, 응답은 DB나 외부 API를
 * 호출하지 않는 bounded fixture만 반환한다.</p>
 */
@RestController
public class EccEndpointSmokeController {

    private static final String SOURCE_PROJECT = "DevelopYour/ECC-back";
    private static final long MAX_DELAY_MILLIS = 950L;

    private static final List<RouteSpec> ROUTE_SPECS = List.of(
            new RouteSpec("GET", "/api/auth/signup/check-id", "auth"),
            new RouteSpec("POST", "/api/auth/signup", "auth"),
            new RouteSpec("POST", "/api/auth/login", "auth"),
            new RouteSpec("POST", "/api/auth/refresh", "auth"),
            new RouteSpec("POST", "/api/auth/logout", "auth"),
            new RouteSpec("GET", "/api/users/me", "member"),
            new RouteSpec("PATCH", "/api/users/me/update", "member"),
            new RouteSpec("DELETE", "/api/users/me/cancel", "member"),
            new RouteSpec("PATCH", "/api/users/me/password", "member"),
            new RouteSpec("PATCH", "/api/users/me/level", "member"),
            new RouteSpec("POST", "/api/users/me/withdraw", "member"),
            new RouteSpec("GET", "/api/major", "member"),
            new RouteSpec("GET", "/api/admin/users", "admin-member"),
            new RouteSpec("GET", "/api/admin/users/{uuid}", "admin-member"),
            new RouteSpec("GET", "/api/admin/users/status/{status}", "admin-member"),
            new RouteSpec("GET", "/api/admin/users/pending", "admin-member"),
            new RouteSpec("GET", "/api/admin/users/level", "admin-member"),
            new RouteSpec("GET", "/api/admin/users/level/{level}", "admin-member"),
            new RouteSpec("GET", "/api/admin/users/filter", "admin-member"),
            new RouteSpec("PATCH", "/api/admin/users/{uuid}/approve", "admin-member"),
            new RouteSpec("DELETE", "/api/admin/users/{uuid}/reject", "admin-member"),
            new RouteSpec("PATCH", "/api/admin/users/{uuid}/status", "admin-member"),
            new RouteSpec("PATCH", "/api/admin/users/{uuid}/level", "admin-member"),
            new RouteSpec("PATCH", "/api/admin/users/level/{requestId}/approve", "admin-member"),
            new RouteSpec("PATCH", "/api/admin/users/level/{requestId}/reject", "admin-member"),
            new RouteSpec("GET", "/api/admin/main/summary", "admin-main"),
            new RouteSpec("GET", "/api/teams/regular/apply/status", "team-regular-apply"),
            new RouteSpec("POST", "/api/teams/regular/apply", "team-regular-apply"),
            new RouteSpec("GET", "/api/teams/regular/apply", "team-regular-apply"),
            new RouteSpec("PUT", "/api/teams/regular/apply", "team-regular-apply"),
            new RouteSpec("DELETE", "/api/teams/regular/apply", "team-regular-apply"),
            new RouteSpec("GET", "/api/teams/one-time", "team-one-time"),
            new RouteSpec("GET", "/api/teams/one-time/status/{status}", "team-one-time"),
            new RouteSpec("GET", "/api/teams/one-time/{teamId}", "team-one-time"),
            new RouteSpec("POST", "/api/teams/one-time", "team-one-time"),
            new RouteSpec("PUT", "/api/teams/one-time/{teamId}", "team-one-time"),
            new RouteSpec("PATCH", "/api/teams/one-time/{teamId}/apply", "team-one-time"),
            new RouteSpec("DELETE", "/api/teams/one-time/{teamId}/apply", "team-one-time"),
            new RouteSpec("DELETE", "/api/teams/one-time/{teamId}", "team-one-time"),
            new RouteSpec("GET", "/api/teams/me", "team"),
            new RouteSpec("GET", "/api/teams/me/regular", "team"),
            new RouteSpec("GET", "/api/teams/me/one-time", "team"),
            new RouteSpec("GET", "/api/teams/{teamId}", "team"),
            new RouteSpec("GET", "/api/teams/subjects", "team"),
            new RouteSpec("GET", "/api/admin/teams", "admin-team"),
            new RouteSpec("POST", "/api/admin/teams", "admin-team"),
            new RouteSpec("GET", "/api/admin/teams/{teamId}", "admin-team"),
            new RouteSpec("GET", "/api/admin/teams/{teamId}/report/{reportId}", "admin-team"),
            new RouteSpec("GET", "/api/admin/teams/one-time/{teamId}/report", "admin-team"),
            new RouteSpec("GET", "/api/admin/teams/{teamId}/{week}/report", "admin-team"),
            new RouteSpec("PATCH", "/api/admin/teams/{teamId}/{week}/report/grade", "admin-team"),
            new RouteSpec("DELETE", "/api/admin/teams/one-time/{teamId}", "admin-team"),
            new RouteSpec("PATCH", "/api/admin/teams/{teamId}/score", "admin-team"),
            new RouteSpec("GET", "/api/admin/teams/{teamId}/members", "admin-team"),
            new RouteSpec("POST", "/api/admin/teams/{teamId}/members", "admin-team"),
            new RouteSpec("DELETE", "/api/admin/teams/{teamId}/members/{memberUuid}", "admin-team"),
            new RouteSpec("GET", "/api/admin/teams/{teamId}/attendance", "admin-team"),
            new RouteSpec("GET", "/api/admin/teams/reports/status", "admin-team"),
            new RouteSpec("GET", "/api/admin/team-match/applications", "admin-team-match"),
            new RouteSpec("GET", "/api/admin/team-match", "admin-team-match"),
            new RouteSpec("POST", "/api/admin/team-match", "admin-team-match"),
            new RouteSpec("GET", "/api/admin/content/categories", "admin-content"),
            new RouteSpec("POST", "/api/admin/content/categories", "admin-content"),
            new RouteSpec("PUT", "/api/admin/content/categories/{categoryId}", "admin-content"),
            new RouteSpec("DELETE", "/api/admin/content/categories/{categoryId}", "admin-content"),
            new RouteSpec("GET", "/api/admin/content/topics", "admin-content"),
            new RouteSpec("POST", "/api/admin/content/topics", "admin-content"),
            new RouteSpec("PUT", "/api/admin/content/topics/{topicId}", "admin-content"),
            new RouteSpec("DELETE", "/api/admin/content/topics/{topicId}", "admin-content"),
            new RouteSpec("GET", "/api/admin/setting", "admin-setting"),
            new RouteSpec("GET", "/api/admin/setting/semester", "admin-setting"),
            new RouteSpec("POST", "/api/admin/setting/semester", "admin-setting"),
            new RouteSpec("GET", "/api/admin/setting/study-recruitment", "admin-setting"),
            new RouteSpec("PATCH", "/api/admin/setting/study-recruitment", "admin-setting"),
            new RouteSpec("POST", "/chat", "ai"),
            new RouteSpec("GET", "/api/study/team/{teamId}/overview", "study"),
            new RouteSpec("POST", "/api/study/team/{teamId}", "study"),
            new RouteSpec("GET", "/api/study/{studyId}", "study"),
            new RouteSpec("GET", "/api/study/team/{teamId}/topic", "study"),
            new RouteSpec("POST", "/api/study/{studyId}/topic", "study"),
            new RouteSpec("POST", "/api/study/{studyId}/ai-help", "study"),
            new RouteSpec("POST", "/api/study/{studyId}/general/corrections", "study"),
            new RouteSpec("POST", "/api/study/{studyId}/general/vocabs", "study"),
            new RouteSpec("POST", "/api/study/{studyId}/general/ai-help", "study"),
            new RouteSpec("PUT", "/api/study/{studyId}/general", "study"),
            new RouteSpec("PUT", "/api/study/{studyId}", "study"),
            new RouteSpec("GET", "/api/study/report/{reportId}", "study"),
            new RouteSpec("PATCH", "/api/study/report/{reportId}", "study"),
            new RouteSpec("GET", "/api/review/me/report/{reportId}", "review"),
            new RouteSpec("GET", "/api/review/me", "review"),
            new RouteSpec("GET", "/api/review/me/{reviewId}", "review"),
            new RouteSpec("POST", "/api/review/me/{reviewId}/test", "review"),
            new RouteSpec("PATCH", "/api/review/me/{reviewId}/test", "review"));

    /**
     * smoke 서버가 포함하는 ECC route 목록을 반환한다. Polling script가 호출 후보를 만들 때 사용한다.
     */
    @GetMapping("/api/ecc-smoke/routes")
    public EccSmokeEnvelope<RouteCatalogResponse> routes() {
        RouteCatalogResponse data = new RouteCatalogResponse(
                SOURCE_PROJECT,
                ROUTE_SPECS.size(),
                ROUTE_SPECS);
        return EccSmokeEnvelope.success("ECC endpoint smoke route 목록입니다.", data);
    }

    /**
     * 의도적인 500 응답을 만들어 portal과 starter의 error path 집계를 확인한다.
     */
    @GetMapping("/api/ecc-smoke/error-500")
    public ResponseEntity<EccSmokeEnvelope<EccEndpointResponse>> intentionalServerError(
            @RequestParam(defaultValue = "0") long delayMillis,
            HttpServletRequest request) {
        sleepBounded(delayMillis);
        EccEndpointResponse data = responseFor("GET", "smoke-error", request);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(EccSmokeEnvelope.error("의도적으로 발생시킨 500 응답입니다.", data));
    }

    /**
     * 의도적으로 늦게 응답해 starter local percentile과 duration bucket의 slow path 집계를 확인한다.
     *
     * <p>기본 delay는 850ms로, 현재 histogram의 500ms 초과/1000ms 이하 bucket에 안전하게 들어간다.</p>
     */
    @GetMapping("/api/ecc-smoke/slow-p99")
    public EccSmokeEnvelope<EccEndpointResponse> intentionalSlowResponse(
            @RequestParam(defaultValue = "850") long delayMillis,
            HttpServletRequest request) {
        sleepBounded(delayMillis);
        return EccSmokeEnvelope.success(
                "의도적으로 늦게 응답한 smoke endpoint입니다.",
                responseFor("GET", "smoke-latency", request));
    }

    /**
     * ECC-back의 GET endpoint shape를 DB 접근 없이 수락한다.
     */
    @GetMapping({
            "/api/auth/signup/check-id",
            "/api/users/me",
            "/api/major",
            "/api/admin/users",
            "/api/admin/users/{uuid}",
            "/api/admin/users/status/{status}",
            "/api/admin/users/pending",
            "/api/admin/users/level",
            "/api/admin/users/level/{level}",
            "/api/admin/users/filter",
            "/api/admin/main/summary",
            "/api/teams/regular/apply/status",
            "/api/teams/regular/apply",
            "/api/teams/one-time",
            "/api/teams/one-time/status/{status}",
            "/api/teams/one-time/{teamId}",
            "/api/teams/me",
            "/api/teams/me/regular",
            "/api/teams/me/one-time",
            "/api/teams/{teamId}",
            "/api/teams/subjects",
            "/api/admin/teams",
            "/api/admin/teams/{teamId}",
            "/api/admin/teams/{teamId}/report/{reportId}",
            "/api/admin/teams/one-time/{teamId}/report",
            "/api/admin/teams/{teamId}/{week}/report",
            "/api/admin/teams/{teamId}/members",
            "/api/admin/teams/{teamId}/attendance",
            "/api/admin/teams/reports/status",
            "/api/admin/team-match/applications",
            "/api/admin/team-match",
            "/api/admin/content/categories",
            "/api/admin/content/topics",
            "/api/admin/setting",
            "/api/admin/setting/semester",
            "/api/admin/setting/study-recruitment",
            "/api/study/team/{teamId}/overview",
            "/api/study/{studyId}",
            "/api/study/team/{teamId}/topic",
            "/api/study/report/{reportId}",
            "/api/review/me/report/{reportId}",
            "/api/review/me",
            "/api/review/me/{reviewId}"
    })
    public EccSmokeEnvelope<EccEndpointResponse> getEndpoint(HttpServletRequest request) {
        return accepted("GET", request);
    }

    /**
     * ECC-back의 POST endpoint shape를 DB 접근 없이 수락한다.
     */
    @PostMapping({
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/users/me/withdraw",
            "/api/teams/regular/apply",
            "/api/teams/one-time",
            "/api/admin/teams",
            "/api/admin/teams/{teamId}/members",
            "/api/admin/team-match",
            "/api/admin/content/categories",
            "/api/admin/content/topics",
            "/api/admin/setting/semester",
            "/chat",
            "/api/study/team/{teamId}",
            "/api/study/{studyId}/topic",
            "/api/study/{studyId}/ai-help",
            "/api/study/{studyId}/general/corrections",
            "/api/study/{studyId}/general/vocabs",
            "/api/study/{studyId}/general/ai-help",
            "/api/review/me/{reviewId}/test"
    })
    public EccSmokeEnvelope<EccEndpointResponse> postEndpoint(HttpServletRequest request) {
        return accepted("POST", request);
    }

    /**
     * ECC-back의 PATCH endpoint shape를 DB 접근 없이 수락한다.
     */
    @PatchMapping({
            "/api/users/me/update",
            "/api/users/me/password",
            "/api/users/me/level",
            "/api/admin/users/{uuid}/approve",
            "/api/admin/users/{uuid}/status",
            "/api/admin/users/{uuid}/level",
            "/api/admin/users/level/{requestId}/approve",
            "/api/admin/users/level/{requestId}/reject",
            "/api/teams/one-time/{teamId}/apply",
            "/api/admin/teams/{teamId}/{week}/report/grade",
            "/api/admin/teams/{teamId}/score",
            "/api/admin/setting/study-recruitment",
            "/api/study/report/{reportId}",
            "/api/review/me/{reviewId}/test"
    })
    public EccSmokeEnvelope<EccEndpointResponse> patchEndpoint(HttpServletRequest request) {
        return accepted("PATCH", request);
    }

    /**
     * ECC-back의 PUT endpoint shape를 DB 접근 없이 수락한다.
     */
    @PutMapping({
            "/api/teams/regular/apply",
            "/api/teams/one-time/{teamId}",
            "/api/admin/content/categories/{categoryId}",
            "/api/admin/content/topics/{topicId}",
            "/api/study/{studyId}/general",
            "/api/study/{studyId}"
    })
    public EccSmokeEnvelope<EccEndpointResponse> putEndpoint(HttpServletRequest request) {
        return accepted("PUT", request);
    }

    /**
     * ECC-back의 DELETE endpoint shape를 DB 접근 없이 수락한다.
     */
    @DeleteMapping({
            "/api/users/me/cancel",
            "/api/admin/users/{uuid}/reject",
            "/api/teams/regular/apply",
            "/api/teams/one-time/{teamId}/apply",
            "/api/teams/one-time/{teamId}",
            "/api/admin/teams/one-time/{teamId}",
            "/api/admin/teams/{teamId}/members/{memberUuid}",
            "/api/admin/content/categories/{categoryId}",
            "/api/admin/content/topics/{topicId}"
    })
    public EccSmokeEnvelope<EccEndpointResponse> deleteEndpoint(HttpServletRequest request) {
        return accepted("DELETE", request);
    }

    private static EccSmokeEnvelope<EccEndpointResponse> accepted(String method, HttpServletRequest request) {
        return EccSmokeEnvelope.success("ECC endpoint smoke 응답입니다.", responseFor(method, "ecc-endpoint", request));
    }

    private static EccEndpointResponse responseFor(String method, String group, HttpServletRequest request) {
        String route = matchedRoute(request);
        return new EccEndpointResponse(
                method,
                request.getRequestURI(),
                route,
                group,
                SOURCE_PROJECT,
                false,
                false,
                Instant.now().toString());
    }

    private static String matchedRoute(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return route == null ? request.getRequestURI() : route.toString();
    }

    private static void sleepBounded(long delayMillis) {
        long boundedDelayMillis = Math.max(0L, Math.min(delayMillis, MAX_DELAY_MILLIS));
        if (boundedDelayMillis == 0L) {
            return;
        }
        try {
            Thread.sleep(boundedDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * ECC 원본 route 후보를 polling 대상으로 노출하는 read-only item이다.
     */
    public record RouteSpec(String method, String path, String group) {
    }

    /**
     * route catalog endpoint의 bounded 응답 shape다.
     */
    public record RouteCatalogResponse(String sourceProject, int routeCount, List<RouteSpec> routes) {
    }

    /**
     * 개별 endpoint 호출이 처리된 route와 테스트 제약을 알려주는 fixture 응답이다.
     */
    public record EccEndpointResponse(
            String method,
            String path,
            String route,
            String group,
            String sourceProject,
            boolean dbAccess,
            boolean businessLogic,
            String observedAt) {
    }

    /**
     * ECC-back의 ResponseDto와 비슷하게 success/message/data를 갖는 smoke 전용 envelope다.
     */
    public record EccSmokeEnvelope<T>(boolean success, String message, T data) {

        /**
         * 정상 smoke 응답 envelope를 만든다.
         */
        public static <T> EccSmokeEnvelope<T> success(String message, T data) {
            return new EccSmokeEnvelope<>(true, message, data);
        }

        /**
         * 의도적인 오류 smoke 응답 envelope를 만든다.
         */
        public static <T> EccSmokeEnvelope<T> error(String message, T data) {
            return new EccSmokeEnvelope<>(false, message, data);
        }
    }
}
