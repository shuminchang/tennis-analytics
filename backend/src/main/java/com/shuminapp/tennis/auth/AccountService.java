package com.shuminapp.tennis.auth;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;private final PasswordEncoder encoder;
    public AccountService(UserRepository users,PasswordEncoder encoder) { this.users=users;this.encoder=encoder; }
    @Transactional
    public long create(String actor,String username,String password,String role) {
        validateAccountFields(username,password,role);
        username=username.toLowerCase(Locale.ROOT);
        long id=users.create(username,encoder.encode(password),role);users.audit(actor,"ACCOUNT_CREATED",username);return id;
    }
    @Transactional
    public void update(String actor,long id,String role,boolean enabled) {
        validateRole(role);var self=users.find(actor,false).orElseThrow();if(self.id()==id && (!role.equals("ADMIN") || !enabled)) throw new IllegalArgumentException("Cannot revoke own administrator account");
        users.update(id,role,enabled);users.audit(actor,"ACCOUNT_UPDATED",Long.toString(id));
    }
    public static void validateAccountFields(String username,String password,String role) {
        if(username==null || !username.toLowerCase(Locale.ROOT).matches("[a-z0-9_.-]{3,64}")) throw new IllegalArgumentException("Username must contain 3-64 letters, digits, underscores, dots or hyphens");
        validateRole(role);
        if(password==null || password.length()<12) throw new IllegalArgumentException("Password must contain at least 12 characters");
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
    }
    private static void validateRole(String role) { if(role==null || !Set.of("MEMBER","ADVANCED","ADMIN").contains(role)) throw new IllegalArgumentException(); }
}
