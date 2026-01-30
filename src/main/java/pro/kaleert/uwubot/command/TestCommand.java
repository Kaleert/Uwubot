package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.api.methods.updatingmessages.EditMessageText;
import com.kaleert.nyagram.api.objects.message.Message;
import com.kaleert.nyagram.api.objects.InputFile;
import com.kaleert.nyagram.command.*;
import com.kaleert.nyagram.api.methods.send.SendDocument;
import com.kaleert.nyagram.security.LevelRequired;
import com.kaleert.nyagram.core.AsyncMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.kaleert.uwubot.entity.Lesson;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.repository.LessonRepository;
import pro.kaleert.uwubot.repository.StudentRepository;
import pro.kaleert.uwubot.service.ScheduleDiffService;
import pro.kaleert.uwubot.service.UpdateService;
import pro.kaleert.uwubot.service.parser.ScheduleBundle;
import pro.kaleert.uwubot.service.parser.ExcelDiagnosticService;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@BotCommand(value = "/test", description = "Инструменты администратора")
@RequiredArgsConstructor
public class TestCommand {

    private final UpdateService updateService;
    private final StudentRepository studentRepository;
    private final LessonRepository lessonRepository;
    private final ScheduleDiffService diffService;
    private final ExcelDiagnosticService diagnosticService;

    @CommandHandler(value = "parser", description = "Проверить парсинг файла")
    @LevelRequired(min = 10)
    public void testParser(CommandContext context, @CommandArgument("url") String url) {
        runTest(context, url, false);
    }

    @CommandHandler(value = "broadcast", description = "Проверить Diff рассылки")
    @LevelRequired(min = 10)
    public void testBroadcast(CommandContext context, @CommandArgument("url") String url) {
        runTest(context, url, true);
    }
    
    @CommandHandler(value = "clear", description = "Очистить расписание в БД")
    @LevelRequired(min = 10)
    public void clearDb(CommandContext context) {
        long count = lessonRepository.count();
        lessonRepository.deleteAll();
        context.reply("🗑 База данных очищена. Удалено записей: " + count);
    }
    
    @CommandHandler(value = "dump", description = "Скачать дамп структуры Excel")
    @LevelRequired(min = 10)
    @AsyncMode(AsyncMode.Mode.CONCURRENT)
    public void dumpStructure(CommandContext context, @CommandArgument("url") String urlString) {
        
        Message statusMsg = context.reply("⏳ Скачивание и анализ файла...").join();

        File dumpFile = null;
        try {
            URL rawUrl = new URL(urlString);
            
            String decodedPath = URLDecoder.decode(rawUrl.getPath(), StandardCharsets.UTF_8);
            
            String encodedUrl = new URI(
                    rawUrl.getProtocol(), 
                    rawUrl.getUserInfo(), 
                    rawUrl.getHost(), 
                    rawUrl.getPort(), 
                    decodedPath,
                    rawUrl.getQuery(), 
                    null
            ).toASCIIString();

            log.info("Downloading for dump: {}", encodedUrl);
            
            try (InputStream in = new URL(encodedUrl).openStream()) {
                dumpFile = diagnosticService.createDump(in);
            }

            SendDocument doc = SendDocument.builder()
                    .chatId(context.getChatId().toString())
                    .document(new InputFile(dumpFile, "schedule_dump.txt"))
                    .caption("📊 <b>Дамп структуры</b>\n\n🔗 Источник: " + urlString)
                    .parseMode("HTML")
                    .build();

            context.getClient().execute(doc);
            context.deleteMessage(Math.toIntExact(statusMsg.getMessageId()));

        } catch (Exception e) {
            log.error("Dump failed", e);
            try {
                context.getClient().execute(EditMessageText.builder()
                        .chatId(context.getChatId().toString())
                        .messageId(Math.toIntExact(statusMsg.getMessageId()))
                        .text("❌ <b>Ошибка:</b>\n" + e.getMessage())
                        .parseMode("HTML")
                        .build());
            } catch (Exception ignored) {}
        } finally {
            if (dumpFile != null && dumpFile.exists()) {
                boolean deleted = dumpFile.delete();
                if (!deleted) log.warn("Failed to delete temp dump file: {}", dumpFile.getAbsolutePath());
            }
        }
    }
    
    @CommandHandler(value = "broadcast", aliases = {"/tb"}, hidden = true)
    @LevelRequired(min = 10)
    public void testBroadcastShortcut(CommandContext context, @CommandArgument("url") String url) {
        runTest(context, url, true);
    }

    private void runTest(CommandContext context, String url, boolean isBroadcastTest) {
        Student student = studentRepository.findById(context.getUserId()).orElse(null);
        if (student == null || student.getSelectedGroup() == null) {
            context.reply("⚠️ Сначала выберите группу через /group");
            return;
        }

        Message statusMsg = context.reply("⏳ Запуск теста (" + (isBroadcastTest ? "BROADCAST" : "PARSER") + ")...").join();

        CompletableFuture.runAsync(() -> {
            try {
                URL rawUrlObj = new URL(url.startsWith("http") ? url : "file://" + url);
                String decodedPath = URLDecoder.decode(rawUrlObj.getPath(), StandardCharsets.UTF_8);
                
                String finalUrl = new URI(
                    rawUrlObj.getProtocol(), rawUrlObj.getUserInfo(), rawUrlObj.getHost(), 
                    rawUrlObj.getPort(), decodedPath, rawUrlObj.getQuery(), null
                ).toASCIIString();
                
                ScheduleBundle bundle = updateService.parseFileOnly(finalUrl);
                List<Lesson> newLessons = bundle.lessons();
                LocalDate fileDate = bundle.weekStart();
                
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
                    result = (diff == null) ? "✅ Изменений нет." : "📩 <b>Вид уведомления:</b>\n\n" + diff;
                } else {
                    result = RaspCommand.formatSchedule(myGroup, myNew, Collections.emptyMap(), true, fileDate);
                }

                context.reply(result, "HTML");
                context.deleteMessage(Math.toIntExact(statusMsg.getMessageId()));

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