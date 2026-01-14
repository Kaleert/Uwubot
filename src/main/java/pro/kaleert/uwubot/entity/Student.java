package pro.kaleert.uwubot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "students")
public class Student {
    @Id
    private Long userId;
    
    private Long chatId;
    
    private String firstName;
    
    private String selectedGroup;
    
    private boolean notificationsEnabled = true;

    private boolean showCodes = false; 
}