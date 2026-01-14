package pro.kaleert.uwubot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "subject_aliases", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "original_name"})
})
public class SubjectAlias {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "alias_name")
    private String aliasName;

    public SubjectAlias(Long userId, String originalName, String aliasName) {
        this.userId = userId;
        this.originalName = originalName;
        this.aliasName = aliasName;
    }
}
