package com.shuminapp.tennis.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean SecurityFilterChain security(HttpSecurity http,AccountAuthenticationProvider provider,UserRepository users) throws Exception {
        http.authenticationProvider(provider).authorizeHttpRequests(a->a
                .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/v1/h2h","/v1/players/*/*/elo","/v1/visualizations/correlations").hasAnyRole("ADVANCED","ADMIN")
                .requestMatchers("/v1/compare","/v1/favorites/**").hasAnyRole("MEMBER","ADVANCED","ADMIN")
                .requestMatchers("/auth/**","/v1/**","/error").permitAll().anyRequest().denyAll())
            .formLogin(f->f.loginProcessingUrl("/auth/login").successHandler((req,res,auth)->json(res,200,"OK"))
                .failureHandler((req,res,e)->json(res,401,"INVALID_CREDENTIALS")))
            .logout(l->l.logoutUrl("/auth/logout").logoutSuccessHandler((req,res,auth)->json(res,200,"OK")))
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->json(res,401,"AUTH_REQUIRED"))
                .accessDeniedHandler((req,res,x)->{ users.audit(req.getUserPrincipal()==null?null:req.getUserPrincipal().getName(),"ACCESS_DENIED",req.getServletPath());json(res,403,"FORBIDDEN"); }))
            .requestCache(c->c.disable())
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                    var auth=SecurityContextHolder.getContext().getAuthentication();
                    if(auth!=null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                        var user=users.find(auth.getName(),false);
                        if(user.isEmpty() || !user.get().enabled()) { SecurityContextHolder.clearContext();var session=req.getSession(false);if(session!=null) session.invalidate(); }
                        else SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(auth.getName(),null,List.of(new SimpleGrantedAuthority("ROLE_"+user.get().role()))));
                    }
                    chain.doFilter(req,res);
                }
            },UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private static void json(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"code\":\""+code+"\"}");
    }
}
