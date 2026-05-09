package com.fitness.userservice.service;

import com.fitness.userservice.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User,String> {
    Boolean existsByEmail(String email);

    User findByEmail(@NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email);

    Boolean existsByKeyCloakId(String keyCloakId);
}
