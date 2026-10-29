package com.courseplanner.api.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulletinSummaryDto {
    private Long id;
    private Integer catalogYear;
    private String catalogLabel;
    private String bulletinRoot;
    private LocalDateTime scrapedAt;
    @JsonProperty("isCurrent")
    private boolean current;
}
