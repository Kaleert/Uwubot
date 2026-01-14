package pro.kaleert.uwubot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pro.kaleert.uwubot.entity.Lesson;

import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    
    List<Lesson> findByGroupName(String groupName);
    
    void deleteAll();

    @Query("SELECT DISTINCT l.groupName FROM Lesson l")
    List<String> findAllGroupNames();
}