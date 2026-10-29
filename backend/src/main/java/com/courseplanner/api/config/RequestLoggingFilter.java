package com.courseplanner.api.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String method = req.getMethod();
        String path = req.getRequestURI();
        // Query parameters may contain CAS tickets or other credentials.

        // Skip noisy health/actuator paths
        if (path.startsWith("/actuator") || path.equals("/api/hello")) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        log.debug("→ {} {}", method, path);

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = res.getStatus();

            if (status >= 500) {
                log.error("← {} {} {} ({}ms)", status, method, path, duration);
            } else if (status >= 400) {
                log.warn("← {} {} {} ({}ms)", status, method, path, duration);
            } else {
                log.debug("← {} {} {} ({}ms)", status, method, path, duration);
            }
        }
    }
}
