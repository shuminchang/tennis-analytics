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
        if(username==null || password==null) throw new IllegalArgumentException();
        username=username.toLowerCase(Locale.ROOT);validateRole(role);
        if(!username.matches("[a-z0-9_.-]{3,64}") || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Invalid account fields");
        long id=users.create(username,encoder.encode(password),role);users.audit(actor,"ACCOUNT_CREATED",username);return id;
    }
    @Transactional
    public void update(String actor,long id,String role,boolean enabled) {
        validateRole(role);var self=users.find(actor,false).orElseThrow();if(self.id()==id && (!role.equals("ADMIN") || !enabled)) throw new IllegalArgumentException("Cannot revoke own administrator account");
        users.update(id,role,enabled);users.audit(actor,"ACCOUNT_UPDATED",Long.toString(id));
    }
    private void validateRole(String role) { if(role==null || !Set.of("MEMBER","ADVANCED","ADMIN").contains(role)) throw new IllegalArgumentException(); }
}
