package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.api.methods.updatingmessages.EditMessageText;
import com.kaleert.nyagram.callback.annotation.Callback;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.api.objects.replykeyboard.InlineKeyboardMarkup;
import com.kaleert.nyagram.util.keyboard.InlineKeyboardBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.entity.SubjectAlias;
import pro.kaleert.uwubot.repository.StudentRepository;
import pro.kaleert.uwubot.repository.SubjectAliasRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SettingsCallbacks {

    private final StudentRepository studentRepository;
    private final SubjectAliasRepository aliasRepository;

    @Callback("delete_msg")
    public void deleteMessage(CommandContext context) {
        context.deleteMessage(null);
    }

    @Callback("settings:toggle_notif")
    public void toggleNotifications(CommandContext context) {
        Student student = studentRepository.findById(context.getUserId()).orElse(null);
        if (student == null) return;
        student.setNotificationsEnabled(!student.isNotificationsEnabled());
        studentRepository.save(student);
        updateSettingsMenu(context, student);
    }

    @Callback("settings:toggle_codes")
    public void toggleCodes(CommandContext context) {
        Student student = studentRepository.findById(context.getUserId()).orElse(null);
        if (student == null) return;
        student.setShowCodes(!student.isShowCodes());
        studentRepository.save(student);
        updateSettingsMenu(context, student);
    }

    @Callback("settings:change_group")
    public void changeGroup(CommandContext context) {
        context.reply("✍️ Введите команду:\n<code>/group [новая_группа]</code>\n\nНапример: <code>/group И-255</code>", "HTML");
    }

    @Callback("settings:aliases")
    public void showAliases(CommandContext context) {
        Long userId = context.getUserId();
        List<SubjectAlias> aliases = aliasRepository.findAllByUserId(userId);

        if (aliases.isEmpty()) {
            editMessage(context, "⚙️ <b>Настройки > Алиасы</b>\n\nУ вас пока нет замен.\nИспользуйте: <code>/alias Мат = Матеша</code>", 
                InlineKeyboardBuilder.create().button("🔙 Назад", "settings:back").build());
            return;
        }

        InlineKeyboardBuilder builder = InlineKeyboardBuilder.create();
        StringBuilder text = new StringBuilder("⚙️ <b>Ваши алиасы:</b>\n\n");

        for (SubjectAlias alias : aliases) {
            text.append(String.format("• %s ➝ <b>%s</b>\n", alias.getOriginalName(), alias.getAliasName()));
            builder.button("🗑 " + alias.getOriginalName(), "alias:delete:" + alias.getId()).row();
        }
        builder.button("🔙 Назад", "settings:back");

        editMessage(context, text.toString(), builder.build());
    }

    @Callback("alias:delete:{id}")
    public void deleteAlias(CommandContext context, @com.kaleert.nyagram.callback.annotation.CallbackVar("id") Long aliasId) {
        if (aliasRepository.existsById(aliasId)) {
            aliasRepository.deleteById(aliasId);
        }
        showAliases(context);
    }

    @Callback("settings:back")
    public void backToMain(CommandContext context) {
        Student student = studentRepository.findById(context.getUserId()).orElse(null);
        updateSettingsMenu(context, student);
    }

    private void updateSettingsMenu(CommandContext context, Student student) {
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

        editMessage(context, text, markup);
    }

    private void editMessage(CommandContext context, String text, com.kaleert.nyagram.api.objects.replykeyboard.ReplyKeyboard markup) {
        try {
            context.getClient().execute(EditMessageText.builder()
                    .chatId(context.getChatId().toString())
                    .messageId(context.getMessage().get().getMessageId().intValue())
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup((InlineKeyboardMarkup) markup)
                    .build());
        } catch (Exception e) {
            // ignore
        }
    }
}