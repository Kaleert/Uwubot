package pro.kaleert.uwubot.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import pro.kaleert.nyagram.api.methods.send.CopyMessage;
import pro.kaleert.nyagram.api.methods.send.ForwardMessage;
import pro.kaleert.nyagram.api.methods.updatingmessages.EditMessageText;
import pro.kaleert.nyagram.api.objects.replykeyboard.InlineKeyboardMarkup;
import pro.kaleert.nyagram.callback.annotation.Callback;
import pro.kaleert.nyagram.command.BotCommand;
import pro.kaleert.nyagram.command.CommandContext;
import pro.kaleert.nyagram.command.CommandHandler;
import pro.kaleert.nyagram.core.CommandResult;
import pro.kaleert.nyagram.fsm.SessionManager;
import pro.kaleert.nyagram.fsm.UserSession;
import pro.kaleert.nyagram.fsm.annotation.StateAction;
import pro.kaleert.nyagram.security.LevelRequired;
import pro.kaleert.nyagram.util.keyboard.InlineKeyboardBuilder;
import pro.kaleert.nyagram.core.concurrency.NyagramExecutor;
import pro.kaleert.nyagram.client.NyagramClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.kaleert.uwubot.repository.StudentRepository;
import pro.kaleert.uwubot.entity.Student;

import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@BotCommand
@RequiredArgsConstructor
public class BroadcastCommand {

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final StudentRepository studentRepository;
    private final NyagramExecutor taskExecutor; 
    private final NyagramClient nyagramClient;

    @CommandHandler(value = "/broadcast", description = "Массовая рассылка")
    @LevelRequired(min = 10)
    public void startBroadcast(CommandContext ctx) {
        ctx.deleteMessageQuietly(null); 
        sessionManager.startSession(ctx.getUserId(), ctx.getChatId(), "BC_WAIT_MESSAGE");
        var kb = InlineKeyboardBuilder.create().button("❌ Отмена", "bc:cancel", "danger").build();
        
        ctx.reply("Отправьте мне пост, который вы хотите разослать всем студентам:", "HTML", null, kb)
           .thenAccept(msg -> {
               UserSession session = sessionManager.getSession(ctx.getUserId());
               if (session != null) {
                   session.putData("promptMsgId", msg.getMessageId().intValue());
                   sessionManager.saveSession(session);
               }
           });
    }

    @StateAction("BC_WAIT_MESSAGE")
    public void onMessageReceived(CommandContext ctx, UserSession session) {
        session.putData("bcChatId", ctx.getChatId());
        session.putData("bcMsgId", ctx.getMessage().get().getMessageId().intValue());
        if (session.getData("bcMarkup", Object.class) == null) {
            session.putData("bcMarkup", null);
        }
        
        Integer promptId = session.getData("promptMsgId", Integer.class);
        if (promptId != null) ctx.deleteMessageQuietly(promptId);
        
        showMenu(ctx, session, false);
    }

    @StateAction("BC_WAIT_BUTTONS")
    public void onButtonsReceived(CommandContext ctx, UserSession session) {
        String text = ctx.getText().trim();
        ctx.deleteMessageQuietly(null);

        InlineKeyboardMarkup markup = parseButtons(text);
        if (markup == null) {
            ctx.reply("❌ Ошибка парсинга кнопок. Проверьте формат. Попробуйте еще раз:");
            return;
        }
        
        session.putData("bcMarkup", markup);
        showMenu(ctx, session, false);
    }

    @Callback("bc:toggle_filter")
    public CommandResult onToggleFilter(CommandContext ctx) {
        UserSession session = sessionManager.getSession(ctx.getUserId());
        if (session == null) return CommandResult.noResponse();
        
        String f = session.getData("bcFilter", String.class);
        // Адаптация фильтров под базу студентов UwuBot
        if (f == null || f.equals("ALL")) f = "HAS_GROUP";
        else if (f.equals("HAS_GROUP")) f = "NO_GROUP";
        else if (f.equals("NO_GROUP")) f = "NOTIFICATIONS_ON";
        else f = "ALL";
        
        session.putData("bcFilter", f);
        showMenu(ctx, session, true);
        return CommandResult.noResponse();
    }

    @Callback("bc:edit:msg")
    public CommandResult onEditMsg(CommandContext ctx) {
        UserSession session = sessionManager.getSession(ctx.getUserId());
        if (session != null) {
            deleteOldPreview(ctx, session);
            session.setState("BC_WAIT_MESSAGE");
            var kb = InlineKeyboardBuilder.create().button("❌ Отмена", "bc:cancel", "danger").build();
            ctx.reply("Отправьте новое сообщение для рассылки:", "HTML", null, kb).thenAccept(msg -> {
               session.putData("promptMsgId", msg.getMessageId().intValue());
               sessionManager.saveSession(session);
            });
        }
        return CommandResult.noResponse();
    }

    @Callback("bc:edit:btn")
    public CommandResult onEditBtn(CommandContext ctx) {
        UserSession session = sessionManager.getSession(ctx.getUserId());
        if (session != null) {
            session.setState("BC_WAIT_BUTTONS");
            sessionManager.saveSession(session);
            var kb = InlineKeyboardBuilder.create().button("❌ Отмена", "bc:cancel_btn_edit", "danger").build();
            ctx.editMessage("Отправьте кнопки в формате:\n\nОплатить - https://t.me/...\nКнопка 2 - data_123", kb);
        }
        return CommandResult.noResponse();
    }

    @Callback("bc:cancel_btn_edit")
    public CommandResult onCancelBtnEdit(CommandContext ctx) {
        UserSession session = sessionManager.getSession(ctx.getUserId());
        if (session != null) showMenu(ctx, session, true);
        return CommandResult.noResponse();
    }

    @Callback("bc:del:btn")
    public CommandResult onDelBtn(CommandContext ctx) {
        UserSession session = sessionManager.getSession(ctx.getUserId());
        if (session != null) {
            session.putData("bcMarkup", null);
            showMenu(ctx, session, false);
        }
        return CommandResult.noResponse();
    }

    @Callback("bc:start")
    public CommandResult onStart(CommandContext ctx) {
        UserSession session = sessionManager.getSession(ctx.getUserId());
        if (session == null || (!"BC_PREVIEW".equals(session.getState()) && !"BC_WAIT_BUTTONS".equals(session.getState()))) {
            return CommandResult.noResponse();
        }

        Long bcChatId = session.getData("bcChatId", Long.class);
        Integer bcMsgId = session.getData("bcMsgId", Integer.class);
        Object rawMarkup = session.getData("bcMarkup", Object.class);
        String filter = session.getData("bcFilter", String.class);
        if (filter == null) filter = "ALL";
        
        InlineKeyboardMarkup markup = rawMarkup != null ? objectMapper.convertValue(rawMarkup, InlineKeyboardMarkup.class) : null;

        String finalFilter = filter;
        new Thread(() -> executeSafeBroadcast(bcChatId, bcMsgId, markup, finalFilter, ctx.getUserId()), "bc-worker").start();

        deleteOldPreview(ctx, session);
        sessionManager.clearSession(ctx.getUserId());
        return CommandResult.success("✅ Рассылка запущена! Отчет придет в этот чат.");
    }

    @Callback("bc:cancel")
    public CommandResult onCancel(CommandContext ctx) {
        UserSession session = sessionManager.getSession(ctx.getUserId());
        if (session != null) {
            deleteOldPreview(ctx, session);
            sessionManager.clearSession(ctx.getUserId());
        }
        ctx.deleteMessageQuietly(null);
        return CommandResult.success("🚫 Рассылка отменена.");
    }

    private void showMenu(CommandContext ctx, UserSession session, boolean isUpdateOnlyMenu) {
        session.setState("BC_PREVIEW"); 
        
        Long bcChatId = session.getData("bcChatId", Long.class);
        Integer bcMsgId = session.getData("bcMsgId", Integer.class);
        Object rawMarkup = session.getData("bcMarkup", Object.class);
        
        String filter = session.getData("bcFilter", String.class);
        if (filter == null) filter = "ALL";
        
        InlineKeyboardMarkup markup = rawMarkup != null ? objectMapper.convertValue(rawMarkup, InlineKeyboardMarkup.class) : null;
        
        List<Long> targets = getTargetList(filter);
        
        String filterName = switch (filter) {
            case "HAS_GROUP" -> "С ВЫБРАННОЙ ГРУППОЙ";
            case "NO_GROUP" -> "БЕЗ ГРУППЫ";
            case "NOTIFICATIONS_ON" -> "УВЕДОМЛЕНИЯ ВКЛЮЧЕНЫ";
            default -> "ВСЕ СТУДЕНТЫ";
        };

        String filterText = "🎯 Аудитория: " + filterName + " (" + targets.size() + " чел.)";

        var kb = InlineKeyboardBuilder.create()
                .button(filterText, "bc:toggle_filter", "primary").row()
                .button("📝 Изм. пост", "bc:edit:msg", "primary")
                .button("⌨️ Кнопки", "bc:edit:btn", "primary")
                .button("🗑 Удал. кнопки", "bc:del:btn", "danger").row()
                .button("✅ ЗАПУСТИТЬ", "bc:start", "success")
                .button("❌ Отмена", "bc:cancel", "danger")
                .build();
        
        String controlText = "⚙️ <b>Управление рассылкой:</b>\n<i>Нажимайте кнопки ниже для настройки. Сообщение обновляется без спама.</i>";

        if (isUpdateOnlyMenu) {
            Integer controlMsgId = session.getData("controlMsgId", Integer.class);
            if (controlMsgId != null) {
                ctx.getClient().executeAsync(EditMessageText.builder()
                        .chatId(ctx.getChatId().toString()).messageId(controlMsgId)
                        .text(controlText).parseMode("HTML").replyMarkup(kb).build());
            }
            sessionManager.saveSession(session);
        } else {
            deleteOldPreview(ctx, session);

            ctx.reply("👀 <b>Предпросмотр рассылки:</b>", "HTML").thenAccept(headerMsg -> {
                session.putData("previewHeaderId", headerMsg.getMessageId().intValue());

                if (markup == null) {
                    ctx.getClient().executeAsync(ForwardMessage.builder()
                            .chatId(ctx.getChatId().toString()).fromChatId(bcChatId.toString()).messageId(bcMsgId).build())
                    .thenAccept(fwdMsg -> {
                        session.putData("previewMsgId", fwdMsg.getMessageId().intValue());
                        sendControlPanel(ctx, session, controlText, kb);
                    });
                } else {
                    ctx.getClient().executeAsync(CopyMessage.builder()
                            .chatId(ctx.getChatId().toString()).fromChatId(bcChatId.toString()).messageId(bcMsgId).replyMarkup(markup).build())
                    .thenAccept(copyMsg -> {
                        session.putData("previewMsgId", copyMsg.messageId());
                        sendControlPanel(ctx, session, controlText, kb);
                    });
                }
            });
        }
    }

    private void deleteOldPreview(CommandContext ctx, UserSession session) {
        Integer hId = session.getData("previewHeaderId", Integer.class);
        Integer pId = session.getData("previewMsgId", Integer.class);
        Integer cId = session.getData("controlMsgId", Integer.class);

        if (hId != null) ctx.deleteMessageQuietly(hId);
        if (pId != null) ctx.deleteMessageQuietly(pId);
        if (cId != null) ctx.deleteMessageQuietly(cId);

        session.putData("previewHeaderId", null);
        session.putData("previewMsgId", null);
        session.putData("controlMsgId", null);
    }

    private InlineKeyboardMarkup parseButtons(String text) {
        try {
            var kb = InlineKeyboardBuilder.create();
            for (String line : text.split("\n")) {
                if (line.isBlank()) continue;
                String[] parts = line.split("-", 2);
                if (parts.length == 2) {
                    kb.smartButton(parts[0].trim(), parts[1].trim()).row();
                } else return null;
            }
            return kb.build();
        } catch (Exception e) { return null; }
    }

    private void sendControlPanel(CommandContext ctx, UserSession session, String text, InlineKeyboardMarkup kb) {
        ctx.reply(text, "HTML", null, kb).thenAccept(controlMsg -> {
            session.putData("controlMsgId", controlMsg.getMessageId().intValue());
            sessionManager.saveSession(session);
        });
    }

    private void executeSafeBroadcast(Long fromChatId, Integer messageId, InlineKeyboardMarkup replyMarkup, String filter, Long adminId) {
        log.info("🚀 Старт рассылки от админа {}. Фильтр: {}", adminId, filter);
        long startTime = System.currentTimeMillis();
        AtomicLong total = new AtomicLong();
        AtomicLong success = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        
        List<Long> targetIds = getTargetList(filter);
        Phaser phaser = new Phaser(1); 

        for (Long chatId : targetIds) {
            total.incrementAndGet();
            phaser.register();
            
            taskExecutor.execute(chatId, () -> {
                try {
                    if (replyMarkup == null) {
                        nyagramClient.execute(ForwardMessage.builder()
                                .chatId(chatId.toString())
                                .fromChatId(fromChatId.toString())
                                .messageId(messageId).build());
                    } else {
                        nyagramClient.execute(CopyMessage.builder()
                                .chatId(chatId.toString())
                                .fromChatId(fromChatId.toString())
                                .messageId(messageId)
                                .replyMarkup(replyMarkup).build());
                    }
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    phaser.arriveAndDeregister();
                }
            });

            try { Thread.sleep(40); } catch (InterruptedException ignored) {}
        }

        phaser.arriveAndAwaitAdvance();
        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ Рассылка завершена. Успех: {}, Ошибки: {}", success.get(), failed.get());

        String reportText = "📊 <b>Рассылка завершена!</b>\n\n" +
            "Всего попыток: <b>" + total.get() + "</b>\n" +
            "✅ Успешно доставлено: <b>" + success.get() + "</b>\n" +
            "❌ Ошибки / Заблочили: <b>" + failed.get() + "</b>\n" +
            "⏱ Время выполнения: <b>" + (duration / 1000.0) + " сек.</b>";
            
        try {
            nyagramClient.executeAsync(pro.kaleert.nyagram.api.methods.send.SendMessage.builder()
                    .chatId(adminId.toString())
                    .text(reportText)
                    .parseMode("HTML")
                    .build());
        } catch (Exception ignored) {}
    }
    
    private List<Long> getTargetList(String filter) {
        List<Student> allUsers = studentRepository.findAll();
        
        return allUsers.stream()
            .filter(u -> {
                if ("HAS_GROUP".equals(filter)) return u.getSelectedGroup() != null;
                if ("NO_GROUP".equals(filter)) return u.getSelectedGroup() == null;
                if ("NOTIFICATIONS_ON".equals(filter)) return u.isNotificationsEnabled();
                return true; // "ALL"
            })
            .map(Student::getChatId)
            .toList();
    }
}