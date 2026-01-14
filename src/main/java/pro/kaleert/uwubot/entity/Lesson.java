package pro.kaleert.uwubot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.DayOfWeek;

@Entity
@Data
@Table(name = "lessons", indexes = {
    @Index(name = "idx_group_day", columnList = "group_name, day_of_week")
})

@EqualsAndHashCode(exclude = "id") 
public class Lesson {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_name")
    private String groupName;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek dayOfWeek;

    private int lessonNumber;

    @Column(length = 1000)
    private String rawText;
}