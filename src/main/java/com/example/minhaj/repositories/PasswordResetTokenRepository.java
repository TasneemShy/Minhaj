package com.example.minhaj.repositories;

import com.example.minhaj.models.PasswordResetToken;
import com.example.minhaj.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    // Takes the SHA-256 hash of the submitted token, which is what the token column holds.
    Optional<PasswordResetToken> findByToken(String tokenHash);
    @Transactional
    void deleteByUser(User user);
}