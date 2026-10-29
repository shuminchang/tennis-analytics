package com.shuminapp.tennis.ops;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(Ordered.HIGHEST_PRECEDENCE+20)
public class RequestRateFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();private final int requests,logins;
    public RequestRateFilter(@Value("${app.rate.requests:240}") int requests,@Value("${app.rate.logins:20}") int logins) { this.requests=requests;this.logins=logins; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        if(req.getRequestURI().endsWith("/v1/health")) { chain.doFilter(req,res);return; }
        long now=System.currentTimeMillis();boolean login=req.getRequestURI().endsWith("/auth/login");String key=req.getRemoteAddr()+(login?":login":":api");
        if(windows.size()>=10000) { windows.entrySet().removeIf(e->now-e.getValue().start>=60000);if(windows.size()>=10000 && !windows.containsKey(key)) { deny(res);return; } }
        Window window=windows.compute(key,(k,old)->old==null || now-old.start>=60000?new Window(now):old);
        synchronized(window) { if(++window.count>(login?logins:requests)) { deny(res);return; } }
        chain.doFilter(req,res);
    }
    private void deny(HttpServletResponse res) throws IOException { res.setStatus(429);res.setHeader("Retry-After","60");res.setContentType("application/json");res.getWriter().write("{\"code\":\"RATE_LIMITED\"}"); }
    private static final class Window { final long start;int count;Window(long start) { this.start=start; } }
}
