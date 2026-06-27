package com.observation.portal.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * prod profile에서 필수 외부 주입 설정이 비어 있으면 일반 bean 생성 전에 기동을 중단한다.
 *
 * <p>실패 메시지는 property key와 주입 경로만 알려주고 secret 원문은 절대 포함하지 않는다.</p>
 */
@Component
public class ProdRequiredPropertiesGuard implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private static final String PROD_PROFILE = "prod";
    private static final List<RequiredProperty> REQUIRED_PROPERTIES = List.of(
            new RequiredProperty("spring.datasource.url", "SPRING_DATASOURCE_URL"),
            new RequiredProperty("spring.datasource.username", "SPRING_DATASOURCE_USERNAME"),
            new RequiredProperty("spring.datasource.password", "SPRING_DATASOURCE_PASSWORD"),
            new RequiredProperty("portal.auth.github.client-id", "PORTAL_AUTH_GITHUB_CLIENT_ID"),
            new RequiredProperty("portal.auth.github.client-secret", "PORTAL_AUTH_GITHUB_CLIENT_SECRET"),
            new RequiredProperty("portal.auth.github.redirect-uri", "PORTAL_AUTH_GITHUB_REDIRECT_URI"),
            new RequiredProperty("portal.auth.github.homepage-url", "PORTAL_AUTH_GITHUB_HOMEPAGE_URL"),
            new RequiredProperty("portal.auth.service-token.signing-key", "PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY"),
            new RequiredProperty("portal.auth.oauth-state.signing-key", "PORTAL_AUTH_OAUTH_STATE_SIGNING_KEY"));

    private Environment environment;

    /**
     * Spring Environment를 받아 active profile과 외부 주입 property를 확인할 수 있게 한다.
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    /**
     * prod profile에서만 필수 설정 누락을 fail-closed로 처리한다.
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!isProdProfileActive()) {
            return;
        }
        List<RequiredProperty> missing = REQUIRED_PROPERTIES.stream()
                .filter(property -> isBlank(environment.getProperty(property.propertyKey())))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(messageFor(missing));
        }
    }

    /**
     * 다른 BeanFactoryPostProcessor보다 먼저 실행해 datasource/Flyway 초기화 전 설정 오류를 드러낸다.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .anyMatch(PROD_PROFILE::equalsIgnoreCase);
    }

    private static String messageFor(List<RequiredProperty> missing) {
        String keys = missing.stream()
                .map(property -> property.propertyKey() + " via " + property.envVarName())
                .collect(java.util.stream.Collectors.joining(", "));
        return "prod profile requires non-empty external configuration: " + keys
                + ". Inject values through AWS SSM/systemd environment or GitHub Actions deployment handoff.";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 운영 필수 property와 대응되는 환경변수 이름이다.
     */
    private record RequiredProperty(String propertyKey, String envVarName) {
    }
}
