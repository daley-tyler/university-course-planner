package com.courseplanner.api.auth;

import com.courseplanner.api.model.entities.plans.AppUser;
import com.courseplanner.api.model.repositories.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CasTicketValidationFilter extends OncePerRequestFilter {

    private static final Pattern USER_PATTERN = Pattern.compile("<cas:user>(.*?)</cas:user>");

    private final AppUserRepository userRepo;
    private final AuthAuditService auditService;
    private final String casValidateUrl;
    private final String emailDomain;
    private final String serviceUrl;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public CasTicketValidationFilter(
            AppUserRepository userRepo,
            AuthAuditService auditService,
            String serviceUrl,
            String casValidateUrl,
            String emailDomain
    ) {
        this.userRepo = userRepo;
        this.auditService = auditService;
        this.casValidateUrl = casValidateUrl;
        this.emailDomain = emailDomain;
        this.serviceUrl = serviceUrl + "/login/cas";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only intercept GET /login/cas?ticket=...
        return !request.getServletPath().equals("/login/cas")
                || request.getParameter("ticket") == null;
    }

    @Override
    @Transactional
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ticket = request.getParameter("ticket");

        String casId = validateTicket(ticket);
        if (casId == null) {
            auditService.log("LOGIN_FAILED", null, request);
            response.sendRedirect("/?error=auth_failed");
            return;
        }

        if (!casId.matches("^[a-z0-9]{2,30}$")) {
            auditService.log("LOGIN_FAILED", null, request);
            response.sendRedirect("/?error=auth_failed");
            return;
        }

        AppUser user = userRepo.findByCasId(casId)
                .orElseGet(() -> userRepo.save(AppUser.fromCas(casId, emailDomain)));
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        CasUserPrincipal principal = new CasUserPrincipal(user);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        auditService.log("LOGIN", auth, request);
        response.sendRedirect("/");
    }

    private String validateTicket(String ticket) {
        try {
            String url = casValidateUrl + "?service=" +
                    URLEncoder.encode(serviceUrl, StandardCharsets.UTF_8) +
                    "&ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (body.contains("<cas:authenticationSuccess>")) {
                Matcher matcher = USER_PATTERN.matcher(body);
                if (matcher.find()) {
                    return matcher.group(1).trim();
                }
            }
        } catch (Exception e) {
            logger.error("CAS ticket validation failed", e);
        }
        return null;
    }
}
