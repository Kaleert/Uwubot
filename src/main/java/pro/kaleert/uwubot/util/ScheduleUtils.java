package pro.kaleert.uwubot.util;

import pro.kaleert.uwubot.entity.Lesson;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ScheduleUtils {

    public static List<Lesson> fillGaps(List<Lesson> rawLessons) {
        if (rawLessons.isEmpty()) return rawLessons;

        int maxPair = rawLessons.stream()
                .mapToInt(Lesson::getLessonNumber)
                .max().orElse(0);

        var lessonMap = rawLessons.stream()
                .collect(Collectors.toMap(Lesson::getLessonNumber, l -> l));

        return IntStream.rangeClosed(1, Math.max(4, maxPair)) // Минимум 4 пары показываем
                .mapToObj(i -> lessonMap.getOrDefault(i, createEmptyLesson(i)))
                .sorted(Comparator.comparingInt(Lesson::getLessonNumber))
                .collect(Collectors.toList());
    }

    private static Lesson createEmptyLesson(int number) {
        Lesson l = new Lesson();
        l.setLessonNumber(number);
        l.setRawText("—");
        return l;
    }
}