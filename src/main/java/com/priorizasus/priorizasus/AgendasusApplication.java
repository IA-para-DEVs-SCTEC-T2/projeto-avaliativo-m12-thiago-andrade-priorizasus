package com.priorizasus.priorizasus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgendasusApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgendasusApplication.class, args);
  }
}
