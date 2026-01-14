package pro.kaleert.uwubot.service.parser;

import pro.kaleert.uwubot.entity.Lesson;
import java.time.LocalDate;
import java.util.List;

public record ScheduleBundle(
    List<Lesson> lessons, 
    String dateRangeString,
    LocalDate weekStart,
    String bellSchedule
) {}