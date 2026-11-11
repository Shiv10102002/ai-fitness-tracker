package com.fitness.gateway.user;

import lombok.Data;

@Data
public class RegisterRequest {
    private String keyCloakId;
    private String email;
    private String firstName;
    private String lastName;
}
