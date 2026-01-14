package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.api.objects.replykeyboard.ReplyKeyboardMarkup;
import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import com.kaleert.nyagram.util.keyboard.ReplyKeyboardBuilder;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.repository.StudentRepository;

@BotCommand(value = "/start", description = "Начать работу")
@RequiredArgsConstructor
public class StartCommand {

    private final StudentRepository studentRepository;

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

        String messageText = """
        👋 Привет! Я бот с расписанием колледжа.
        
        Для начала работы мне нужно знать твою группу.
        Напиши команду:
        <code>/group [твоя_группа]</code>
        
        Например: <code>/group И-255</code> или <code>/group 255</code>
        
        Потом можешь воспользоваться кнопками снизу.
        Также в <b>Настройках</b> можно включить уведомления, коды предметов и управлять алиасами и смотреть расписание других групп:
        <code>/rasp И-255</code> или <code>/rasp 255</code>.
        """;

        ReplyKeyboardMarkup keyboard = ReplyKeyboardBuilder.create()
                .button("📅 Расписание")
                .button("⚙️ Настройки")
                .resize()
                .build();

        context.reply(messageText, "HTML", null, keyboard);
    }
}