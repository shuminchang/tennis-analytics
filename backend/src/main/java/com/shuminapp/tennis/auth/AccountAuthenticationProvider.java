package com.shuminapp.tennis.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountAuthenticationProvider implements AuthenticationProvider {
    private final UserRepository users;private final PasswordEncoder encoder;private final String dummy;
    public AccountAuthenticationProvider(UserRepository users,PasswordEncoder encoder) { this.users=users;this.encoder=encoder;dummy=encoder.encode("non-account-timing-placeholder"); }
    @Override @Transactional(noRollbackFor=AuthenticationException.class)
    public Authentication authenticate(Authentication request) throws AuthenticationException {
        String username=request.getName().toLowerCase(java.util.Locale.ROOT);String password=String.valueOf(request.getCredentials());
        if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || username.length()>64) throw new BadCredentialsException("Invalid credentials");
        var user=users.find(username,true);
        if(user.isEmpty()) { encoder.matches(password,dummy);throw new BadCredentialsException("Invalid credentials"); }
        var u=user.get();
        if(!u.enabled() || u.lockedUntil()!=null && u.lockedUntil().isAfter(Instant.now())) throw new BadCredentialsException("Invalid credentials");
        if(!encoder.matches(password,u.passwordHash())) { users.failed(u);users.audit(username,"LOGIN_FAILED",username);throw new BadCredentialsException("Invalid credentials"); }
        users.success(u.id());users.audit(username,"LOGIN_SUCCESS",username);
        return UsernamePasswordAuthenticationToken.authenticated(u.username(),null,List.of(new SimpleGrantedAuthority("ROLE_"+u.role())));
    }
    @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
}
