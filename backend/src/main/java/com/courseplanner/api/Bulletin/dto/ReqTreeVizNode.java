// src/main/java/com/courseplanner/api/Bulletin/Requirements/dto/ReqTreeVizNode.java
package com.courseplanner.api.Bulletin.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReqTreeVizNode {
    private String name;
    private String kind;            // e.g., ALL_OF, ANY_OF, N_OF, CREDIT_AT_LEAST, LEAF_COURSES
    private Integer nRequired;      // for N_OF
    private BigDecimal creditMin;   // for CREDIT_AT_LEAST
    private BigDecimal creditMax;   // for CREDIT_AT_LEAST
    private List<String> courses;   // only for LEAF_COURSES (e.g., ["CMR 101","IMS 392"])
    @Builder.Default
    private List<ReqTreeVizNode> children = new ArrayList<>();
}
