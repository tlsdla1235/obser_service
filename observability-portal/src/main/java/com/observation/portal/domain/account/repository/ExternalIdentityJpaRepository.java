package com.observation.portal.domain.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GitHub provider subject와 내부 account 연결을 조회/저장하는 Spring Data JPA repository다.
 */
@Repository
public interface ExternalIdentityJpaRepository extends JpaRepository<ExternalIdentityEntity, UUID> {

    /**
     * provider와 provider subject 조합으로 기존 external identity를 찾는다.
     */
    Optional<ExternalIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);

    /**
     * local smoke seed의 보조 selector로 display name이 정확히 하나인지 확인하기 위해 조회한다.
     */
    List<ExternalIdentityEntity> findByProviderAndDisplayName(String provider, String displayName);

    /**
     * 동시 첫 로그인 race에서 unique violation 대신 이미 생성된 identity로 수렴하도록 insert-if-absent를 수행한다.
     */
    @Query(value = """
            insert into external_identities (
              id, account_id, provider, provider_subject, email, display_name, avatar_url, created_at, updated_at
            )
            values (
              :id, :accountId, :provider, :providerSubject, :email, :displayName, :avatarUrl, :now, :now
            )
            on conflict (provider, provider_subject) do nothing
            returning account_id
            """, nativeQuery = true)
    Optional<UUID> insertIfAbsentReturningAccountId(
            @Param("id") UUID id,
            @Param("accountId") UUID accountId,
            @Param("provider") String provider,
            @Param("providerSubject") String providerSubject,
            @Param("email") String email,
            @Param("displayName") String displayName,
            @Param("avatarUrl") String avatarUrl,
            @Param("now") OffsetDateTime now);
}
