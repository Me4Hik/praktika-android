# Утверждённые продуктовые решения

## DEC-001 — Последовательная модель цикла

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Вопросы имеют постоянную позицию от 1 до 21 и не закреплены за конкретным временем суток.

После запуска практики ближайший будущий слот получает вопрос №1. Следующие слоты последовательно получают вопросы №2–21. После №21 следующий слот получает №1 нового цикла.

Конкретные дата, время и временной слот принадлежат `QuestionOccurrence`, а не `Question`.

### Пример

При запуске практики в 14:00:

- 15:00 — вопрос №1;
- 19:00 — вопрос №2;
- следующий день, 11:00 — вопрос №3.

### Последствия для модели данных

- `Question` хранит `cyclePosition`;
- `Question` не хранит день или время показа;
- `ScheduleSlot` хранит локальное время;
- `QuestionOccurrence` хранит конкретные дату, время, слот, цикл и статус.

## DEC-002 — Физическое удаление ответа

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

При удалении ответа строка `Answer` физически удаляется из базы.

`QuestionOccurrence` сохраняется, статус показа остаётся `ANSWERED`. Повторно ответить на старый показ нельзя. Удалённый текст отсутствует в архиве и экспорте.

### Последствия для модели данных

- поля `deletedAt`, `questionId`, `cycleNumber`, `topicSnapshot` и `questionTextSnapshot` в `Answer` отсутствуют;
- удаление выполняется через `DELETE`, а не soft delete.

## DEC-003 — Тема вопроса отсутствует в Room v1

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Поле `Question.topic` отсутствует в Room v1. Темы вопросов пока не утверждены.

### Последствия для модели данных

- архив и экспорт используют ID, позицию и текст вопроса;
- темы можно добавить позднее отдельной миграцией после утверждения классификации.

## DEC-004 — Статус CANCELLED отсутствует

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Допустимые статусы `QuestionOccurrence` в Room v1:

- `SCHEDULED`
- `AVAILABLE`
- `ANSWERED`
- `SKIPPED_BY_USER`
- `MISSED_BY_TIME`

Статус `CANCELLED` отсутствует.

## DEC-005 — Отдельный флаг знакомства отсутствует

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Поле `onboardingCompleted` отсутствует.

Логика будущего стартового экрана:

- `isPracticeStarted = false` → экран знакомства;
- `isPracticeStarted = true` → главный экран.

Отдельное поле допустимо добавить миграцией только при появлении самостоятельного сценария знакомства.

## DEC-006 — Снимок текста конкретного показа

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

`QuestionOccurrence.questionTextSnapshot` обязателен.

Снимок создаётся вместе с показом. Старые показы не зависят от последующего изменения `Question.text`. Архив использует снимок. `Answer` снимок не хранит.

### Последствия для модели данных

- миграция Room 1 → 2 добавляет колонку `questionTextSnapshot`;
- архивный JOIN читает `question_occurrences.questionTextSnapshot`, а не `questions.text`.

## DEC-007 — PracticeState создаётся во время seed

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Строка `PracticeState(id=1)` создаётся одной транзакцией с 21 вопросом и тремя слотами.

Отсутствие строки после инициализации считается повреждённым состоянием. `activeZoneId` берётся через `TimeZone.getDefault().id`. `seedVersion = 1`.

### Последствия для модели данных

- seed атомарно создаёт questions, schedule_slots и practice_state;
- `isPracticeStarted = false` до нажатия «Начать практику».

## DEC-008 — Включительная минута слота

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Вся минута установленного слота относится к этому слоту:

- 11:00:00.000–11:00:59.999 → слот 11:00;
- 15:00:00.000–15:00:59.999 → слот 15:00;
- 19:00:00.000–19:00:59.999 → слот 19:00.

Начиная со следующей минуты выбирается следующий слот. `plannedAtEpochMillis` хранит начало минуты слота. Допуск доставки уведомлений к расчёту цикла отношения не имеет.

## DEC-009 — Пауза до наступления SCHEDULED

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Если практика поставлена на паузу до наступления запланированного показа:

- строка `QuestionOccurrence` сохраняется, статус остаётся `SCHEDULED`;
- во время паузы показ не становится `MISSED_BY_TIME`;
- после возобновления тот же показ переносится на ближайший слот по DEC-008;
- сохраняются `id`, `questionId`, `questionTextSnapshot`, `cycleNumber`, `cyclePosition`;
- пересчитываются `scheduleSlotIndex`, `plannedAtEpochMillis`, `availableUntilEpochMillis`, `zoneId`;
- новый показ не создаётся, позиция цикла не изменяется.

## DEC-010 — Пауза во время AVAILABLE

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Если практика поставлена на паузу при доступном вопросе:

- тот же показ остаётся `AVAILABLE`, `plannedAtEpochMillis` сохраняется;
- во время паузы `MISSED_BY_TIME` не возникает;
- после возобновления дедлайн увеличивается на продолжительность паузы;
- следующий вопрос до завершения текущего не создаётся;
- календарные слоты, прошедшие во время паузы, игнорируются.

## DEC-011 — Время завершения MISSED_BY_TIME

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

`completedAtEpochMillis = availableUntilEpochMillis`. Время фактического запуска reconciliation не используется как время пропуска.

## DEC-012 — UI запуска откладывается

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

На Этапе 4 реализовать `CycleRepository.startPractice()` и полностью протестировать операцию. Кнопку «Начать практику» к базе не подключать. Визуальное поведение экранов не менять.

## DEC-013 — Следующий вопрос после замороженного показа

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

После завершения вопроса, срок которого сдвигался паузой:

- после `SKIPPED_BY_USER` или ответа (Этап 8) следующий вопрос планируется на первый слот строго после времени завершения;
- после `MISSED_BY_TIME` следующий вопрос планируется на первый слот строго после перенесённого дедлайна;
- слоты, прошедшие во время паузы, не восстанавливаются;
- два вопроса в один слот не назначаются.

## DEC-014 — Масштабируемое виртуальное время

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Ускоренная сборка использует формулу:

`virtualNow = virtualAnchorEpochMillis + (elapsedRealtimeNow - realAnchorElapsedRealtimeMillis) × speedMultiplier`

Стандартные слоты 11:00, 15:00, 19:00 не изменяются. `ScheduleCalculator` и `CycleRepository` не содержат условий тестового режима. Все timestamps ускоренной базы — единая виртуальная шкала.

## DEC-015 — Скорости и начальное состояние accelerated

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Preset: 60×, 240×, 600×. По умолчанию 240×. Новая сессия: локальная дата, 08:00, текущий zone, виртуальные часы на технической паузе. Практика и виртуальные часы — разные состояния.

## DEC-016 — Отдельный flavor accelerated

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Product flavors `production` и `accelerated` (dimension `runtimeMode`). Варианты: `productionDebug`, `productionRelease`, `acceleratedDebug`. `acceleratedRelease` отключён.

## DEC-017 — Полная изоляция данных

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Production: `com.me4hik.praktika`, `praktika.db`. Accelerated: `com.me4hik.praktika.accelerated`, `praktika_accelerated.db`. `allowBackup=false` для accelerated. Название «Практика (TEST)».

## DEC-018 — Process death и перезагрузка

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Process death в одном boot: часы продолжаются. После reboot: техническая пауза, `virtualNow >= max(checkpoint, lastProcessedAt)`, продолжение только после `resume_clock`.

## DEC-019 — Управление через ADB

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

На Этапе 5: без Compose-панели; управление через ADB broadcast; диагностика в Logcat. Accelerated-команды отсутствуют в production.

## DEC-020 — Полный reset accelerated sandbox

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

Reset через `adb shell pm clear com.me4hik.praktika.accelerated`. Production не затрагивается.

## DEC-021 — Смена скорости через re-anchor

**Дата:** 04.08.2026  
**Статус:** утверждено

### Решение

При смене multiplier: вычислить текущий virtualNow, установить новые anchors без скачка. Room timestamps не меняются.

## DEC-022 — SCHEDULED на главном экране

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

На главном экране для статуса `SCHEDULED` показывать:

- «Вопрос N из 21»;
- абсолютную локальную дату и время следующего показа;
- состояние ожидания.

Текст `questionTextSnapshot` до перехода в `AVAILABLE` не показывать.

## DEC-023 — AVAILABLE на главном экране

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Для `AVAILABLE` показывать:

- `questionTextSnapshot`;
- позицию «N из 21»;
- локальный дедлайн;
- кнопку «Ответить» (переход на question route-заглушку).

Полноценная логика вопроса — Этап 7. Question-заглушка не показывает фиктивный текст другого вопроса.

**Уточнение (05.08.2026, Промпт №020):** на Этапе 6 кнопка на Home называется «Ответить» и открывает question-placeholder без answer/skip/openedAt.

## DEC-024 — пауза на главном экране

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

При `isPaused=true`:

- показывать индикатор «Практика на паузе»;
- показывать сохранённый незавершённый показ;
- не добавлять кнопки pause/resume;
- не разрешать открытие вопроса.

## DEC-025 — практика уже запущена

**Дата:** 05.08.2026  
**Статус:** утверждено (применение перенесено на Этап 11)

### Решение

`CycleAlreadyStartedException` при повторном старте:

- не показывается как ошибка;
- не создаёт второй показ;
- UI продолжает читать Room;
- state-driven navigation открывает Home.

**Уточнение (05.08.2026, Промпт №020):** идемпотентность пользовательского повторного нажатия «Начать практику» относится к Этапу 11. На Этапе 6 пользовательский запуск из onboarding отключён.

## DEC-026 — номер цикла скрыт

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

`cycleNumber` — техническое поле, пользователю на Этапе 6 не показывается. Показывать только «Вопрос N из 21».

## DEC-027 — абсолютное локальное время

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Показывать абсолютное локальное время в формате «5 августа 2026, 15:00» с использованием `QuestionOccurrence.zoneId`.

Не добавлять «через N минут», countdown и метки «сегодня/завтра».

## DEC-028 — Back после запуска

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Cold start при уже запущенной практике (`isPracticeStarted=true`) открывает Home; Back с Home не возвращает onboarding. Back из Archive, Settings и Question возвращает Home.

**Уточнение (05.08.2026, Промпт №020):** удаление onboarding из back stack после пользовательского первого запуска относится к Этапу 11. На Этапе 6 сохраняется только поведение для уже запущенной практики и навигации Back с дочерних экранов.

## DEC-029 — единый PracticeRootViewModel

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Один `PracticeRootViewModel` для onboarding, запуска практики, наблюдения Room, состояния Home и ошибок. Отдельный onboarding-ViewModel не создаётся.

## DEC-030 — граница Этапов 6 и 11

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Этап 6 наблюдает и отображает состояние практики, но не запускает её из пользовательского onboarding.

На Этапе 6 практика для проверки Home может быть запущена:

- accelerated ADB-командой;
- instrumented-тестом;
- ранее сохранённым состоянием базы.

Пользовательская кнопка «Начать практику», выбор расписания, создание первого occurrence и переход после первого запуска относятся к Этапу 11.

## DEC-031 — повторный seed при пользовательских данных

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Повторный запуск seed разрешён при существующих QuestionOccurrence и Answer, если seed-owned структура базы совместима.

Question — seed-owned. ScheduleSlot и PracticeState после первоначального создания являются пользовательскими изменяемыми данными и не возвращаются к defaults.

Наличие пользовательских записей не является повреждением базы. Partial или несовместимая seed-owned структура остаётся corruption. Несовместимый seedVersion требует явной migration.

## DEC-032 — детерминированный переход корневого состояния

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Room PracticeState остаётся источником истины для верхнеуровневой навигации.

Если приложение отображает onboarding и Room меняется NotStarted → Started, UI обязан перейти на Home без polling, таймаута и повторного запуска Activity.

Навигация ожидает фактически созданный back stack entry, а не использует ограниченный retry по времени.

Started-состояние не принуждает переход на Home, если пользователь уже находится на Question, Archive или Settings.

## DEC-033 — изоляция production connected UI-тестов

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Connected UI-тесты production не используют пользовательскую praktika.db и не очищают production sandbox.

UI проверяется через изолированный test runtime и отдельную in-memory/test database, созданную до setContent.

Тесты, применимые только к production variant, размещаются в androidTestProductionDebug. Shared androidTest содержит только действительно общие и безопасные тесты.

## DEC-034 — согласованный read snapshot для UI

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

PracticeState и незавершённые QuestionOccurrence для UI читаются как единый транзакционно согласованный snapshot.

Независимый combine DAO Flow не используется для проверки межтабличных инвариантов, поскольку Room invalidation двух таблиц может дать промежуточную комбинацию старого и нового значения.

Corruption фиксируется только по данным, прочитанным внутри одной read transaction.

Порядок writes бизнес-команд не изменяется ради UI-наблюдения.

Production Compose использует lifecycle-aware StateFlow collection.

## DEC-035 — маршруты конкретного показа

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Question и Answer routes содержат `occurrenceId`:

- `question/{occurrenceId}`
- `answer/{occurrenceId}`

Home передаёт id конкретного AVAILABLE occurrence.

После process recreation route продолжает ссылаться на тот же `QuestionOccurrence`, а не автоматически подменяется новым текущим вопросом.

## DEC-036 — источник текста конкретного показа

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Единственный источник текста конкретного показа на экранах Question и Answer — `QuestionOccurrence.questionTextSnapshot`.

`QuestionEntity.text` не используется для отображения уже созданного occurrence.

## DEC-037 — occurrence-bound skip

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Пользовательский skip обязан содержать `expectedOccurrenceId`.

Совпадение id проверяется внутри той же `CycleRepository` transaction, в которой выполняются reconciliation, изменение статуса и создание следующего occurrence.

Предварительная проверка id только в UI или ViewModel недостаточна.

После успешного skip Question route удаляется из back stack, пользователь возвращается на Home.

## DEC-038 — отдельный QuestionViewModel

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

`QuestionViewModel` имеет lifecycle конкретного Question route.

`PracticeRootViewModel` продолжает обслуживать только onboarding и главный экран.

`QuestionViewModel` читает конкретный occurrence, контролирует skip и подготавливает границу для Этапа 8.

## DEC-039 — недоступный и устаревший occurrence

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Действия разрешены только для текущего AVAILABLE occurrence при неприостановленной практике.

SCHEDULED не раскрывает `questionTextSnapshot`.

Paused, completed, missing и non-current occurrence не позволяют ответ или skip и не считаются повреждением данных.

Пользователь получает безопасный возврат на Home.

## DEC-040 — атомарное сохранение ответа

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Сохранение ответа выполняется occurrence-bound командой `saveAnswer(expectedOccurrenceId, answerText)`.

Внутри одного `CycleRepository` mutex и одной Room transaction выполняются: reconciliation; проверка ожидаемого occurrence ID; проверка статуса AVAILABLE; проверка открытого временного окна; проверка отсутствия существующего Answer; вставка Answer; `AVAILABLE → ANSWERED`; `completedAt`; создание следующего occurrence; обновление PracticeState/cursor; проверка инвариантов.

Частично сохранённое состояние недопустимо.

## DEC-041 — семантика текста ответа

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Ответ не сохраняется, если `answerText.isBlank() == true`.

Допустимы одно слово, короткий и длинный многострочный текст, Unicode, кириллица, emoji, кавычки, знаки препинания и переносы строк.

Непустой текст хранится дословно: без `trim()`, без нормализации переносов, без произвольного лимита длины и без счётчика символов на Этапе 8.

## DEC-042 — черновик ответа

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Черновик хранится в `SavedStateHandle` route-scoped `AnswerViewModel`.

Гарантируется сохранение при recomposition, Activity recreation, системном process recreation через SavedStateRegistry и временном уходе приложения в background, пока back stack сохранён.

Не гарантируется сохранение после явного Back с удалением Answer route, force-stop, очистки данных приложения и перезапуска телефона без восстановления системного saved state.

Черновик не является `AnswerEntity` и не записывается в Room до явного нажатия «Сохранить ответ».

## DEC-043 — отдельный AnswerViewModel

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

Answer route имеет отдельный route-scoped `AnswerViewModel`.

`AnswerViewModel` отвечает за чтение конкретного occurrence, черновик, валидацию текста, save command, состояние сохранения, ошибки и одноразовое navigation event.

`QuestionViewModel` логикой ответа не расширяется.

## DEC-044 — подтверждение и back stack

**Дата:** 05.08.2026  
**Статус:** утверждено

### Решение

После успешного сохранения Answer и Question routes текущего occurrence удаляются; пользователь возвращается на Home; Home показывает подтверждение «Ответ сохранён»; Back не возвращает Answer или Question сохранённого occurrence; следующий occurrence берётся из Room.

## DEC-045 — область Этапа 10

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Этап 10 реализует:

- изменение времени трёх фиксированных ежедневных slots;
- настройку звука уведомлений;
- pause/resume практики;
- блок «О приложении».

Число slots остаётся равным трём. Отдельный timezone picker не добавляется. Settings доступен с Home после старта практики.

Команда `updateSchedule` должна также поддерживать состояние до старта, чтобы Этап 11 использовал ту же доменную реализацию без дублирования.

## DEC-046 — валидация расписания

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Расписание содержит ровно три записи с уникальными `slotIndex` 1, 2, 3.

Каждое `timeOfDayMinutes` находится в диапазоне 0..1439.

Все три времени должны различаться.

Минимальный интервал между slots не вводится.

UI использует точность до минуты. Секунды не сохраняются.

Repository не требует, чтобы вход был отсортирован. Для расчётов slots сортируются по `timeOfDayMinutes`.

Стабильная связь `slotIndex` сохраняется: время каждого существующего slot row обновляется, строки не удаляются и не пересоздаются.

## DEC-047 — атомарное обновление расписания

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

`updateSchedule` выполняется под существующим `CycleRepository` mutex и внутри одной Room transaction.

До изменения slots, если практика запущена и не paused, выполняется reconciliation по старому расписанию. Это не позволяет изменением расписания восстановить occurrence, который уже должен быть `MISSED_BY_TIME`.

После reconciliation три slot rows обновляются staged-способом, не нарушающим `UNIQUE(timeOfDayMinutes)` при перестановке значений.

`DELETE + INSERT` запрещены из-за FK RESTRICT.

После обновления slots корректируется текущий incomplete occurrence, затем проверяются инварианты.

При любой ошибке transaction полностью откатывается.

## DEC-048 — active SCHEDULED

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

После reconciliation, если текущий occurrence остаётся `SCHEDULED`, при сохранении нового расписания сохраняются: occurrence id, question id, `questionTextSnapshot`, cycle number, cycle position.

Пересчитываются: `scheduleSlotIndex`, `plannedAtEpochMillis`, `availableUntilEpochMillis`, `zoneId`, `status`.

Anchor — `now` из `TimeProvider` на момент transaction. Используется `findStartSlot(now)` с inclusive slot minute по DEC-008.

Если `now` находится в минуте нового slot, тот же occurrence сразу становится `AVAILABLE`.

Следующий occurrence не создаётся. Cycle cursor не продвигается.

## DEC-049 — active AVAILABLE вне pause

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Если после reconciliation текущий occurrence имеет `AVAILABLE`, при изменении расписания сохраняются: occurrence id, вопрос, snapshot, cycle position, status `AVAILABLE`, исторический `plannedAtEpochMillis`, исторический `scheduleSlotIndex`.

Изменяется только `availableUntilEpochMillis`. Новое `availableUntil` равно ближайшему slot нового расписания, строго следующему после `now`.

Slot в текущую минуту не используется как deadline текущего `AVAILABLE` occurrence.

Если occurrence был просрочен до начала `updateSchedule`, он завершается reconciliation по старому расписанию и не оживает.

## DEC-050 — изменение расписания во время pause

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Редактирование расписания во время pause разрешено.

**Paused SCHEDULED:** slot rows сохраняются сразу; frozen occurrence не меняется при Save; при resume тот же occurrence re-anchor через новое расписание по DEC-009.

**Paused AVAILABLE:** slot rows сохраняются сразу; текущий frozen `AVAILABLE` occurrence не пересчитывается; сохранённый remaining window имеет приоритет; при resume применяется существующая DEC-010; новое расписание применяется при создании следующего occurrence после завершения текущего.

Запрещено одновременно пересчитывать current `availableUntil` по новым slots и добавлять pause duration.

## DEC-051 — Settings draft и сохранение

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Settings имеет отдельный route-scoped `SettingsViewModel`.

Черновик трёх времён хранится в `SavedStateHandle`.

Расписание сохраняется только явной кнопкой «Сохранить расписание». Autosave slot changes не используется.

После успешного Save: экран Settings остаётся открыт; показывается Snackbar «Расписание сохранено»; persisted snapshot обновляется из Room; draft становится clean.

При Back с несохранённым draft показывается confirmation dialog.

Reset defaults на Этапе 10 не добавляется.

## DEC-052 — звук, pause/resume и About

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Sound enabled хранится отдельно от Room в Preferences DataStore. Default sound enabled = true.

Sound switch сохраняется сразу и не входит в Room transaction изменения расписания.

Pause/resume выполняются отдельными немедленными командами `CycleRepository`.

Блок «О приложении» является read-only.

На Этапе 10 не создаются notification scheduler, `ScheduleChangeNotifier`, schedule revision или alarm hooks. Настройка звука будет использована системными уведомлениями на Этапе 12.

## DEC-053 — пользовательский запуск практики

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Кнопка «Начать практику» на onboarding вызывает единый start-flow в `PracticeRootViewModel`.

Пока flow выполняется, повторное нажатие игнорируется, а кнопка disabled.

`CycleAlreadyStartedException` считается успешным идемпотентным результатом и не отображается пользователю.

Duplicate occurrence не создаётся.

## DEC-054 — root transition после старта

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Единственный source of truth для перехода Onboarding → Home — Room `PracticeState.isPracticeStarted`.

Command success сам по себе не вызывает navigation.

После Room emission Started выполняется переход на Home с:

- `popUpTo(ONBOARDING) { inclusive = true }`
- `launchSingleTop = true`

System Back не возвращает onboarding.

## DEC-055 — расписание onboarding

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Onboarding показывает три фиксированных slot times и позволяет изменить каждое время до старта практики.

Отдельной кнопки «Сохранить расписание» на onboarding нет.

При нажатии «Начать практику»:

1. draft валидируется;
2. если draft отличается от persisted schedule — вызывается `updateSchedule`;
3. после успешного update вызывается `startPractice`.

Если schedule не изменён, вызывается только `startPractice`.

Новый `configureAndStartPractice` repository command не создаётся.

## DEC-056 — partial start и повтор

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

`updateSchedule` и `startPractice` остаются отдельными атомарными Room transactions под существующим `CycleRepository` mutex.

Если schedule успешно сохранён, но `startPractice` не завершился, пользователь остаётся на onboarding.

Сохранённое расписание остаётся валидным persisted состоянием и используется при повторном запуске.

Это не считается повреждённым или частично неконсистентным состоянием.

## DEC-057 — onboarding draft

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Draft расписания onboarding хранится в `SavedStateHandle` единого `PracticeRootViewModel`.

Draft переживает recomposition, Activity recreation и поддержанное Android saved-state restoration.

Force-stop persistence для несохранённого draft не гарантируется.

Persisted schedule хранится только в Room.

Ключи onboarding draft не совпадают с Settings SavedState keys.

## DEC-058 — ошибки и граница уведомлений

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Schedule validation и обычные технические ошибки start/update — recoverable inline error на onboarding.

`CycleCorruptionException`, `SeedCorruptionException` и нарушения устойчивых Room-инвариантов переводят root UI в `FatalError`.

`CancellationException` не превращается в UI error.

Этап 11 не запрашивает notification permission, не создаёт channel и не планирует уведомления. Это scope Этапа 12.

## DEC-059 — source of truth и one-occurrence planning

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Room и `CycleRepository` остаются единственным календарём практики.

`AlarmManager` хранит только границы текущего incomplete occurrence через `NotificationPlanner`.

Для `SCHEDULED` планируются `PLANNED_BOUNDARY` и `EXPIRY_BOUNDARY`. Для `AVAILABLE` — `EXPIRY_BOUNDARY` и показ notification при необходимости.

После каждого platform callback выполняется authoritative reconcile и новый planning.

## DEC-060 — inexact delivery

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Используется `AlarmManager.setWindow` с окном 10 минут.

Exact alarm permissions и WorkManager не используются.

Статус occurrence определяется только Room; фактическая минута доставки notification на статус не влияет.

## DEC-061 — permission, channels и privacy

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Permission запрашивается только после явного нажатия на Home.

Отказ не блокирует практику. Повторный автоматический dialog запрещён.

Используются channels `practice_sound` и `practice_silent`.

Notification имеет `VISIBILITY_PRIVATE` и нейтральную public version на lock screen.

## DEC-062 — foreground, time, timezone и reboot

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Production foreground driver выполняет immediate reconcile и ждёт ближайшую boundary без polling.

`BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` и `MY_PACKAGE_REPLACED` вызывают initialization, reconcile и reschedule.

`activeZoneId` синхронизируется с system timezone; paused occurrence остаётся frozen byte-for-byte.

## DEC-063 — identity, stale guard и idempotency

**Дата:** 06.08.2026  
**Статус:** утверждено

### Решение

Alarm и notification identity включают occurrenceId, boundary type и planned timestamp.

Tap и alarm проходят reconcile и Room validation.

`openedAtEpochMillis` предотвращает повторный alert для уже открытого AVAILABLE occurrence.

Stale events silently ignore и не мутируют cycle.

## DEC-064 — Archive date/time display timezone

**Дата:** 07.08.2026  
**Статус:** утверждено

### Решение

Archive date/time derived from `answeredAtEpochMillis` in the current application ZoneId at display time.

Schema v2 не хранит historical ZoneId для каждого Answer; календарная дата и время в архиве вычисляются через текущий ZoneId приложения/устройства на момент отображения.
