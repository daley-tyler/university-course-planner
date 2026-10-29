package com.courseplanner.api.config;

import com.courseplanner.api.auth.AuthAuditService;
import com.courseplanner.api.auth.CasTicketValidationFilter;
import com.courseplanner.api.auth.LocalDevAutoLoginFilter;
import com.courseplanner.api.model.repositories.AppUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired private AppUserRepository userRepo;
    @Autowired private AuthAuditService auditService;
    @Autowired(required = false) private LocalDevAutoLoginFilter localDevAutoLoginFilter;
    @Value("${cas.service.url}") private String casServiceUrl;
    @Value("${cas.login.url}") private String casLoginUrl;
    @Value("${cas.validate.url}") private String casValidateUrl;
    @Value("${cas.user.email.domain}") private String casUserEmailDomain;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/courses/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/bulletins/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/programs/**").permitAll()
                .requestMatchers("/api/auth/**", "/login/cas").permitAll()
                .anyRequest().authenticated()
            )
            // Insert our custom CAS filter before Spring's auth filter
            .addFilterBefore(
                new CasTicketValidationFilter(
                        userRepo,
                        auditService,
                        casServiceUrl,
                        casValidateUrl,
                        casUserEmailDomain
                ),
                UsernamePasswordAuthenticationFilter.class
            )
            // Auto-login for local development (only active with 'local' profile)
            .addFilterBefore(
                localDevAutoLoginFilter != null ? localDevAutoLoginFilter : (req, res, chain) -> chain.doFilter(req, res),
                CasTicketValidationFilter.class
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(delegatingEntryPoint())
            )
            .sessionManagement(session -> session
                .sessionFixation().newSession()
                .maximumSessions(3)
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/login/cas", "/api/auth/logout")
            )
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31_536_000)
                    .includeSubDomains(true)
                )
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(ref -> ref
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> {
                    auditService.log("LOGOUT", auth, req);
                    res.sendRedirect(casServiceUrl);
                })
                .invalidateHttpSession(true)
                .deleteCookies("SESSION")
            )
            .cors(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint delegatingEntryPoint() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> map = new LinkedHashMap<>();
        map.put(request -> request.getServletPath().startsWith("/api/"),
            (req, res, ex) -> {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Unauthenticated\"}");
            }
        );
        DelegatingAuthenticationEntryPoint delegate = new DelegatingAuthenticationEntryPoint(map);
        // Default: redirect browser to CAS login
        delegate.setDefaultEntryPoint((req, res, ex) ->
            res.sendRedirect(
                casLoginUrl + "?service=" +
                URLEncoder.encode(casServiceUrl + "/login/cas", StandardCharsets.UTF_8)
            )
        );
        return delegate;
    }
}
