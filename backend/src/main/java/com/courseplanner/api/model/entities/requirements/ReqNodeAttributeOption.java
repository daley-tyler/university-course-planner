package com.courseplanner.api.model.entities.requirements;

import com.courseplanner.api.model.entities.catalog.Attribute;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "req_node_attribute_option")
@Getter @Setter @NoArgsConstructor
public class ReqNodeAttributeOption {
    @EmbeddedId
    private ReqNodeAttributeOptionId id = new ReqNodeAttributeOptionId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("reqNodeId")
    private ReqNode reqNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("attributeId")
    private Attribute attribute;
}