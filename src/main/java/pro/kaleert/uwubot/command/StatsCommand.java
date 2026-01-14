package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import com.kaleert.nyagram.util.TimeUtil;
import com.kaleert.nyagram.i18n.LocaleService;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.ParsingMeta;
import pro.kaleert.uwubot.repository.LessonRepository;
import pro.kaleert.uwubot.repository.ParsingMetaRepository;
import pro.kaleert.uwubot.repository.StudentRepository;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@BotCommand(value = "/stats", description = "Статистика бота")
@RequiredArgsConstructor
public class StatsCommand {

    private final StudentRepository studentRepository;
    private final LessonRepository lessonRepository;
    private final ParsingMetaRepository metaRepository;
    private final LocaleService localeService; 

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    @CommandHandler(aliases = {"стата", "статистика"})
    public void execute(CommandContext context) {
        long usersCount = studentRepository.count();
        long lessonsCount = lessonRepository.count();
        
        ParsingMeta meta = metaRepository.findById("schedule_file").orElse(null);
        String lastFile = (meta != null && meta.getLastFileUrl() != null) ? "Загружен" : "Нет";
        
        String lastCheck = (meta != null && meta.getLastCheckTime() != null) 
                ? meta.getLastCheckTime().format(TIME_FMT) 
                : "—";
                
        String lastUpdate = (meta != null && meta.getLastSuccessfulUpdate() != null) 
                ? meta.getLastSuccessfulUpdate().format(TIME_FMT) 
                : "—";

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        String uptime = TimeUtil.formatDuration(Duration.ofMillis(uptimeMs), localeService, new Locale("ru"));

        String text = String.format("""
                📊 <b>Статистика бота</b>
                
                👥 Пользователей: <b>%d</b>
                📅 Записей уроков: <b>%d</b>
                
                🕵️ Последняя проверка: <b>%s</b>
                💾 Последнее обновление: <b>%s</b>
                
                ⏱ Аптайм: <b>%s</b>
                """,
                usersCount, lessonsCount, lastCheck, lastUpdate, uptime
        );

        context.reply(text, "HTML");
    }
}