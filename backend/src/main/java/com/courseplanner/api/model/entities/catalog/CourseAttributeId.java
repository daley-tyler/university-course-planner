package com.courseplanner.api.model.entities.catalog;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
class CourseAttributeId implements java.io.Serializable {
    private Long courseId;
    private Integer attributeId;
}