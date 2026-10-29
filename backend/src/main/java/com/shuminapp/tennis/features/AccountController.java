package com.shuminapp.tennis.features;

import com.shuminapp.tennis.auth.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private final AccountService service;private final UserRepository users;
    public AccountController(AccountService service,UserRepository users) { this.service=service;this.users=users; }
    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/auth/me") public Map<String,Object> me(Authentication auth) {
        return auth==null?Map.of("authenticated",false):Map.of("authenticated",true,"username",auth.getName(),"roles",auth.getAuthorities().stream().map(a->a.getAuthority().replace("ROLE_","")).toList());
    }
    @GetMapping("/v1/admin/users") public List<Map<String,Object>> users() { return users.list(); }
    @PostMapping("/v1/admin/users") public Map<String,Long> create(Authentication actor,@RequestBody Create request) { return Map.of("id",service.create(actor.getName(),request.username(),request.password(),request.role())); }
    @PatchMapping("/v1/admin/users/{id}") public void update(Authentication actor,@PathVariable long id,@RequestBody Update request) { service.update(actor.getName(),id,request.role(),request.enabled()); }
    public record Create(String username,String password,String role) { }
    public record Update(String role,boolean enabled) { }
}
