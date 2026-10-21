package com.example.minhaj.repositories;

import com.example.minhaj.models.PasswordResetToken;
import com.example.minhaj.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    // Takes the SHA-256 hash of the submitted token, which is what the token column holds.
    Optional<PasswordResetToken> findByToken(String tokenHash);
    @Transactional
    void deleteByUser(User user);

    // expiry_date has no time zone: it is compared with LocalDateTime.now() of the app, like isExpired().
    @Transactional
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiryDate < :now")
    int deleteExpiredBefore(@Param("now") LocalDateTime now);
}