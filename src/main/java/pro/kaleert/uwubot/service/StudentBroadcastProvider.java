package pro.kaleert.uwubot.service;

import com.kaleert.nyagram.feature.broadcast.spi.BroadcastTargetProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.repository.StudentRepository;

import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StudentBroadcastProvider implements BroadcastTargetProvider {

    private final StudentRepository studentRepository;

    @Override
    public Stream<Long> getTargetChatIds() {
        return studentRepository.findAll().stream()
                .map(Student::getChatId)
                .distinct();
    }
}
