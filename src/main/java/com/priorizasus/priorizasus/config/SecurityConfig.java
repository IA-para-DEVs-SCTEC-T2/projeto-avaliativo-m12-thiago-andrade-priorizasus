package com.priorizasus.priorizasus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration — basic in-memory auth for Phase 1.
 *
 * <p>Single ADMIN role protects the Staff Dashboard. Patient-facing pages are public. Uses form
 * login with a custom Bootstrap-styled page.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${app.admin.username:admin}")
  private String adminUsername;

  @Value("${app.admin.password:PRIORIZASUS2026}")
  private String adminPassword;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/staff/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        "/",
                        "/booking/**",
                        "/api/booking/**",
                        "/patients/**",
                        "/css/**",
                        "/js/**",
                        "/webjars/**",
                        "/h2-console/**",
                        "/error/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.loginPage("/auth/login")
                    .loginProcessingUrl("/auth/login")
                    .defaultSuccessUrl("/staff/dashboard", true)
                    .permitAll())
        .httpBasic(Customizer.withDefaults())
        .logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessUrl("/").permitAll());

    // H2 Console uses frames — disable CSRF for dev; also exempt Swagger UI
    http.csrf(
        csrf ->
            csrf.ignoringRequestMatchers("/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**"));
    http.headers(headers -> headers.frameOptions(f -> f.sameOrigin()));

    return http.build();
  }

  @Bean
  public InMemoryUserDetailsManager userDetailsManager() {
    PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    UserDetails admin =
        User.builder()
            .username(adminUsername)
            .password(encoder.encode(adminPassword))
            .roles("ADMIN")
            .build();
    UserDetails thiago =
        User.builder()
            .username("thiago.andrade")
            .password(encoder.encode("admin"))
            .roles("ADMIN")
            .build();
    return new InMemoryUserDetailsManager(admin, thiago);
  }
}
