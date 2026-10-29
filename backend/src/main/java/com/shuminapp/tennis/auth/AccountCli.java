package com.shuminapp.tennis.auth;

import com.shuminapp.tennis.TennisApplication;
import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class AccountCli {
    public static void main(String[] args) {
        if(args.length!=2 || System.console()==null) throw new IllegalArgumentException("Usage: account-admin <username> <MEMBER|ADVANCED|ADMIN>; interactive console required");
        char[] password=System.console().readPassword("Password (12+ characters, <=72 UTF-8 bytes): ");
        try(var context=new SpringApplicationBuilder(TennisApplication.class).web(WebApplicationType.NONE).run()) {
            context.getBean(AccountService.class).create("LOCAL_CONSOLE",args[0],new String(password),args[1]);
            System.out.println("Account created.");
        } finally { Arrays.fill(password,'\0'); }
    }
}
