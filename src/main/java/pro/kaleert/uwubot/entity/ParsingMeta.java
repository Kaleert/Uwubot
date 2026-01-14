package pro.kaleert.uwubot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParsingMeta {
    @Id
    private String keyName;
    private String lastFileHash;
    private String lastFileUrl;
    
    private String lastDateRange;
    private LocalDate weekStart;
    
    @Column(length = 4096)
    private String lastBellSchedule; 
    
    private LocalDateTime lastCheckTime;
    private LocalDateTime lastSuccessfulUpdate;
}