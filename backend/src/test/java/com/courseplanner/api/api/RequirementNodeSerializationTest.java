package com.courseplanner.api.api;

import com.courseplanner.api.api.dto.RequirementNodeDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RequirementNodeSerializationTest {
    @Test
    void selectionCountUsesTheFrontendPropertyName() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.valueToTree(RequirementNodeDto.builder().nRequired(2).build());
        assertThat(json.get("nRequired").intValue()).isEqualTo(2);
        assertThat(json.has("nrequired")).isFalse();
        assertThat(mapper.treeToValue(json, RequirementNodeDto.class).getNRequired()).isEqualTo(2);
    }
}
