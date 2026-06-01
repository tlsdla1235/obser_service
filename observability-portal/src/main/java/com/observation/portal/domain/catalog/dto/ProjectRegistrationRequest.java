package com.observation.portal.domain.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * public project registration request body다.
 *
 * <p>Stage 1에서는 project name만 non-secret 입력으로 허용하며, client가 보낸 account/role/key/hash 계열 field는
 * controller-service boundary로 전달하지 않는다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectRegistrationRequest(String name) {
}
