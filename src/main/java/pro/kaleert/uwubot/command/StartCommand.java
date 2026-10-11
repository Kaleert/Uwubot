package pro.kaleert.uwubot.command;

import pro.kaleert.nyagram.api.objects.replykeyboard.InlineKeyboardMarkup;
import pro.kaleert.nyagram.callback.annotation.Callback;
import pro.kaleert.nyagram.command.BotCommand;
import pro.kaleert.nyagram.command.CommandContext;
import pro.kaleert.nyagram.command.CommandHandler;
import pro.kaleert.nyagram.util.keyboard.InlineKeyboardBuilder;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.repository.StudentRepository;
import pro.kaleert.nyagram.fsm.SessionManager;

@BotCommand(value = "/start", description = "Начать работу")
@RequiredArgsConstructor
public class StartCommand {

    private final StudentRepository studentRepository;
    private final SessionManager sessionManager;

    @CommandHandler
    public void execute(CommandContext context) {
        Long userId = context.getUserId();
        Student student = studentRepository.findById(userId).orElseGet(() -> {
            Student s = new Student();
            s.setUserId(userId);
            s.setChatId(context.getChatId());
            s.setFirstName(context.getTelegramUser().getFirstName());
            return s;
        });
        studentRepository.save(student);

        context.reply(getMainMenuText(), "HTML", null, getMainMenuKeyboard());
    }

    @Callback("cmd:back_to_start")
    public void onBackToStart(CommandContext context) {
        sessionManager.clearSession(context.getUserId());
        context.editMessage(getMainMenuText(), getMainMenuKeyboard());
    }

    public static String getMainMenuText() {
        return """
        👋 <b>Привет! Я бот с расписанием колледжа.</b>
        
        Для начала работы мне нужно знать твою группу.
        Нажми на кнопку ниже или используй команду /group.
        
        В <b>Настройках</b> можно включить уведомления, скрыть коды предметов и управлять алиасами.
        
        ➖➖➖➖➖➖➖➖➖➖
        <i>Sponsored by <a href="https://t.me/vpnka_official_bot?start=utm_rasp">VPN-KA</a> (<a href="https://t.me/vpnka_official">Канал</a>)</i>
        <i>Powered by <a href="https://github.com/kaleert/nyagram">Nyagram</a></i>
        """;
    }

    public static InlineKeyboardMarkup getMainMenuKeyboard() {
        return InlineKeyboardBuilder.create()
                .button("📅 Расписание", "cmd:rasp", "primary").row()
                .button("✍️ Установить группу", "cmd:set_group", "success").row()
                .button("⚙️ Настройки", "cmd:settings")
                .build();
    }
}