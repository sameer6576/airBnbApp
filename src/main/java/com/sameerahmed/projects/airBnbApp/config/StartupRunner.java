package com.sameerahmed.projects.airBnbApp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class StartupRunner implements CommandLineRunner {

    @Autowired
    private Environment environment;

    @Override
    public void run(String... args) {
        System.out.println("==================================");
        System.out.println("Active Profiles: " +
                Arrays.toString(environment.getActiveProfiles()));
        System.out.println("==================================");
    }
}