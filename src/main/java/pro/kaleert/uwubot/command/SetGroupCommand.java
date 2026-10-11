package pro.kaleert.uwubot.command;

import pro.kaleert.nyagram.command.*;
import pro.kaleert.nyagram.callback.annotation.Callback;
import pro.kaleert.nyagram.fsm.SessionManager;
import pro.kaleert.nyagram.fsm.UserSession;
import pro.kaleert.nyagram.fsm.annotation.StateAction;
import pro.kaleert.nyagram.util.keyboard.InlineKeyboardBuilder;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.repository.StudentRepository;
import pro.kaleert.uwubot.service.GroupService;

@BotCommand(value = "/group", description = "Установить группу")
@RequiredArgsConstructor
public class SetGroupCommand {

    private final StudentRepository studentRepository;
    private final GroupService groupService;
    private final SessionManager sessionManager;

    @CommandHandler(aliases = {"группа"}) 
    public void setGroup(CommandContext context, @CommandArgument(value = "group_name", required = false) String groupName) {
        if (groupName != null && !groupName.isBlank()) {
            processGroup(context, groupName, false);
            return;
        }
        sessionManager.startSession(context.getUserId(), context.getChatId(), "WAITING_GROUP");
        context.reply("✍️ <b>Введите номер или название вашей группы:</b>\n\n<i>Например: И-255 или просто 255.</i>\n<i>Для отмены напишите /cancel</i>", "HTML");
    }

    @Callback("cmd:set_group")
    public void onSetGroupCallback(CommandContext context) {
        sessionManager.startSession(context.getUserId(), context.getChatId(), "WAITING_GROUP");
        var kb = InlineKeyboardBuilder.create().button("🔙 Назад", "cmd:back_to_start").build();
        context.editMessage("✍️ <b>Введите номер или название вашей группы:</b>\n\n<i>Например: И-255 или просто 255.</i>", kb);
    }

    @StateAction(value = "WAITING_GROUP", clearAfter = true)
    public void onGroupInput(CommandContext context, UserSession session) {
        String input = context.getText();
        if (input == null || input.isBlank()) {
            context.reply("Пожалуйста, отправьте текст.");
            return;
        }
        processGroup(context, input, true);
    }

    private void processGroup(CommandContext context, String groupName, boolean isFromFsm) {
        try {
            String resolvedGroup = groupService.resolveGroupName(groupName);
            
            Student student = studentRepository.findById(context.getUserId())
                    .orElseGet(() -> {
                        Student s = new Student();
                        s.setUserId(context.getUserId());
                        s.setChatId(context.getChatId());
                        s.setFirstName(context.getTelegramUser().getFirstName());
                        return s;
                    });
            
            student.setSelectedGroup(resolvedGroup);
            studentRepository.save(student);
            sessionManager.clearSession(context.getUserId());
            
            String successText = "✅ Группа <b>" + resolvedGroup + "</b> успешно сохранена!\nТеперь вы можете нажать /rasp";
            var kb = InlineKeyboardBuilder.create().button("🔙 В меню", "cmd:back_to_start").build();
            
            if (isFromFsm) {
                context.reply(successText, "HTML", null, kb);
            } else {
                context.reply(successText, "HTML");
            }
            
        } catch (IllegalArgumentException e) {
            var kb = InlineKeyboardBuilder.create().button("🔙 Назад", "cmd:back_to_start").build();
            String err = "⚠️ " + e.getMessage() + "\n\n<i>Попробуйте еще раз.</i>";
            
            if (isFromFsm) context.reply(err, "HTML", null, kb);
            else context.reply(err, "HTML");
        }
    }
}