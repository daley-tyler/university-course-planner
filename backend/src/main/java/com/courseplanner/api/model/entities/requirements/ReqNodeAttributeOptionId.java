package com.courseplanner.api.model.entities.requirements;
import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
class ReqNodeAttributeOptionId implements java.io.Serializable {
    private Long reqNodeId;
    private Integer attributeId;
}