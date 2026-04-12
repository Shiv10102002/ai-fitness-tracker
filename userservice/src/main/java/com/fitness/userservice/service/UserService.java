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
           throw new RuntimeException("email already exists");
        }

        User user = new User();
        user.setEmail(req.getEmail());
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setPassword(req.getPassword());

        User saveduser = repository.save(user);
        UserResponse response = new UserResponse();
        response.setId(saveduser.getId());
        response.setPassword(saveduser.getPassword());
        response.setEmail(saveduser.getEmail());
        response.setFirstName(saveduser.getFirstName());
        response.setLastName(saveduser.getLastName());
        response.setCreatedAt(saveduser.getCreatedAt());
        response.setUpdatedAt(saveduser.getUpdatedAt());

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

    public Boolean existByUserId(String userId) {
        log.info("checking user with userId : {} exist in db or not",userId);
        return repository.existsById(userId);
    }
}
