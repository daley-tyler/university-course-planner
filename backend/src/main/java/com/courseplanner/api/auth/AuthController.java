package com.courseplanner.api.auth;

import com.courseplanner.api.model.entities.plans.AppUser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${cas.service.url}")
    private String casServiceUrl;

    @Value("${cas.login.url}")
    private String casLoginUrl;

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        CasUserPrincipal principal = (CasUserPrincipal) auth.getPrincipal();
        AppUser user = principal.getUser();

        return ResponseEntity.ok(Map.of(
            "id",          user.getId(),
            "casId",       user.getCasId(),
            "email",       user.getEmail(),
            "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getCasId()
        ));
    }

    @GetMapping("/login")
    public void login(HttpServletResponse res) throws IOException {
        res.sendRedirect(
            casLoginUrl + "?service=" +
            URLEncoder.encode(casServiceUrl + "/login/cas", StandardCharsets.UTF_8)
        );
    }
}
