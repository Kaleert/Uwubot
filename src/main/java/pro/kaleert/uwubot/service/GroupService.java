package pro.kaleert.uwubot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pro.kaleert.uwubot.repository.LessonRepository;
import pro.kaleert.uwubot.util.TextNormalizer;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final LessonRepository lessonRepository;

    public String resolveGroupName(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Введите номер группы.");
        }

        String normalizedInput = TextNormalizer.normalizeGroup(input);
        String rawInput = input.toUpperCase().replace("-", "").replace(" ", "").trim();

        List<String> allGroups = lessonRepository.findAllGroupNames();
        
        if (allGroups.isEmpty()) {
            return normalizedInput;
        }

        if (allGroups.contains(normalizedInput)) {
            return normalizedInput;
        }

        List<String> matches = allGroups.stream()
                .filter(dbGroup -> {
                    String cleanDbGroup = dbGroup.replace("-", "").replace(" ", "").toUpperCase(); // "И255"
                    return cleanDbGroup.contains(rawInput) || cleanDbGroup.endsWith(rawInput);
                })
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Группа не найдена в расписании.");
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }

        throw new IllegalArgumentException("Найдено несколько групп: " + String.join(", ", matches) + ". Уточните ввод (добавьте букву).");
    }
}