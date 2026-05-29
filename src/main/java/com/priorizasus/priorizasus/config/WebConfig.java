package com.priorizasus.priorizasus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration — registers view controllers for static pages.
 *
 * <p>Per ADR-0003, all displayed times use the clinic timezone ({@code America/Sao_Paulo}). The
 * shared model attributes ({@code brtTime}, {@code today}) are provided by the top-level {@link
 * GlobalModelAttributes} class so Spring component scanning reliably detects
 * {@code @ControllerAdvice}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // Home page is handled by HomeController
  }
}
