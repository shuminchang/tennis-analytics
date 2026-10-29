package com.shuminapp.tennis.features;

import com.shuminapp.tennis.TennisApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class EloCli {
    public static void main(String[] args) {
        if(!"candidate".equals(System.getenv("IMPORT_TARGET"))) throw new IllegalArgumentException("Set IMPORT_TARGET=candidate and DB_* explicitly");
        try(var context=new SpringApplicationBuilder(TennisApplication.class).web(WebApplicationType.NONE).properties("spring.datasource.hikari.data-source-properties.reWriteBatchedInserts=true").run()) {
            var r=context.getBean(EloBuildService.class).rebuild();System.out.printf("eloRun=%d matches=%d reused=%s%n",r.runId(),r.matches(),r.reused());
        }
    }
}
