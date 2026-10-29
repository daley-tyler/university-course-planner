package com.courseplanner.api.model.entities.requirements;

import com.courseplanner.api.model.entities.catalog.Course;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "req_node_course_option")
@Getter @Setter @NoArgsConstructor
public class ReqNodeCourseOption {
    @EmbeddedId
    private ReqNodeCourseOptionId id = new ReqNodeCourseOptionId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("reqNodeId")
    private ReqNode reqNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("courseId")
    private Course course;
}