package com.shuminapp.tennis.features;

import static org.assertj.core.api.Assertions.assertThat;
import com.shuminapp.tennis.ops.RequestRateFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RateLimitTest {
    @Test void boundsRequestsAndKeepsLoginWindowSeparate() throws Exception {
        var filter=new RequestRateFilter(2,1);
        for(int i=0;i<3;i++) { var req=new MockHttpServletRequest("GET","/v1/home");req.setRemoteAddr("127.0.0.1");var res=new MockHttpServletResponse();filter.doFilter(req,res,(a,b)->{});assertThat(res.getStatus()).isEqualTo(i<2?200:429); }
        var req=new MockHttpServletRequest("POST","/auth/login");req.setRemoteAddr("127.0.0.1");var res=new MockHttpServletResponse();filter.doFilter(req,res,(a,b)->{});assertThat(res.getStatus()).isEqualTo(200);
    }
}
