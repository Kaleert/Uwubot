package pro.kaleert.uwubot.command;

import pro.kaleert.nyagram.api.objects.replykeyboard.InlineKeyboardMarkup;
import pro.kaleert.nyagram.command.BotCommand;
import pro.kaleert.nyagram.command.CommandContext;
import pro.kaleert.nyagram.command.CommandHandler;
import pro.kaleert.nyagram.util.keyboard.InlineKeyboardBuilder;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.repository.StudentRepository;

@BotCommand(value = "/settings", description = "Настройки")
@RequiredArgsConstructor
public class SettingsCommand {

    private final StudentRepository studentRepository;

    @CommandHandler(aliases = {"настройки", "settings"})
    public void execute(CommandContext context) {
        Student student = studentRepository.findById(context.getUserId()).orElse(null);
        if (student == null) {
            context.reply("Сначала нажмите /start");
            return;
        }
        sendSettingsMenu(context, student);
    }

    public static void sendSettingsMenu(CommandContext context, Student student) {
        String group = student.getSelectedGroup() != null ? student.getSelectedGroup() : "Не выбрана";
        String notifStatus = student.isNotificationsEnabled() ? "✅ Вкл" : "🔕 Выкл";
        String codesStatus = student.isShowCodes() ? "✅ Вкл" : "🔕 Выкл";

        String text = String.format("""
                ⚙️ <b>Настройки</b>
                
                👤 Группа: <b>%s</b>
                🔔 Уведомления: <b>%s</b>
                🔢 Коды предметов: <b>%s</b>
                """, group, notifStatus, codesStatus);

        InlineKeyboardMarkup markup = InlineKeyboardBuilder.create()
                .button("📝 Мои алиасы", "settings:aliases")
                .button("🔔 Уведомления", "settings:toggle_notif")
                .row()
                .button("🔢 Коды предметов", "settings:toggle_codes")
                .button("🔄 Сменить группу", "settings:change_group")
                .row()
                .button("❌ Закрыть", "delete_msg")
                .build();

        context.reply(text, "HTML", null, markup);
    }

    @pro.kaleert.nyagram.callback.annotation.Callback("cmd:settings")
    public void onSettingsCallback(CommandContext context) {
        context.answerCallback("Открываю настройки...", false);
        execute(context);
    }
}