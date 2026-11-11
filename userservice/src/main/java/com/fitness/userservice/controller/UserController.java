package com.fitness.userservice.controller;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /** Profile of the caller. {@code X-User-ID} is set by the gateway from the verified JWT. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(userService.getByKeyCloakId(userId));
    }

    /** Internal: called by the gateway on a user's first request. Blocked at the gateway for clients. */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(userService.register(req));
    }

    /** Internal: used by the gateway and activity-service. Blocked at the gateway for clients. */
    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.existsByKeyCloakId(userId));
    }
}
