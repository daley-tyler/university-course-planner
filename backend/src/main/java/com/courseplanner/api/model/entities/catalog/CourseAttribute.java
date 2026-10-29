package com.courseplanner.api.model.entities.catalog;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "course_attribute")
@Getter @Setter @NoArgsConstructor
public class CourseAttribute {
    @EmbeddedId
    private CourseAttributeId id = new CourseAttributeId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("courseId")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("attributeId")
    private Attribute attribute;
}