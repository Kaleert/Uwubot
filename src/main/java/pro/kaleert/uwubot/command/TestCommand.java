package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.api.methods.updatingmessages.EditMessageText;
import com.kaleert.nyagram.api.objects.message.Message;
import com.kaleert.nyagram.command.*;
import com.kaleert.nyagram.security.LevelRequired;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.Lesson;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.repository.LessonRepository;
import pro.kaleert.uwubot.repository.StudentRepository;
import pro.kaleert.uwubot.service.ScheduleDiffService;
import pro.kaleert.uwubot.service.UpdateService;
import pro.kaleert.uwubot.service.parser.ScheduleBundle;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@BotCommand(value = "/test", description = "Тесты")
@RequiredArgsConstructor
public class TestCommand {

    private final UpdateService updateService;
    private final StudentRepository studentRepository;
    private final LessonRepository lessonRepository;
    private final ScheduleDiffService diffService;

    @CommandHandler
    @LevelRequired(min = 10)
    public void execute(CommandContext context, 
                        @CommandArgument("mode") String mode, 
                        @CommandArgument("url") String url) {
        
        boolean isBroadcastTest;
        if (mode.equalsIgnoreCase("broadcast")) {
            isBroadcastTest = true;
        } else if (mode.equalsIgnoreCase("parser")) {
            isBroadcastTest = false;
        } else {
            context.reply("⚠️ Неверный режим. Используйте: <code>/test parser [url]</code> или <code>/test broadcast [url]</code>", "HTML");
            return;
        }

        testLogic(context, url, isBroadcastTest);
    }

    private void testLogic(CommandContext context, String url, boolean isBroadcastTest) {
        Student student = studentRepository.findById(context.getUserId()).orElse(null);
        if (student == null || student.getSelectedGroup() == null) {
            context.reply("Выбери группу");
            return;
        }

        Message statusMsg = context.reply("⏳ Запуск теста (" + (isBroadcastTest ? "BROADCAST" : "PARSER") + ")...").join();

        CompletableFuture.runAsync(() -> {
            try {
                String finalUrl = url.startsWith("http") ? url : "file://" + url;
                
                ScheduleBundle bundle = updateService.parseFileOnly(finalUrl);
                List<Lesson> newLessons = bundle.lessons();
                LocalDate fileDate = bundle.weekStart(); // 🔥 Берем дату из файла
                
                String myGroup = student.getSelectedGroup();
                List<Lesson> myNew = newLessons.stream()
                        .filter(l -> l.getGroupName().equalsIgnoreCase(myGroup))
                        .collect(Collectors.toList());

                if (myNew.isEmpty()) {
                    updateStatus(context, statusMsg, "❌ Группа <b>" + myGroup + "</b> не найдена в файле.");
                    return;
                }

                String result;
                if (isBroadcastTest) {
                    List<Lesson> myOld = lessonRepository.findByGroupName(myGroup);
                    
                    String diff = diffService.generateDiffReport(context.getUserId(), myGroup, myOld, myNew, fileDate);
                    
                    result = (diff == null) ? "✅ Изменений для вашей группы нет." : "📩 <b>Вид уведомления:</b>\n\n" + diff;
                } else {
                    result = RaspCommand.formatSchedule(myGroup, myNew, Collections.emptyMap(), true, fileDate);
                }

                context.reply(result, "HTML");
                context.getClient().execute(new com.kaleert.nyagram.api.methods.updatingmessages.DeleteMessage(context.getChatId().toString(), Math.toIntExact(statusMsg.getMessageId())));

            } catch (Exception e) {
                updateStatus(context, statusMsg, "❌ Ошибка: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void updateStatus(CommandContext context, Message msg, String text) {
        try {
            context.getClient().execute(EditMessageText.builder()
                    .chatId(context.getChatId().toString())
                    .messageId(Math.toIntExact(msg.getMessageId()))
                    .text(text)
                    .parseMode("HTML")
                    .build());
        } catch (Exception ignored) {}
    }
}