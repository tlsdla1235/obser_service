package com.observation.portal.domain.catalog.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Project Entry 화면이 사용할 read-only navigation 응답 모델이다.
 *
 * <p>프로젝트 scope 선택에 필요한 light summary만 담고 dashboard state나 triage 판단은 포함하지 않는다.</p>
 */
public record ProjectNavigationReadModel(
        OffsetDateTime generatedAt,
        List<ProjectItem> projects
) {

    /**
     * 생성 시각과 프로젝트 목록을 null 없이 고정하고, 목록은 외부 변경이 불가능하게 복사한다.
     */
    public ProjectNavigationReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        projects = List.copyOf(Objects.requireNonNull(projects, "projects must not be null"));
    }

    /**
     * Project Entry 목록의 단일 프로젝트 항목이다.
     *
     * <p>setup/connection issue count는 원인 확정이 아니라 application별 light 후보 개수만 표현한다.</p>
     */
    public record ProjectItem(
            UUID projectId,
            String name,
            int applicationCount,
            int setupConnectionIssueCount,
            ConcernSummary recentConcern,
            ProjectLinks links
    ) {

        /**
         * 프로젝트 identity, count, link를 검증하고 음수 count가 response에 실리지 않도록 막는다.
         */
        public ProjectItem {
            Objects.requireNonNull(projectId, "projectId must not be null");
            name = requireText(name, "name");
            if (applicationCount < 0) {
                throw new IllegalArgumentException("applicationCount must not be negative");
            }
            if (setupConnectionIssueCount < 0) {
                throw new IllegalArgumentException("setupConnectionIssueCount must not be negative");
            }
            Objects.requireNonNull(links, "links must not be null");
        }
    }

    /**
     * 후속 triage source가 제공될 때만 채워질 최대 1개의 light concern summary다.
     */
    public record ConcernSummary(String code, String label, String source) {

        /**
         * concern code, label, source가 공백 없이 전달되도록 보장한다.
         */
        public ConcernSummary {
            code = requireText(code, "code");
            label = requireText(label, "label");
            source = requireText(source, "source");
        }
    }

    /**
     * Project Entry에서 다음 navigation 단계로 이동할 API link 모음이다.
     */
    public record ProjectLinks(String applications) {

        /**
         * application list link가 비어 있지 않도록 보장한다.
         */
        public ProjectLinks {
            applications = requireText(applications, "applications");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
