package com.courseplanner.api.auth;

import com.courseplanner.api.model.entities.plans.AuthEventLog;
import com.courseplanner.api.model.repositories.AuthEventLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthAuditService {

    @Autowired
    private AuthEventLogRepository logRepo;

    public void log(String eventType, Authentication auth, HttpServletRequest req) {
        AuthEventLog entry = new AuthEventLog();
        entry.setEventType(eventType);
        entry.setIpAddress(getClientIp(req));
        entry.setUserAgent(req.getHeader("User-Agent"));
        entry.setOccurredAt(Instant.now());

        if (auth != null && auth.getPrincipal() instanceof CasUserPrincipal p) {
            entry.setCasId(p.getUser().getCasId());
        }

        logRepo.save(entry);
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
