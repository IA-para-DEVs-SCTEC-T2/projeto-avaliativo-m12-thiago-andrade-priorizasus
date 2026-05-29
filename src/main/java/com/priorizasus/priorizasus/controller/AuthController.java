package com.priorizasus.priorizasus.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Controller for authentication pages (login form). */
@Controller
@Tag(name = "Auth", description = "Autenticação — login e logout")
public class AuthController {

  @Operation(
      summary = "Página de login",
      description = "Exibe o formulário de login para acesso ao painel administrativo.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Página de login renderizada")})
  @GetMapping("/auth/login")
  public String loginPage(Model model) {
    model.addAttribute("pageTitle", "Login — PRIORIZASUS");
    return "auth/login";
  }
}
