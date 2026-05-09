package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.model.User;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository repository;

    public UserResponse register(RegisterRequest req) {
        if(repository.existsByEmail(req.getEmail())) {
            User existingUser = repository.findByEmail(req.getEmail());
            UserResponse response = new UserResponse();
            response.setId(existingUser.getId());
            response.setPassword(existingUser.getPassword());
            response.setEmail(existingUser.getEmail());
            response.setFirstName(existingUser.getFirstName());
            response.setLastName(existingUser.getLastName());
            response.setCreatedAt(existingUser.getCreatedAt());
            response.setUpdatedAt(existingUser.getUpdatedAt());
            return response;
        }

        User user = new User();
        user.setEmail(req.getEmail());
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setPassword(req.getPassword());
        user.setKeyCloakId(req.getKeyCloakId());

        User saveduser = repository.save(user);
        UserResponse response = new UserResponse();
        response.setId(saveduser.getId());
        response.setPassword(saveduser.getPassword());
        response.setEmail(saveduser.getEmail());
        response.setFirstName(saveduser.getFirstName());
        response.setLastName(saveduser.getLastName());
        response.setCreatedAt(saveduser.getCreatedAt());
        response.setUpdatedAt(saveduser.getUpdatedAt());
        response.setKeyCloakId(saveduser.getKeyCloakId());

        return response;
    }

    public UserResponse getUserProfileById(String userId) {
        User user = repository.findById(userId).orElseThrow(()-> new RuntimeException("User not found"));
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setPassword(user.getPassword());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    public Boolean existByKeyCloakId(String keyCloakId) {
        log.info("checking user with keyCloakId : {} exist in db or not",keyCloakId);
        return repository.existsByKeyCloakId(keyCloakId);
    }
}
