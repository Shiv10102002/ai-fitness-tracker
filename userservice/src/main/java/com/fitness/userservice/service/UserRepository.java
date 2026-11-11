package com.fitness.userservice.service;

import com.fitness.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByKeyCloakId(String keyCloakId);

    Optional<User> findByEmail(String email);

    boolean existsByKeyCloakId(String keyCloakId);
}
