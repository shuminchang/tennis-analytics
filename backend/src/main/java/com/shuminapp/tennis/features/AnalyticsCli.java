package com.shuminapp.tennis.features;

import com.shuminapp.tennis.TennisApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class AnalyticsCli {
    public static void main(String[] args) {
        if(!"candidate".equals(System.getenv("IMPORT_TARGET"))) throw new IllegalArgumentException("Set IMPORT_TARGET=candidate and DB_* explicitly");
        try(var context=new SpringApplicationBuilder(TennisApplication.class).web(WebApplicationType.NONE).run()) {
            context.getBean(AnalyticsRefreshService.class).refresh();System.out.println("Analytics refreshed.");
        }
    }
}
