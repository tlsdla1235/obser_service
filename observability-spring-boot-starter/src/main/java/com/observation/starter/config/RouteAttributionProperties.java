package com.observation.starter.config;

import com.observation.starter.model.route.RouteTemplateContract;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
        return RouteTemplateContract.isValidTemplate(template);
    }

    /**
     * route template을 정리하고 query string, absolute URL, 실제 ID 값처럼 보이는 segment를 거부한다.
     */
    public static String normalizeAllowlistTemplate(String template) {
        return RouteTemplateContract.normalizeTemplate(template);
    }
}
