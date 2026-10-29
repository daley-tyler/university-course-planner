package com.courseplanner.api.restAPIControllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class UtilityController {

    @GetMapping("/hello")
    public String hello() {
        log.info("Hello endpoint called");
        return "Hello, World!";
    }
}
