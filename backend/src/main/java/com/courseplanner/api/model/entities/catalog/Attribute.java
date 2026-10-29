package com.courseplanner.api.model.entities.catalog;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "attribute")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor       // required for @Builder
@Builder                  // adds Attribute.builder()
public class Attribute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String category;
}
