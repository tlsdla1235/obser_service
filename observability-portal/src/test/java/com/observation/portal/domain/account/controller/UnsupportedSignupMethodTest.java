package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.service.AccountAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnsupportedSignupMethodTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AccountAuthController(mock(AccountAuthService.class)))
            .build();

    @Test
    void unsupportedSignupLoginMethodsDoNotHavePublicRoutes() throws Exception {
        mockMvc.perform(post("/api/auth/password")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auth/magic-link")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auth/google/authorize")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auth/anonymous")).andExpect(status().isNotFound());

        assertThat(unsupportedAuthRoutes())
                .as("Story 6.1 only exposes GitHub OAuth account entry")
                .isEmpty();
    }

    private static List<String> unsupportedAuthRoutes() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> routes = new ArrayList<>();
        for (var beanDefinition : scanner.findCandidateComponents("com.observation.portal.domain")) {
            Class<?> controllerType = Class.forName(beanDefinition.getBeanClassName());
            List<String> basePaths = requestMappingPaths(controllerType.getAnnotation(RequestMapping.class));
            for (var method : controllerType.getDeclaredMethods()) {
                for (String path : httpMappingPaths(method.getAnnotation(GetMapping.class))) {
                    collectUnsupportedRoute(routes, controllerType, method.getName(), basePaths, path);
                }
                for (String path : postMappingPaths(method.getAnnotation(PostMapping.class))) {
                    collectUnsupportedRoute(routes, controllerType, method.getName(), basePaths, path);
                }
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                if (requestMapping != null
                        && List.of(requestMapping.method()).stream()
                        .anyMatch(methodType -> methodType == RequestMethod.GET || methodType == RequestMethod.POST)) {
                    for (String path : requestMappingPaths(requestMapping)) {
                        collectUnsupportedRoute(routes, controllerType, method.getName(), basePaths, path);
                    }
                }
            }
        }
        return routes;
    }

    private static void collectUnsupportedRoute(
            List<String> routes,
            Class<?> controllerType,
            String methodName,
            List<String> basePaths,
            String methodPath) {
        for (String basePath : basePaths) {
            String route = normalizeRoute(basePath, methodPath);
            String normalized = route.toLowerCase();
            boolean unsupportedAuthRoute = normalized.startsWith("/api/auth/")
                    && !normalized.startsWith("/api/auth/github/")
                    && !normalized.equals("/api/auth/token/refresh")
                    && !normalized.equals("/api/auth/logout");
            if (unsupportedAuthRoute) {
                routes.add(controllerType.getName() + "#" + methodName + " " + route);
            }
        }
    }

    private static List<String> requestMappingPaths(RequestMapping requestMapping) {
        if (requestMapping == null) {
            return List.of("");
        }
        if (requestMapping.path().length > 0) {
            return List.of(requestMapping.path());
        }
        if (requestMapping.value().length > 0) {
            return List.of(requestMapping.value());
        }
        return List.of("");
    }

    private static List<String> httpMappingPaths(GetMapping getMapping) {
        if (getMapping == null) {
            return List.of();
        }
        if (getMapping.path().length > 0) {
            return List.of(getMapping.path());
        }
        if (getMapping.value().length > 0) {
            return List.of(getMapping.value());
        }
        return List.of("");
    }

    private static List<String> postMappingPaths(PostMapping postMapping) {
        if (postMapping == null) {
            return List.of();
        }
        if (postMapping.path().length > 0) {
            return List.of(postMapping.path());
        }
        if (postMapping.value().length > 0) {
            return List.of(postMapping.value());
        }
        return List.of("");
    }

    private static String normalizeRoute(String basePath, String methodPath) {
        String joined = ((basePath == null ? "" : basePath.trim()) + "/" + (methodPath == null ? "" : methodPath.trim()))
                .replaceAll("/{2,}", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            return joined.substring(0, joined.length() - 1);
        }
        return joined.isBlank() ? "/" : joined;
    }
}
