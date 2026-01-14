package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.command.*;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.repository.StudentRepository;
import pro.kaleert.uwubot.service.GroupService; // <-- Импорт

@BotCommand(value = "/group", description = "Установить группу")
@RequiredArgsConstructor
public class SetGroupCommand {

    private final StudentRepository studentRepository;
    private final GroupService groupService;

    @CommandHandler(aliases = {"группа"}) 
    public void setGroup(CommandContext context, 
                         @CommandArgument(value = "group_name", required = false) String groupName) {

        if (groupName == null || groupName.isBlank()) {
            String msg = """
                    ✍️ <b>Введите команду:</b>
                    <code>/group [имя/номер]</code>
                    
                    Например: <code>/group 255</code> или <code>/group И-255</code>
                    """;
            context.reply(msg, "HTML");
            return;
        }

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
            
            context.reply("✅ Группа <b>" + resolvedGroup + "</b> сохранена! Теперь жми /rasp", "HTML");
            
        } catch (IllegalArgumentException e) {
            context.reply("⚠️ " + e.getMessage());
        }
    }
}