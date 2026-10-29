package com.courseplanner.api.auth;

import com.courseplanner.api.model.entities.plans.AppUser;
import com.courseplanner.api.model.repositories.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Profile("local")
public class LocalDevAutoLoginFilter extends OncePerRequestFilter {

    private static final String DEV_CAS_ID = "devuser";

    private final AppUserRepository userRepo;
    private final String emailDomain;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public LocalDevAutoLoginFilter(
            AppUserRepository userRepo,
            @Value("${cas.user.email.domain:example.edu}") String emailDomain
    ) {
        this.userRepo = userRepo;
        this.emailDomain = emailDomain;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null
                || !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
                || SecurityContextHolder.getContext().getAuthentication().getPrincipal().equals("anonymousUser")) {

            AppUser user = userRepo.findByCasId(DEV_CAS_ID)
                    .orElseGet(() -> userRepo.save(AppUser.fromCas(DEV_CAS_ID, emailDomain)));

            CasUserPrincipal principal = new CasUserPrincipal(user);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );

            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }

        chain.doFilter(request, response);
    }
}
