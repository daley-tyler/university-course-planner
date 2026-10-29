package com.courseplanner.api.api.controllers;

import com.courseplanner.api.api.dto.BulletinListResponse;
import com.courseplanner.api.api.services.BulletinQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/bulletins")
@RequiredArgsConstructor
public class BulletinQueryController {

    private final BulletinQueryService bulletinQueryService;

    @GetMapping
    public ResponseEntity<BulletinListResponse> list(@RequestParam(defaultValue = "5") int limit) {
        log.debug("GET /api/bulletins limit={}", limit);
        BulletinListResponse response = bulletinQueryService.listRecent(limit);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(response);
    }
}
