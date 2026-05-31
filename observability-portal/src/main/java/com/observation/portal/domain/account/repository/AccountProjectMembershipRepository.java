package com.observation.portal.domain.account.repository;

import com.observation.portal.domain.catalog.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * account-project authorization membership 조회를 담당하는 Spring Data JPA repository다.
 */
@Repository
public interface AccountProjectMembershipRepository extends JpaRepository<AccountProjectMembershipEntity, UUID> {

    /**
     * account가 active membership으로 볼 수 있는 active project만 이름순으로 조회한다.
     */
    @Query("""
            select project
            from AccountProjectMembershipEntity membership, ProjectEntity project
            where project.id = membership.projectId
              and membership.accountId = :accountId
              and membership.status = 'active'
              and project.status = 'active'
            order by project.name asc
            """)
    List<ProjectEntity> findActiveMembershipProjectsByAccountId(@Param("accountId") UUID accountId);

    /**
     * Bearer account가 path project에 대한 active membership을 가진 경우에만 true를 반환한다.
     */
    @Query("""
            select case when count(membership) > 0 then true else false end
            from AccountProjectMembershipEntity membership, ProjectEntity project
            where project.id = membership.projectId
              and membership.accountId = :accountId
              and membership.projectId = :projectId
              and membership.status = 'active'
              and project.status = 'active'
            """)
    boolean existsActiveMembership(
            @Param("accountId") UUID accountId,
            @Param("projectId") UUID projectId);
}
