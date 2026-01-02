package com.expiryguard.repository;

import com.expiryguard.entity.Secret;
import com.expiryguard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SecretRepository extends JpaRepository<Secret, Long> {
    List<Secret> findByUserAndActiveOrderByExpiryDateAsc(User user, boolean active);

    @Query("SELECT s FROM Secret s JOIN FETCH s.user WHERE s.active = true AND s.expiryDate >= :today AND s.expiryDate <= :maxDate")
    List<Secret> findSecretsExpiringBefore(@Param("today") LocalDate today, @Param("maxDate") LocalDate maxDate);
}