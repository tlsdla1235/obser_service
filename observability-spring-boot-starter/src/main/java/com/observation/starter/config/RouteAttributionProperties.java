package com.observation.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * route attribution fallback에서 사용할 starter allowlist 설정이다.
 *
 * <p>{@code observation.route-attribution.allowlist}에는 raw path가 아니라
 * {@code /orders/{orderId}} 같은 route template만 둘 수 있다. 이 목록은
 * {@code http.route}가 없을 때 raw path candidate를 일시적으로 매칭하는 데만 쓰인다.</p>
 * <p>application.yml</p>
 * <p>→ RouteAttributionProperties가 설정값 바인딩</p>
 * <p>→ RouteAttributionAutoConfiguration이 이 값을 Bean에 주입</p>
 * <p>→ RouteNormalizationService가 allowlist 기반으로 raw path를 route template으로 정규화</p>
 */
@ConfigurationProperties(prefix = RouteAttributionProperties.PREFIX)
public class RouteAttributionProperties {

    public static final String PREFIX = "observation.route-attribution";
    public static final String ALLOWLIST_PROPERTY = PREFIX + ".allowlist";

    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern LONG_HEX_SEGMENT = Pattern.compile("(?i)[0-9a-f]{8,}");

    private List<String> allowlist = List.of();

    /**
     * 정규화 fallback에 사용할 route template allowlist를 반환한다.
     */
    public List<String> getAllowlist() {
        return allowlist;
    }

    /**
     * Spring properties binding에서 받은 allowlist를 route template shape로 검증한다.
     */
    public void setAllowlist(Collection<String> allowlist) {
        Collection<String> templates = Objects.requireNonNullElseGet(allowlist, List::of);
        this.allowlist = List.copyOf(templates.stream()
                .map(RouteAttributionProperties::normalizeAllowlistTemplate)
                .toList());
    }

    /**
     * 설정값이 MVP allowlist에 들어갈 수 있는 route template인지 확인한다.
     */
    public static boolean isValidAllowlistTemplate(String template) {
        try {
            normalizeAllowlistTemplate(template);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * route template을 정리하고 query string, absolute URL, 실제 ID 값처럼 보이는 segment를 거부한다.
     */
    public static String normalizeAllowlistTemplate(String template) {
        //빈값 거부
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("route attribution allowlist template must not be blank");
        }

        String candidate = template.trim();

        //query string 거부
        if (candidate.contains("?")) {
            throw new IllegalArgumentException("route attribution allowlist template must not contain query string");
        }
        //absolute URL 거부
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            throw new IllegalArgumentException("route attribution allowlist template must not be an absolute URL");
        }

        if (!candidate.startsWith("/") || candidate.contains("//")) {
            throw new IllegalArgumentException("route attribution allowlist template must start with a single slash");
        }
        ///orders/처럼 trailing slash가 붙은 값은 거부
        if (candidate.length() > 1 && candidate.endsWith("/")) {
            throw new IllegalArgumentException("route attribution allowlist template must not end with slash");
        }

        for (String segment : candidate.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            //{orderId}처럼 template variable이면 허용
            if (isTemplateVariable(segment)) {
                continue;
            }
            //일반 literal segment는 안전한 문자만 허용합니다. 예: orders, api, v1, user-profile
            if (!segment.matches("[A-Za-z0-9._~-]+")) {
                throw new IllegalArgumentException("route attribution allowlist literal segment contains unsupported characters");
            }
            //숫자 ID, UUID, 긴 hex 값처럼 “실제 식별자”로 보이는 값은 거부
            if (looksLikeConcreteIdentifier(segment)) {
                throw new IllegalArgumentException("route attribution allowlist must use a template variable instead of an ID value");
            }
        }
        return candidate;
    }

    private static boolean isTemplateVariable(String segment) {
        return segment.startsWith("{")
                && segment.endsWith("}")
                && segment.length() > 2
                && segment.substring(1, segment.length() - 1).matches("[A-Za-z][A-Za-z0-9_]*");
    }

    /**
     *
     * @param segment
     * @return
     * 8자 이상이고, 문자가 전부 0-9 또는 a-f이면
     * 방어적으로 짜기 위해 보수적으로... 라는데 추후에 고려해봐야할듯.
     */
    private static boolean looksLikeConcreteIdentifier(String segment) {
        return segment.matches("[0-9]+")
                || UUID_SEGMENT.matcher(segment).matches()
                || LONG_HEX_SEGMENT.matcher(segment).matches();
    }
}
