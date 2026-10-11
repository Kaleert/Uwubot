# 📅 Умный бот расписания

Телеграм-бот для студентов, который автоматически парсит расписание из Excel-файлов (edu.tatar.ru), следит за изменениями и уведомляет группу только о реальных правках.

> 💡 **Nyagram Showcase**
> Этот проект является демонстрацией возможностей фреймворка **Nyagram**.
> Здесь показана лишь небольшая часть его функций: обработка команд, пайплайны, работа с клавиатурами и FSM.

## ✨ Возможности

*   **🧩 Умный парсинг Excel**: Автоматически определяет структуру файла (даже если колонки сдвинулись) и извлекает расписание с учетом объединенных ячеек.
*   **🔔 Smart Diff**: Бот понимает разницу между "новой неделей" и "правками текущей". При изменениях отправляет уведомление с выделением (курсив/зачеркивание).
*   **⚙️ Персонализация**:
    *   Выбор группы.
    *   **Алиасы**: Возможность переименовывать предметы (напр. "Математика" -> "Матеша").
    *   Скрытие/показ кодов предметов (ОД.05 и т.д.).
*   **🕓 Расписание звонков**: Парсится автоматически из файла.
*   **🛡 Стабильность**: Настроен для работы в нестабильных сетях (Termux, мобильный интернет) с авто-реконнектом к БД.

## 🛠 Технологии

*   **Java 21**
*   **Spring Boot 3.2** (Data JPA, Web, Scheduling)
*   **Nyagram** (Telegram Bot Framework)
*   **MySQL** (хранение данных)
*   **Apache POI** (работа с Excel)
*   **Jsoup** (парсинг веб-страниц)

## 🚀 Установка и Запуск

1.  **Соберите проект:**
    ```bash
    ./gradlew build
    ```

2.  **Подготовьте конфигурацию:**
    При первом запуске бот создаст файл `config.yml`. Отредактируйте его или создайте заранее.

3.  **Запустите:**
    ```bash
    java -jar build/libs/uwubot-1.1.1.jar
    ```

## ⚙️ Конфигурация (`config.yml`)

```yaml

nyagram:
  bot-token: "123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11"
  bot-username: "MyScheduleBot"
  admin-id: 123456789 # ID админа (для тестов и уведомлений)
  mode: POLLING
  
  # Интервал проверки сайта (в мс), здесь 10 минут
  scheduler:
    check-interval: 600000

spring:
  datasource:
    url: "jdbc:mysql://localhost:3306/uwubot?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&connectTimeout=60000&socketTimeout=60000&tcpKeepAlive=true&relaxAutoCommit=true&rewriteBatchedStatements=true"
    username: "root"
    password: "password"
    driver-class-name: "com.mysql.cj.jdbc.Driver"
  
  hikari:
      auto-commit: false
      maximum-pool-size: 5
      minimum-idle: 0
      connection-timeout: 60000
      idle-timeout: 30000
      max-lifetime: 60000
      keepalive-time: 30000
      test-on-borrow: true
      validation-timeout: 5000
      connection-test-query: SELECT 1

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        temp.use_jdbc_metadata_defaults: false
        jdbc.lob.non_contextual_creation: true
        connection.provider_disables_autocommit: false # Важно для MySQL драйвера
        
        jdbc.batch_size: 50
        order_inserts: true
        order_updates: true

logging:
  level:
    pro.kaleert.uwubot: INFO
    pro.kaleert.nyagram: INFO
```

## 🎮 Команды

### Пользователь
*   `/start` — Регистрация и приветствие.
*   `/rasp` (или `рп`) — Показать расписание (своей группы).
*   `/rasp [группа]` — Показать расписание другой группы.
*   `/group [группа]` — Выбрать/сменить свою группу.
*   `/bells` (или `звонки`) — Показать расписание звонков.
*   `/settings` — Инлайн-меню настроек (уведомления, алиасы, коды).
*   `/alias [Старое]=[Новое]` — Создать алиас для предмета.
*   `/help` — Справка.

### Админ
*   `/stats` — Статистика бота (пользователи, аптайм, статус парсера).
*   `/test parser [url]` — Проверить парсинг файла по ссылке (без сохранения в БД).
*   `/test broadcast [url]` — Симуляция рассылки (показывает Diff изменений).
*   `/test dump [url]` — **Debug**: Скачать детальный дамп структуры Excel (показывает скрытые символы, стили, объединения).
*   `/test clear` — Очистить таблицу уроков в базе данных.
```