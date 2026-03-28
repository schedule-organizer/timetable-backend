package com.schediflow.repository;

import com.schediflow.domain.InvitationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, Long> {

    Optional<InvitationToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);
}
