package com.csm.paas.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.csm.paas.core.infrastructure.dao")
public class PaasCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaasCoreApplication.class, args);
    }
}
