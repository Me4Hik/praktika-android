# Praktika — RU source copy (translation handoff)

Locale-02 export. Russian user-facing copy only.

- Do **not** translate in this file during export.
- Copy `ru` cells **exactly** into EN/UK/PL workstreams.
- Keep placeholders (`%1$s`, `%d`, …) unchanged in translations.
- `gender_mode`: `—` if N/A; otherwise `MASCULINE` / `FEMININE` / `NEUTRAL`.
- Sources: `values/strings.xml`, `values/plurals.xml`, `assets/questions.json`,
  `QuestionDisplayTextResolver`, `MoodCopyResolver`, PDF export labels.

Columns: `key | ru | context | placeholders | gender_mode | notes`

## 1. Android string resources (`values/strings.xml`)

| key | ru | context | placeholders | gender_mode | notes |
| --- | --- | --- | --- | --- | --- |
| `app_name` | Практика | launcher / about brand name | — | — | launcher label; short brand name |
| `practice_starting` | Запуск… | home / practice chrome | — | — | — |
| `practice_error_start_failed` | Не удалось запустить практику | home / practice chrome | — | — | — |
| `practice_error_load_failed` | Не удалось загрузить состояние | home / practice chrome | — | — | — |
| `practice_error_corruption` | Данные практики повреждены | home / practice chrome | — | — | — |
| `practice_retry` | Повторить | home / practice chrome | — | — | — |
| `practice_started_title` | Практика началась | home / practice chrome | — | — | — |
| `practice_paused_title` | Практика на паузе | home / practice chrome | — | — | — |
| `practice_question_position` | Вопрос %1$d из 21 | home / practice chrome | %1$d | — | home status line |
| `practice_next_question` | Следующий вопрос: | home / practice chrome | — | — | — |
| `practice_available_until` | Доступен до: | home / practice chrome | — | — | — |
| `practice_planned_time` | Запланированное время: | home / practice chrome | — | — | — |
| `practice_saved_deadline` | Сохранённый дедлайн: | home / practice chrome | — | — | — |
| `practice_answer` | Ответить | home / practice chrome | — | — | — |
| `practice_first_launch_deferred` | Запуск практики будет подключён на этапе первого запуска. | home / practice chrome | — | — | — |
| `onboarding_description` | Несколько раз в день приложение будет задавать вам вопрос. Остановитесь на несколько секунд, почувствуйте свой ответ и сохраните его. Через время вопрос повторится, и вы сможете увидеть, как изменилось ваше восприятие. | first-launch onboarding (schedule + start) | — | — | long body; first-launch; wrap OK |
| `onboarding_schedule_title` | Расписание | first-launch onboarding (schedule + start) | — | — | — |
| `onboarding_start_practice` | Начать практику | first-launch onboarding (schedule + start) | — | — | — |
| `onboarding_restore_backup` | Восстановить из резервной копии | first-launch onboarding (schedule + start) | — | — | — |
| `practice_open_question` | Открыть вопрос | home / practice chrome | — | — | — |
| `practice_question_placeholder` | Экран вопроса будет подключён на следующем этапе. | home / practice chrome | — | — | — |
| `practice_back` | Назад | home / practice chrome | — | — | — |
| `practice_archive` | Архив ответов | home / practice chrome | — | — | — |
| `practice_settings` | Настройки | home / practice chrome | — | — | — |
| `question_skip` | Пропустить | question screen (defer/skip/blocked) | — | — | — |
| `question_defer` | Отложить | question screen (defer/skip/blocked) | — | — | — |
| `question_deferred_for_minutes` | Отложено на %d минут | question screen (defer/skip/blocked) | %d | — | toast/snackbar-like confirm; keep short |
| `answer_mood_add` | Указать настроение | answer editor / mood chip / snackbar | — | — | — |
| `answer_mood_selected` | %1$s %2$s | answer editor / mood chip / snackbar | %1$s, %2$s | — | mood chip: emoji + title; keep compact |
| `question_back_home` | На главный экран | question screen (defer/skip/blocked) | — | — | — |
| `question_blocked_not_found_title` | Вопрос не найден | question screen (defer/skip/blocked) | — | — | — |
| `question_blocked_not_found_message` | Этот показ вопроса больше недоступен. | question screen (defer/skip/blocked) | — | — | — |
| `question_blocked_not_available_title` | Вопрос ещё недоступен | question screen (defer/skip/blocked) | — | — | — |
| `question_blocked_not_available_message` | Вопрос станет доступен в запланированное время. | question screen (defer/skip/blocked) | — | — | — |
| `question_blocked_completed_title` | Вопрос уже завершён | question screen (defer/skip/blocked) | — | — | — |
| `question_blocked_completed_message` | На этот показ больше нельзя ответить или пропустить. | question screen (defer/skip/blocked) | — | — | — |
| `question_blocked_not_current_title` | Это не текущий вопрос | question screen (defer/skip/blocked) | — | — | — |
| `question_blocked_not_current_message` | Активен другой показ. Вернитесь на главный экран. | question screen (defer/skip/blocked) | — | — | — |
| `question_blocked_paused_message` | Действия недоступны, пока практика на паузе. | question screen (defer/skip/blocked) | — | — | — |
| `answer_placeholder_title` | Ваш ответ | answer editor / mood chip / snackbar | — | — | — |
| `answer_placeholder_deferred` | Ввод и сохранение ответа будут подключены на следующем этапе. | answer editor / mood chip / snackbar | — | — | — |
| `answer_title` | Ваш ответ | answer editor / mood chip / snackbar | — | — | — |
| `answer_input_label` | Ваш ответ | answer editor / mood chip / snackbar | — | — | — |
| `answer_input_placeholder` | Опишите, что вы замечаете… | answer editor / mood chip / snackbar | — | — | — |
| `answer_save` | Сохранить ответ | answer editor / mood chip / snackbar | — | — | — |
| `answer_saved_confirmation` | Ответ сохранён | answer editor / mood chip / snackbar | — | — | snackbar message; single-line preferred |
| `answer_view_history_action` | Посмотреть историю | answer editor / mood chip / snackbar | — | — | snackbar action; full label; space-sensitive |
| `answer_view_history_action_compact` | Смотреть | answer editor / mood chip / snackbar | — | — | snackbar compact action; keep short (UX length-critical) |
| `answer_error_blank` | Введите текст ответа. | answer editor / mood chip / snackbar | — | — | — |
| `answer_error_save_failed` | Не удалось сохранить ответ. Попробуйте ещё раз. | answer editor / mood chip / snackbar | — | — | — |
| `answer_error_occurrence_changed` | Этот показ уже завершён. Вернитесь на главный экран. | answer editor / mood chip / snackbar | — | — | — |
| `answer_blocked_not_found_title` | Показ не найден | answer editor / mood chip / snackbar | — | — | — |
| `answer_blocked_not_found_message` | Этот показ вопроса больше недоступен. | answer editor / mood chip / snackbar | — | — | — |
| `settings_title` | Настройки | settings screen and nested dialogs | — | — | — |
| `settings_schedule_section` | Время вопросов | settings screen and nested dialogs | — | — | — |
| `settings_slot_label` | Время %1$d | settings screen and nested dialogs | %1$d | — | — |
| `settings_slot_change` | Изменить | settings screen and nested dialogs | — | — | — |
| `settings_slot_content_description` | Изменить время %1$d, сейчас %2$s | settings screen and nested dialogs | %1$d, %2$s | — | — |
| `settings_save_schedule` | Сохранить расписание | settings screen and nested dialogs | — | — | — |
| `settings_schedule_duplicate_error` | Времена слотов должны различаться. | settings screen and nested dialogs | — | — | — |
| `settings_schedule_save_error` | Не удалось сохранить расписание. | settings screen and nested dialogs | — | — | — |
| `settings_schedule_validation_error` | Проверьте время слотов. | settings screen and nested dialogs | — | — | — |
| `settings_schedule_saved` | Расписание сохранено | settings screen and nested dialogs | — | — | — |
| `settings_sound_section` | Звук уведомлений | settings screen and nested dialogs | — | — | — |
| `settings_sound_label` | Звук включён | settings screen and nested dialogs | — | — | — |
| `settings_sound_error` | Не удалось изменить настройку звука. | settings screen and nested dialogs | — | — | — |
| `sound_library_entry_title` | Звук уведомления | sound library screen | — | — | — |
| `sound_library_title` | Звук уведомления | sound library screen | — | — | — |
| `sound_library_currently_selected` | Сейчас выбран: %1$s | sound library screen | %1$s | — | — |
| `sound_library_restore_hidden` | Восстановить скрытые | sound library screen | — | — | — |
| `sound_library_hide_action` | Убрать из библиотеки | sound library screen | — | — | — |
| `sound_library_notifications_muted_hint` | Звук в уведомлениях выключен — можно слушать и выбирать здесь. | sound library screen | — | — | — |
| `sound_library_preview_unavailable` | Не удалось воспроизвести звук. | sound library screen | — | — | — |
| `sound_library_error_generic` | Не удалось изменить настройку звука. | sound library screen | — | — | — |
| `sound_library_cd_play` | Слушать | sound library screen | — | — | — |
| `sound_library_cd_stop` | Остановить | sound library screen | — | — | — |
| `sound_library_cd_more` | Ещё | sound library screen | — | — | — |
| `sound_library_cd_selected` | Выбрано | sound library screen | — | — | — |
| `settings_defer_section` | Отложить вопрос | settings defer duration | — | — | — |
| `settings_defer_5` | 5 минут | settings defer duration | — | — | — |
| `settings_defer_10` | 10 минут | settings defer duration | — | — | — |
| `settings_defer_15` | 15 минут | settings defer duration | — | — | — |
| `settings_defer_30` | 30 минут | settings defer duration | — | — | — |
| `settings_defer_error` | Не удалось изменить время отложения. | settings defer duration | — | — | — |
| `settings_wording_section` | Формулировка вопросов | settings question wording mode (gender) | — | — | — |
| `settings_wording_masculine` | В мужском роде | settings question wording mode (gender) | — | — | — |
| `settings_wording_feminine` | В женском роде | settings question wording mode (gender) | — | — | — |
| `settings_wording_neutral` | Без указания рода | settings question wording mode (gender) | — | — | — |
| `settings_wording_error` | Не удалось изменить формулировку вопросов. | settings question wording mode (gender) | — | — | — |
| `settings_practice_section` | Практика | settings screen and nested dialogs | — | — | — |
| `settings_pause_practice` | Приостановить практику | settings screen and nested dialogs | — | — | — |
| `settings_resume_practice` | Продолжить практику | settings screen and nested dialogs | — | — | — |
| `settings_pause_error` | Не удалось изменить состояние практики. | settings screen and nested dialogs | — | — | — |
| `settings_practice_paused` | Практика приостановлена | settings screen and nested dialogs | — | — | — |
| `settings_practice_resumed` | Практика продолжена | settings screen and nested dialogs | — | — | — |
| `settings_about_section` | О приложении | settings screen and nested dialogs | — | — | — |
| `settings_about_version` | Версия %1$s | settings screen and nested dialogs | %1$s | — | — |
| `settings_about_build` | Сборка %1$d | settings screen and nested dialogs | %1$d | — | — |
| `settings_dirty_back_title` | Сохранить изменения? | settings screen and nested dialogs | — | — | — |
| `settings_dirty_back_message` | Расписание изменено, но не сохранено. | settings screen and nested dialogs | — | — | — |
| `settings_stay` | Остаться | settings screen and nested dialogs | — | — | — |
| `settings_discard` | Выйти без сохранения | settings screen and nested dialogs | — | — | — |
| `settings_time_picker_confirm` | Готово | settings screen and nested dialogs | — | — | — |
| `settings_time_picker_cancel` | Отмена | settings screen and nested dialogs | — | — | — |
| `settings_fatal_error` | Не удалось загрузить настройки. | settings screen and nested dialogs | — | — | — |
| `settings_bug_report` | Сообщить о проблеме | settings bug report dialog | — | — | — |
| `settings_bug_report_title` | Сообщить о проблеме | settings bug report dialog | — | — | — |
| `settings_bug_report_description` | К отчёту автоматически прикладываются технические события за последнее время. Тексты ваших ответов не отправляются. | settings bug report dialog | — | — | — |
| `settings_bug_report_comment_hint` | Кратко опишите проблему (необязательно) | settings bug report dialog | — | — | — |
| `settings_bug_report_send` | Отправить | settings bug report dialog | — | — | — |
| `settings_bug_report_cancel` | Отмена | settings bug report dialog | — | — | — |
| `settings_bug_report_close` | Закрыть | settings bug report dialog | — | — | — |
| `settings_bug_report_sent` | Отчёт отправлен. Спасибо! | settings bug report dialog | — | — | — |
| `settings_bug_report_saved_locally` | Отчёт сохранён локально, отправка недоступна. | settings bug report dialog | — | — | — |
| `settings_bug_report_send_failed` | Отчёт сохранён локально, но отправка не удалась. | settings bug report dialog | — | — | — |
| `settings_backup_section` | Резервная копия | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_status_not_configured` | Папка для резервных копий не выбрана. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_status_healthy` | Резервное копирование включено | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_status_configured_no_cache` | Резервное копирование настроено. Пока нет данных о последней успешной копии. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_status_needs_reconnect` | Нет доступа к папке резервных копий. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_status_transient` | Не удалось обновить резервную копию. Можно попробовать ещё раз. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_status_needs_attention` | Папка резервных копий требует проверки. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_status_data_problem` | Сейчас не удалось подготовить данные для резервной копии. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_status_unavailable` | Статус резервного копирования временно недоступен. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_last_success` | Последняя успешная копия: %1$s | settings backup / restore-copy dialogs & snackbars | %1$s | — | — |
| `settings_backup_setup` | Настроить резервную копию | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_now` | Создать копию сейчас | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_change_folder` | Изменить папку | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_reconnect` | Восстановить доступ | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_disable` | Отключить резервное копирование | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_use_folder` | Использовать для резервных копий | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_choose_another` | Выбрать другую папку | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_retry` | Повторить | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_continue` | Продолжить | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_disable_confirm_action` | Отключить | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_cancel` | Отмена | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_close` | Закрыть | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_disclosure_body` | Приложение получит доступ к выбранной папке и будет сохранять в ней резервные копии ответов и настроек практики обычными файлами — без дополнительного шифрования. Выберите надёжную папку, к которой у вас есть доступ. | settings backup / restore-copy dialogs & snackbars | — | — | long dialog body |
| `settings_backup_disclosure_title` | Резервная копия | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_confirm_equal` | В папке уже есть резервная копия Практики. Текущие данные будут сохранены как новая версия. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_confirm_different` | В папке уже есть другая резервная копия Практики. Настройка не восстанавливает её. Текущие данные станут новой копией; старые версии могут быть замещены. Восстановление выполняется из потока восстановления при старте практики. | settings backup / restore-copy dialogs & snackbars | — | — | long dialog body |
| `settings_backup_confirm_invalid_plus_missing` | В папке найден повреждённый файл резервной копии. Повреждённый файл останется без изменений. Текущие данные будут сохранены отдельно. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_confirm_blocked` | В этой папке нельзя безопасно настроить резервное копирование. Выберите другую папку. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_confirm_unsafe_database` | Сейчас не удалось подготовить данные для резервной копии. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_commit_retry_body` | Копия создана, но не удалось завершить настройку. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_disable_body` | Автоматическое резервное копирование будет отключено. Файлы копий в выбранной папке останутся. Приложение снимет свой доступ к папке, если это возможно. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_reconnect_different_body` | Вы выбрали другую папку. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_use_as_new_folder` | Использовать как новую папку | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_enabled` | Резервное копирование включено | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_folder_changed` | Папка для резервных копий изменена | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_created` | Резервная копия создана | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_already_current` | Резервная копия уже актуальна | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_not_configured` | Резервное копирование не настроено | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_needs_reconnect` | Нет доступа к папке резервных копий. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_transient` | Не удалось создать резервную копию. Попробуйте ещё раз. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_needs_attention` | Папка резервных копий требует проверки. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_data_problem` | Сейчас не удалось подготовить данные для резервной копии. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_tracking_written` | Резервная копия создана, но статус в приложении мог не обновиться. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_tracking_nochange` | Копия актуальна, но статус в приложении мог не обновиться. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_temporarily_unavailable` | Резервная копия сейчас недоступна. Попробуйте немного позже. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_reconnect_success` | Доступ к папке восстановлен | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_stale_configuration` | Настройки резервного копирования изменились. Повторите действие. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_permission_failure` | Не удалось получить доступ к папке резервных копий. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_validation_failure` | Не удалось проверить доступ к папке резервных копий. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_disabled` | Резервное копирование отключено | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_disabled_cleanup_warning` | Резервное копирование отключено, но не удалось полностью снять доступ к папке. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_disable_failed` | Не удалось отключить резервное копирование. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_disable_busy` | Не удалось завершить отключение. Попробуйте ещё раз. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_inspect_permission` | Нет доступа к выбранной папке. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_inspect_unavailable` | Не удалось открыть выбранную папку. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_inspect_provider` | Не удалось проверить выбранную папку. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_busy` | Сейчас выполняется другая операция. Попробуйте ещё раз. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_abandon_busy` | Не удалось завершить текущую настройку. Попробуйте ещё раз. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_write_failed` | Не удалось создать резервную копию. Попробуйте ещё раз. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_export_failed` | Сейчас не удалось подготовить данные для резервной копии. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_setup_failed` | Не удалось завершить настройку резервного копирования. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_replace_failed` | Не удалось сменить папку. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `settings_backup_snackbar_replace_failed_still_active` | Не удалось сменить папку. Текущее резервное копирование остаётся активным. | settings backup / restore-copy dialogs & snackbars | — | — | — |
| `home_notification_request_message` | Разрешите уведомления, чтобы получать вопросы по расписанию. | home permission / exact-alarm banners | — | — | — |
| `home_notification_request_action` | Разрешить уведомления | home permission / exact-alarm banners | — | — | — |
| `home_notification_disabled_message` | Уведомления выключены | home permission / exact-alarm banners | — | — | — |
| `home_notification_enable_action` | Включить | home permission / exact-alarm banners | — | — | — |
| `home_exact_alarm_message` | Чтобы напоминания приходили точно по расписанию, разрешите точные будильники и напоминания. | home permission / exact-alarm banners | — | — | — |
| `home_exact_alarm_action` | Разрешить точные напоминания | home permission / exact-alarm banners | — | — | — |
| `settings_notifications_entry` | Уведомления | settings screen and nested dialogs | — | — | — |
| `settings_notifications_title` | Уведомления | settings screen and nested dialogs | — | — | — |
| `settings_notification_settings` | Системные настройки уведомлений | settings screen and nested dialogs | — | — | — |
| `notification_channel_sound_name` | Вопросы со звуком | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_sound_description` | Уведомления о доступных вопросах со стандартным звуком. | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_silent_name` | Вопросы без звука | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_silent_description` | Тихие уведомления о доступных вопросах. | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_due_sound_name` | Вопросы (всплывающие) со звуком | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_due_sound_description` | Всплывающие уведомления о доступных вопросах со звуком. | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_due_silent_name` | Вопросы (всплывающие) без звука | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_due_silent_description` | Всплывающие уведомления о доступных вопросах без звука. | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_snoozed_name` | Отложенные вопросы | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_snoozed_description` | Тихий статус отложенного вопроса без всплывающего окна. | Android notification channel name/description (system Settings UI) | — | — | — |
| `notification_channel_due_custom_sound_name` | Вопросы (всплывающие): %1$s | Android notification channel name/description (system Settings UI) | %1$s | — | system channel name; length can truncate in OEM UI |
| `notification_channel_due_custom_sound_description` | Всплывающие уведомления со звуком «%1$s». | Android notification channel name/description (system Settings UI) | %1$s | — | — |
| `notif_sound_display_system` | Системный звук | notification sound display names | — | — | — |
| `notif_sound_display_numbered` | Звук %1$d | notification sound display names | %1$d | — | — |
| `notification_public_title` | Практика | notification channels / actions / lockscreen copy | — | — | lockscreen public title; short |
| `notification_public_body` | Доступен новый вопрос | notification channels / actions / lockscreen copy | — | — | lockscreen public body; short |
| `notification_snoozed_body` | Повтор в %1$s | notification channels / actions / lockscreen copy | %1$s | — | notification body; keep short |
| `notification_action_answer` | Ответить | notification channels / actions / lockscreen copy | — | — | notification action; short |
| `notification_action_answer_now` | Ответить сейчас | notification channels / actions / lockscreen copy | — | — | notification action; short |
| `notification_action_defer` | Отложить | notification channels / actions / lockscreen copy | — | — | notification action; short |
| `archive_title` | Архив | archive hub / history / export / share | — | — | — |
| `archive_empty` | Архив пока пуст | archive hub / history / export / share | — | — | — |
| `archive_day_empty` | За этот день ответов нет | archive hub / history / export / share | — | — | — |
| `archive_back` | Назад | archive hub / history / export / share | — | — | — |
| `archive_question_label` | Вопрос | archive hub / history / export / share | — | — | — |
| `archive_answer_label` | Ответ | archive hub / history / export / share | — | — | — |
| `archive_open_days` | Архив по дням | archive hub / history / export / share | — | — | — |
| `archive_by_days_title` | Архив по дням | archive hub / history / export / share | — | — | — |
| `archive_open_questions` | Архив по вопросам | archive hub / history / export / share | — | — | — |
| `archive_open_insights` | Закономерности | archive hub / history / export / share | — | — | — |
| `archive_questions_title` | Архив по вопросам | archive hub / history / export / share | — | — | — |
| `archive_question_history_title` | История ответов | archive hub / history / export / share | — | — | — |
| `archive_question_answer_count` | Ответов: %1$d | archive hub / history / export / share | %1$d | — | — |
| `archive_question_rejected_count` | Отклонений: %1$d | archive hub / history / export / share | %1$d | — | — |
| `archive_question_missed_count` | Пропусков: %1$d | archive hub / history / export / share | %1$d | — | — |
| `archive_cycle_label` | Цикл %1$d | archive hub / history / export / share | %1$d | — | — |
| `archive_rejected_label` | Отклонён | archive hub / history / export / share | — | — | — |
| `archive_missed_label` | Пропущен | archive hub / history / export / share | — | — | — |
| `archive_answer_deleted_body` | Ответ удалён | archive hub / history / export / share | — | — | — |
| `archive_delete_action` | Удалить | archive hub / history / export / share | — | — | — |
| `archive_delete_title` | Удалить ответ? | archive hub / history / export / share | — | — | — |
| `archive_delete_message` | Это действие нельзя отменить. | archive hub / history / export / share | — | — | — |
| `archive_delete_cancel` | Отмена | archive hub / history / export / share | — | — | — |
| `archive_delete_confirm` | Удалить | archive hub / history / export / share | — | — | — |
| `archive_delete_error` | Не удалось удалить ответ | archive hub / history / export / share | — | — | — |
| `archive_export_and_share` | Экспорт и поделиться | archive export / share dialogs | — | — | — |
| `archive_export_all` | Экспорт всего | archive export / share dialogs | — | — | — |
| `archive_export_period` | Экспорт периода | archive export / share dialogs | — | — | — |
| `archive_export_day` | Экспорт дня | archive export / share dialogs | — | — | — |
| `archive_export_history` | Экспорт истории | archive export / share dialogs | — | — | — |
| `archive_export_period_dialog_title` | Выберите период | archive export / share dialogs | — | — | — |
| `archive_export_period_confirm` | Экспортировать | archive export / share dialogs | — | — | — |
| `archive_export_period_cancel` | Отмена | archive export / share dialogs | — | — | — |
| `archive_export_period_start_date` | Начальная дата | archive export / share dialogs | — | — | — |
| `archive_export_period_end_date` | Конечная дата | archive export / share dialogs | — | — | — |
| `archive_export_period_date_unset` | Не выбрано | archive export / share dialogs | — | — | — |
| `archive_export_period_date_ok` | Готово | archive export / share dialogs | — | — | — |
| `archive_export_no_answers` | Нет ответов для экспорта | archive export / share dialogs | — | — | — |
| `archive_export_saved` | Файл сохранён | archive export / share dialogs | — | — | — |
| `archive_export_write_error` | Не удалось сохранить файл | archive export / share dialogs | — | — | — |
| `archive_export_format_dialog_title` | Формат файла | archive export / share dialogs | — | — | — |
| `archive_export_format_markdown` | Markdown | archive export / share dialogs | — | — | — |
| `archive_export_format_csv` | CSV | archive export / share dialogs | — | — | — |
| `archive_export_format_xlsx` | XLSX | archive export / share dialogs | — | — | — |
| `archive_export_format_pdf` | PDF | archive export / share dialogs | — | — | — |
| `archive_export_format_cancel` | Отмена | archive export / share dialogs | — | — | — |
| `archive_share_action` | Поделиться | archive export / share dialogs | — | — | — |
| `archive_share_all` | Поделиться всем | archive export / share dialogs | — | — | — |
| `archive_share_period` | Поделиться периодом | archive export / share dialogs | — | — | — |
| `archive_share_day` | Поделиться архивом | archive export / share dialogs | — | — | — |
| `archive_share_history` | Поделиться историей | archive export / share dialogs | — | — | — |
| `archive_share_period_dialog_title` | Выберите период | archive export / share dialogs | — | — | — |
| `archive_share_period_confirm` | Поделиться | archive export / share dialogs | — | — | — |
| `archive_share_format_dialog_title` | Формат файла | archive export / share dialogs | — | — | — |
| `archive_share_no_answers` | Нет ответов для отправки | archive export / share dialogs | — | — | — |
| `archive_share_prepare_error` | Не удалось подготовить файл | archive export / share dialogs | — | — | — |
| `archive_share_chooser_error` | Не удалось открыть меню отправки | archive export / share dialogs | — | — | — |
| `archive_share_text_chooser_title` | Поделиться | archive export / share dialogs | — | — | — |
| `archive_share_file_chooser_title` | Отправить файл | archive export / share dialogs | — | — | — |
| `archive_entry_share_dialog_title` | Поделиться | archive export / share dialogs | — | — | — |
| `archive_entry_share_question_only` | Только вопрос | archive export / share dialogs | — | — | — |
| `archive_entry_share_question_and_answer` | Вопрос и ответ | archive export / share dialogs | — | — | — |
| `archive_entry_share_cancel` | Отмена | archive export / share dialogs | — | — | — |
| `analytics_insights_title` | Закономерности | archive insights analytics | — | — | — |
| `analytics_section_weekdays` | По дням недели | archive insights analytics | — | — | — |
| `analytics_section_top_missed` | Чаще пропускаю | archive insights analytics | — | — | — |
| `analytics_section_top_deferred` | Чаще откладываю | archive insights analytics | — | — | — |
| `analytics_section_duration` | Обычно откладываю на… | archive insights analytics | — | — | — |
| `analytics_empty` | Пока нет данных для закономерностей | archive insights analytics | — | — | — |
| `analytics_low_sample_hint` | Пока данных немного. Ниже — то, что уже записано. | archive insights analytics | — | — | — |
| `analytics_weekday_monday` | Понедельник | archive insights analytics | — | — | — |
| `analytics_weekday_tuesday` | Вторник | archive insights analytics | — | — | — |
| `analytics_weekday_wednesday` | Среда | archive insights analytics | — | — | — |
| `analytics_weekday_thursday` | Четверг | archive insights analytics | — | — | — |
| `analytics_weekday_friday` | Пятница | archive insights analytics | — | — | — |
| `analytics_weekday_saturday` | Суббота | archive insights analytics | — | — | — |
| `analytics_weekday_sunday` | Воскресенье | archive insights analytics | — | — | — |
| `analytics_missed_of` | Пропущено %1$d из %2$d | archive insights analytics | %1$d, %2$d | — | — |
| `analytics_deferred_of` | Отложено %1$d из %2$d | archive insights analytics | %1$d, %2$d | — | — |
| `analytics_rejected_of` | Отклонено %1$d из %2$d | archive insights analytics | %1$d, %2$d | — | — |
| `analytics_question_deferred_of` | Откладывал %1$d из %2$d | archive insights analytics | %1$d, %2$d | — | — |
| `analytics_duration_mode` | Чаще всего — на %1$d мин | archive insights analytics | %1$d | — | — |
| `analytics_duration_low_sample` | Пока %1$d записей об отложении | archive insights analytics | %1$d | — | — |
| `analytics_missed_detail_weekday_title` | Пропуски · %1$s | archive insights analytics | %1$s | — | — |
| `analytics_missed_detail_question_title` | Пропуски вопроса | archive insights analytics | — | — | — |
| `analytics_missed_detail_empty` | Пропусков пока нет | archive insights analytics | — | — | — |
| `analytics_missed_detail_error` | Не удалось загрузить пропуски | archive insights analytics | — | — | — |
| `analytics_weekdays_expand_cd` | Развернуть дни недели | archive insights analytics | — | — | — |
| `analytics_weekdays_collapse_cd` | Свернуть дни недели | archive insights analytics | — | — | — |
| `restore_close_content_description` | Закрыть | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_idle_title` | Восстановление данных | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_idle_body` | Выберите папку, которую использовали для резервных копий Практики. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_idle_disclosure` | Приложение получит доступ к выбранной папке для восстановления и будущих резервных копий. Копия хранится в ней обычным файлом. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_choose_folder` | Выбрать папку | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_choose_other_folder` | Выбрать другую папку | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_choose_folder_again` | Выбрать папку снова | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_cancel` | Отмена | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_close` | Закрыть | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_checking_title` | Проверка… | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_choosing_folder_title` | Выбор папки… | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_connecting_title` | Подключение… | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_connecting_body` | Получаем доступ к папке. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_inspecting_title` | Поиск копии… | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_inspecting_body` | Ищем резервную копию в выбранной папке. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_blocked_title` | Восстановление недоступно | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_blocked_not_empty_body` | Восстановление доступно только до начала практики. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_blocked_unsafe_body` | Восстановление сейчас недоступно. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_no_backup_title` | Копия не найдена | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_no_backup_body` | В этой папке нет резервной копии Практики. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_invalid_title` | Копия повреждена | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_invalid_body` | Резервная копия повреждена или неполна. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_incompatible_title` | Копия несовместима | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_incompatible_body` | Эта резервная копия несовместима с текущей версией приложения. Проверьте, установлена ли последняя версия. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_permission_title` | Нет доступа | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_permission_body` | Нет доступа к выбранной папке. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_read_title` | Не удалось прочитать | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_read_body` | Не удалось прочитать резервную копию. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_active_folder_title` | Не удалось подключить папку | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_active_folder_body` | Не удалось сохранить доступ к выбранной папке. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_restore_write_title` | Не удалось восстановить данные | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_restore_write_body` | Не удалось сохранить восстановленные данные. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_unexpected_title` | Что-то пошло не так | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_error_unexpected_body` | Попробуйте выбрать папку ещё раз. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_preview_title` | Резервная копия от %1$s | restore-from-backup overlay (onboarding) | %1$s | — | — |
| `restore_preview_answers` | Сохранённых ответов: %1$d | restore-from-backup overlay (onboarding) | %1$d | — | — |
| `restore_preview_completed` | Завершённых показов: %1$d | restore-from-backup overlay (onboarding) | %1$d | — | — |
| `restore_preview_practice_started` | Практика была начата | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_preview_practice_not_started` | Практика ещё не была начата | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_preview_schedule` | Расписание: будет восстановлено | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_preview_deleted_text` | Ранее удалённые тексты ответов останутся удалёнными: %1$d | restore-from-backup overlay (onboarding) | %1$d | — | — |
| `restore_preview_folder_note` | Эта папка будет использоваться для будущих резервных копий. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_stale_notice` | Резервная копия изменилась. Проверьте данные ещё раз. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_confirm` | Восстановить | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_restoring_title` | Восстановление… | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_restoring_body` | Не закрывайте приложение. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_success_title` | Данные восстановлены | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_success_body` | Практика и расписание восстановлены. | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_continue` | Продолжить | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_runtime_warning_title` | Данные восстановлены | restore-from-backup overlay (onboarding) | — | — | — |
| `restore_runtime_warning_body` | Данные восстановлены, но часть обновления не завершилась. Если состояние отображается неверно, закройте и снова откройте приложение. | restore-from-backup overlay (onboarding) | — | — | — |
| `settings_tester_section` | Для тестирования | settings tester / interactive tour entry (user-visible when unlocked) | — | — | — |
| `settings_start_interactive_tour` | Интерактивное обучение (тест) | settings tester / interactive tour entry (user-visible when unlocked) | — | — | — |
| `settings_tour_result_completed` | Последнее обучение: завершено | settings tester / interactive tour entry (user-visible when unlocked) | — | — | — |
| `settings_tour_result_exited` | Последнее обучение: вышли на шаге %1$d — %2$s | settings tester / interactive tour entry (user-visible when unlocked) | %1$d, %2$s | — | — |
| `settings_tour_result_skipped` | · пропущено: %1$d | settings tester / interactive tour entry (user-visible when unlocked) | %1$d | — | — |
| `tour_progress` | %1$d из %2$d | interactive tour step/title (user-visible) | %1$d, %2$d | — | — |
| `tour_progress_overview` | Обзор · %1$d из %2$d | interactive tour step/title (user-visible) | %1$d, %2$d | — | — |
| `tour_skip` | Пропустить | interactive tour step/title (user-visible) | — | — | — |
| `tour_exit` | Выйти | interactive tour step/title (user-visible) | — | — | — |
| `tour_next` | Далее | interactive tour step/title (user-visible) | — | — | — |
| `tour_done` | Готово | interactive tour step/title (user-visible) | — | — | — |
| `tour_finish` | Закончить | interactive tour step/title (user-visible) | — | — | — |
| `tour_gate_continue` | Посмотреть остальное | interactive tour step/title (user-visible) | — | — | — |
| `tour_gate_finish` | Закончить | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_start_intro` | Здравствуйте. Здесь можно быстро познакомиться с основными действиями Практики. Если элемент визуально зовёт нажать — его можно нажать. Если какой-то шаг уже знаком, его можно пропустить. | interactive tour step/title (user-visible) | — | — | long tour body |
| `tour_task_archive` | Откройте Архив ответов. | interactive tour step/title (user-visible) | — | — | — |
| `tour_task_archive_days` | Теперь откройте Архив по дням. | interactive tour step/title (user-visible) | — | — | — |
| `tour_task_archive_days_info` | Здесь будут собраны ваши ответы по дням. Нажмите „Далее“. | interactive tour step/title (user-visible) | — | — | — |
| `tour_task_schedule` | Настроим время вопросов. | interactive tour step/title (user-visible) | — | — | — |
| `tour_task_schedule_change` | Нажмите „Изменить“ и выберите удобное время для вопросов. | interactive tour step/title (user-visible) | — | — | — |
| `tour_task_wording` | Выберите, в каком роде будут звучать вопросы: мужском, женском или без указания рода. | interactive tour step/title (user-visible) | — | — | — |
| `tour_task_sound_notifications` | Откройте „Уведомления“. | interactive tour step/title (user-visible) | — | — | — |
| `tour_task_sound_library` | Откройте „Звук уведомления“. | interactive tour step/title (user-visible) | — | — | — |
| `tour_task_sound` | Выберите любой звук уведомления. | interactive tour step/title (user-visible) | — | — | — |
| `tour_task_defer` | Выберите, на сколько обычно откладывать вопрос. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_phase_gate` | Основное готово. Вы уже попробовали главные действия. Дальше можно быстро посмотреть остальные возможности — или закончить здесь. | interactive tour step/title (user-visible) | — | — | — |
| `tour_overview_home` | На главном экране видно состояние Практики. Отсюда открываются Архив и Настройки. | interactive tour step/title (user-visible) | — | — | — |
| `tour_overview_archive` | В Архиве собраны ответы по дням, вопросы, закономерности и экспорт. | interactive tour step/title (user-visible) | — | — | — |
| `tour_overview_export_share` | Ответы можно сохранить как Markdown, CSV, PDF или Excel и поделиться ими. | interactive tour step/title (user-visible) | — | — | — |
| `tour_overview_pause_backup` | Практику можно поставить на паузу и снова возобновить. Чтобы не потерять ответы и прогресс, создайте резервную копию в выбранной папке. Из неё данные можно восстановить после переустановки или сбоя. | interactive tour step/title (user-visible) | — | — | long tour body |
| `tour_completion_farewell` | Готово. Вы познакомились с основными возможностями приложения. Удачной практики! | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_task_archive_days` | Архив по дням | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_task_schedule` | Расписание | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_task_wording` | Формулировки | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_task_sound` | Звук | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_task_defer` | Отложение | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_phase_gate` | Основное готово | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_overview_home` | Главный экран | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_overview_archive` | Архив | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_overview_export_share` | Экспорт и поделиться | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_overview_pause_backup` | Пауза и резервная копия | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_completion` | Готово | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_home_intro` | Это главный экран: здесь видно состояние практики и основные разделы. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_home_status` | Здесь видно текущее состояние Практики. Если вопрос доступен — появятся вопрос и кнопка «Ответить». Если ещё рано — время следующего вопроса. На паузе здесь будет видно состояние паузы. | interactive tour step/title (user-visible) | — | — | long tour body |
| `tour_step_click_archive` | Откройте Архив ответов. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_archive_hub` | Отсюда можно открыть ответы по дням, вопросы, аналитику и экспорт. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_click_archive_days` | Заглянем в Архив по дням. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_archive_days_content` | Здесь ответы собраны по датам. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_back_from_days` | Вернитесь в Архив. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_click_export_accordion` | Откройте блок «Экспорт и поделиться». | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_export_info` | Ответы можно сохранить как Markdown, CSV, PDF или Excel. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_share_info` | Здесь экспортом можно поделиться. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_back_from_archive` | Вернитесь на главный экран. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_click_settings` | Откройте Настройки. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_schedule_info` | Нажмите «Изменить» у одного из времён. При необходимости выберите другое время и подтвердите — оно сохранится. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_choose_wording` | Выберите, как будут звучать вопросы. Этот выбор сохранится. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_pause_info` | Здесь практику можно поставить на паузу и снова возобновить. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_backup_info` | Здесь можно создать резервную копию данных и восстановить их. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_click_notifications` | Откройте Уведомления. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_sound_switch_info` | Здесь можно полностью включить или выключить звук уведомлений. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_click_sound_library` | Откройте библиотеку звуков. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_preview_sound` | Прослушайте один из звуков. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_choose_sound` | Выберите звук. Этот выбор сохранится. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_hide_restore_info` | Ненужный звук можно скрыть, а потом вернуть через «Восстановить скрытые». | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_hide_restore_all_hidden` | Скрытые звуки можно вернуть здесь. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_back_from_sound` | Вернитесь в Уведомления. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_choose_defer` | Выберите, на сколько обычно откладывать вопрос. Настройка сохранится. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_system_notifications` | Здесь открываются системные настройки уведомлений Android. | interactive tour step/title (user-visible) | — | — | — |
| `tour_step_finished` | Готово. Теперь вы знаете, где находятся основные настройки и инструменты Praktika. | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_start_intro` | Вступление | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_home_overview` | Главный экран | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_home_status` | Статус практики | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_click_archive` | Архив | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_archive_hub` | Разделы архива | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_click_archive_days` | Архив по дням | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_archive_days_content` | Ответы по датам | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_back_from_days` | Назад в Архив | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_click_export_accordion` | Экспорт и поделиться | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_export_info` | Экспорт | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_share_info` | Поделиться | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_back_from_archive` | Назад на главный | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_click_settings` | Настройки | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_schedule_info` | Расписание | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_choose_wording` | Формулировки | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_pause_info` | Пауза | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_backup_info` | Резервная копия | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_click_notifications` | Уведомления | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_sound_switch_info` | Звук уведомлений | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_click_sound_library` | Библиотека звуков | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_preview_sound` | Прослушивание звука | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_choose_sound` | Выбор звука | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_hide_restore_info` | Скрыть и восстановить | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_back_from_sound` | Назад в Уведомления | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_choose_defer` | Отложение | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_system_notifications` | Системные уведомления | interactive tour step/title (user-visible) | — | — | — |
| `tour_title_finished` | Завершение | interactive tour step/title (user-visible) | — | — | — |

## 2. Plurals (`values/plurals.xml`)

| key | ru | context | placeholders | gender_mode | notes |
| --- | --- | --- | --- | --- | --- |
| `archive_occurrence_deferred_count.one` | Отложено %d раз | archive occurrence defer count line (quantity forms) | %d | — | Android plural quantity=`one`; RU uses one/few/many/other |
| `archive_occurrence_deferred_count.few` | Отложено %d раза | archive occurrence defer count line (quantity forms) | %d | — | Android plural quantity=`few`; RU uses one/few/many/other |
| `archive_occurrence_deferred_count.many` | Отложено %d раз | archive occurrence defer count line (quantity forms) | %d | — | Android plural quantity=`many`; RU uses one/few/many/other |
| `archive_occurrence_deferred_count.other` | Отложено %d раз | archive occurrence defer count line (quantity forms) | %d | — | Android plural quantity=`other`; RU uses one/few/many/other |

## 3. Practice questions (canonical masculine seed)

Source: `android/app/src/main/assets/questions.json` → Room `questions.text`.
Canonical seed is **MASCULINE**. Ids 2/5/8/11 also have FEMININE/NEUTRAL overlays in §4.

| key | ru | context | placeholders | gender_mode | notes |
| --- | --- | --- | --- | --- | --- |
| `question.1.masculine` | Как мои стопы ощущают контакт с землёй? | practice question id=1, cyclePosition=1; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.2.masculine` | Какой настоящий я сейчас по цвету? | practice question id=2, cyclePosition=2; shown in UI + notification body via snapshot | — | MASCULINE | canonical masculine; wording-dependent (see gender overlays) |
| `question.3.masculine` | Что я чувствую, когда представляю себя в центре Вселенной? | practice question id=3, cyclePosition=3; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.4.masculine` | Как моя спина чувствует поддержку? | practice question id=4, cyclePosition=4; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.5.masculine` | Какой настоящий я сейчас по запаху? | practice question id=5, cyclePosition=5; shown in UI + notification body via snapshot | — | MASCULINE | canonical masculine; wording-dependent (see gender overlays) |
| `question.6.masculine` | Как и где я воспринимаю свою внутреннюю тишину? | practice question id=6, cyclePosition=6; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.7.masculine` | Как мой живот реагирует на мои эмоции? | practice question id=7, cyclePosition=7; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.8.masculine` | Какой настоящий я сейчас по звуку? | practice question id=8, cyclePosition=8; shown in UI + notification body via snapshot | — | MASCULINE | canonical masculine; wording-dependent (see gender overlays) |
| `question.9.masculine` | Как я сегодня могу найти мир и покой внутри себя? | practice question id=9, cyclePosition=9; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.10.masculine` | Как я ощущаю связь с Природой? | practice question id=10, cyclePosition=10; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.11.masculine` | Какой настоящий я сейчас на ощупь? | practice question id=11, cyclePosition=11; shown in UI + notification body via snapshot | — | MASCULINE | canonical masculine; wording-dependent (see gender overlays) |
| `question.12.masculine` | Как сегодня я прощаю и отпускаю обиды? | practice question id=12, cyclePosition=12; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.13.masculine` | Как я реагирую на препятствия на моём пути? | practice question id=13, cyclePosition=13; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.14.masculine` | Как я позволяю себе расслабиться и отдохнуть? | practice question id=14, cyclePosition=14; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.15.masculine` | Как я могу отпустить контроль и довериться процессу жизни? | practice question id=15, cyclePosition=15; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.16.masculine` | Что я могу сделать сегодня, чтобы больше ценить каждый момент времени? | practice question id=16, cyclePosition=16; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.17.masculine` | Как я могу использовать свои мечты как источник вдохновения? | practice question id=17, cyclePosition=17; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.18.masculine` | Как сегодня выглядит для меня моя свобода? | practice question id=18, cyclePosition=18; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.19.masculine` | Как я могу выражать благодарность этому миру каждый день? | practice question id=19, cyclePosition=19; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.20.masculine` | Что я могу сделать сегодня, чтобы укрепить свою веру в себя? | practice question id=20, cyclePosition=20; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |
| `question.21.masculine` | Как я могу привлечь больше изобилия в свою жизнь? | practice question id=21, cyclePosition=21; shown in UI + notification body via snapshot | — | MASCULINE | canonical; same text for all wording modes today |

## 4. Question gender overlays (`QuestionDisplayTextResolver`)

Only question ids **2, 5, 8, 11**. MASCULINE = §3 canonical.

| key | ru | context | placeholders | gender_mode | notes |
| --- | --- | --- | --- | --- | --- |
| `question.2.feminine` | Какая я настоящая сейчас по цвету? | gender overlay for question id=2; replaces canonical when wording=FEMININE | — | FEMININE | must stay parallel to masculine/neutral sense variants |
| `question.5.feminine` | Какая я настоящая сейчас по запаху? | gender overlay for question id=5; replaces canonical when wording=FEMININE | — | FEMININE | must stay parallel to masculine/neutral sense variants |
| `question.8.feminine` | Какая я настоящая сейчас по звуку? | gender overlay for question id=8; replaces canonical when wording=FEMININE | — | FEMININE | must stay parallel to masculine/neutral sense variants |
| `question.11.feminine` | Какая я настоящая сейчас на ощупь? | gender overlay for question id=11; replaces canonical when wording=FEMININE | — | FEMININE | must stay parallel to masculine/neutral sense variants |
| `question.2.neutral` | Какой цвет сейчас лучше всего меня описывает? | gender overlay for question id=2; replaces canonical when wording=NEUTRAL | — | NEUTRAL | must stay parallel to masculine/feminine sense variants |
| `question.5.neutral` | Какой запах сейчас лучше всего меня описывает? | gender overlay for question id=5; replaces canonical when wording=NEUTRAL | — | NEUTRAL | must stay parallel to masculine/feminine sense variants |
| `question.8.neutral` | Какой звук сейчас лучше всего меня описывает? | gender overlay for question id=8; replaces canonical when wording=NEUTRAL | — | NEUTRAL | must stay parallel to masculine/feminine sense variants |
| `question.11.neutral` | Какое ощущение на ощупь сейчас лучше всего меня описывает? | gender overlay for question id=11; replaces canonical when wording=NEUTRAL | — | NEUTRAL | must stay parallel to masculine/feminine sense variants |

## 5. Mood copy (`MoodCopyResolver`)

Domain stores only `MoodLevel`. Emoji are stable UI chrome (not translated here).
Levels: VERY_LOW, LOW, NEUTRAL, GOOD, GREAT × modes FEMININE / MASCULINE / NEUTRAL.

| key | ru | context | placeholders | gender_mode | notes |
| --- | --- | --- | --- | --- | --- |
| `mood.VERY_LOW.FEMININE.title` | Тяжело | mood check-in picker title; level=VERY_LOW | — | FEMININE | paired with emoji 😣; keep short (chip/list) |
| `mood.VERY_LOW.FEMININE.explanation` | Потеряла опору | mood check-in picker explanation; level=VERY_LOW | — | FEMININE | secondary line under title; keep concise |
| `mood.LOW.FEMININE.title` | Хрупко | mood check-in picker title; level=LOW | — | FEMININE | paired with emoji 🙁; keep short (chip/list) |
| `mood.LOW.FEMININE.explanation` | Уязвима | mood check-in picker explanation; level=LOW | — | FEMININE | secondary line under title; keep concise |
| `mood.NEUTRAL.FEMININE.title` | Ровно | mood check-in picker title; level=NEUTRAL | — | FEMININE | paired with emoji 😐; keep short (chip/list) |
| `mood.NEUTRAL.FEMININE.explanation` | Прислушиваюсь к себе | mood check-in picker explanation; level=NEUTRAL | — | FEMININE | secondary line under title; keep concise |
| `mood.GOOD.FEMININE.title` | Светло | mood check-in picker title; level=GOOD | — | FEMININE | paired with emoji 🙂; keep short (chip/list) |
| `mood.GOOD.FEMININE.explanation` | В контакте с собой | mood check-in picker explanation; level=GOOD | — | FEMININE | secondary line under title; keep concise |
| `mood.GREAT.FEMININE.title` | Наполнена | mood check-in picker title; level=GREAT | — | FEMININE | paired with emoji 🤩; keep short (chip/list) |
| `mood.GREAT.FEMININE.explanation` | Расцветаю | mood check-in picker explanation; level=GREAT | — | FEMININE | secondary line under title; keep concise |
| `mood.VERY_LOW.MASCULINE.title` | Тяжело | mood check-in picker title; level=VERY_LOW | — | MASCULINE | paired with emoji 😣; keep short (chip/list) |
| `mood.VERY_LOW.MASCULINE.explanation` | Потерял опору | mood check-in picker explanation; level=VERY_LOW | — | MASCULINE | secondary line under title; keep concise |
| `mood.LOW.MASCULINE.title` | Хрупко | mood check-in picker title; level=LOW | — | MASCULINE | paired with emoji 🙁; keep short (chip/list) |
| `mood.LOW.MASCULINE.explanation` | Уязвим | mood check-in picker explanation; level=LOW | — | MASCULINE | secondary line under title; keep concise |
| `mood.NEUTRAL.MASCULINE.title` | Ровно | mood check-in picker title; level=NEUTRAL | — | MASCULINE | paired with emoji 😐; keep short (chip/list) |
| `mood.NEUTRAL.MASCULINE.explanation` | Прислушиваюсь к себе | mood check-in picker explanation; level=NEUTRAL | — | MASCULINE | secondary line under title; keep concise |
| `mood.GOOD.MASCULINE.title` | Светло | mood check-in picker title; level=GOOD | — | MASCULINE | paired with emoji 🙂; keep short (chip/list) |
| `mood.GOOD.MASCULINE.explanation` | В контакте с собой | mood check-in picker explanation; level=GOOD | — | MASCULINE | secondary line under title; keep concise |
| `mood.GREAT.MASCULINE.title` | Наполнен | mood check-in picker title; level=GREAT | — | MASCULINE | paired with emoji 🤩; keep short (chip/list) |
| `mood.GREAT.MASCULINE.explanation` | Полон сил и энергии | mood check-in picker explanation; level=GREAT | — | MASCULINE | secondary line under title; keep concise |
| `mood.VERY_LOW.NEUTRAL.title` | Тяжело | mood check-in picker title; level=VERY_LOW | — | NEUTRAL | paired with emoji 😣; keep short (chip/list) |
| `mood.VERY_LOW.NEUTRAL.explanation` | Теряю опору | mood check-in picker explanation; level=VERY_LOW | — | NEUTRAL | secondary line under title; keep concise |
| `mood.LOW.NEUTRAL.title` | Хрупко | mood check-in picker title; level=LOW | — | NEUTRAL | paired with emoji 🙁; keep short (chip/list) |
| `mood.LOW.NEUTRAL.explanation` | Чувствую уязвимость | mood check-in picker explanation; level=LOW | — | NEUTRAL | secondary line under title; keep concise |
| `mood.NEUTRAL.NEUTRAL.title` | Ровно | mood check-in picker title; level=NEUTRAL | — | NEUTRAL | paired with emoji 😐; keep short (chip/list) |
| `mood.NEUTRAL.NEUTRAL.explanation` | Прислушиваюсь к себе | mood check-in picker explanation; level=NEUTRAL | — | NEUTRAL | secondary line under title; keep concise |
| `mood.GOOD.NEUTRAL.title` | Светло | mood check-in picker title; level=GOOD | — | NEUTRAL | paired with emoji 🙂; keep short (chip/list) |
| `mood.GOOD.NEUTRAL.explanation` | В контакте с собой | mood check-in picker explanation; level=GOOD | — | NEUTRAL | secondary line under title; keep concise |
| `mood.GREAT.NEUTRAL.title` | Наполненность | mood check-in picker title; level=GREAT | — | NEUTRAL | paired with emoji 🤩; keep short (chip/list) |
| `mood.GREAT.NEUTRAL.explanation` | Ощущаю наполненность | mood check-in picker explanation; level=GREAT | — | NEUTRAL | secondary line under title; keep concise |

## 6. PDF / export hardcoded labels

Sources: `PdfArchiveFormatter`, `PdfArchiveSelectionLabel`.

| key | ru | context | placeholders | gender_mode | notes |
| --- | --- | --- | --- | --- | --- |
| `pdf.archive_title` | Практика — Архив | PDF document title (header) | — | — | PdfArchiveFormatter.TITLE |
| `pdf.label_question` | Вопрос | PDF field label before question text | — | — | hardcoded in PdfArchiveFormatter |
| `pdf.label_answer` | Ответ | PDF field label before answer text | — | — | hardcoded in PdfArchiveFormatter |
| `pdf.selection_all` | Все ответы | PDF subtitle when exporting all answers | — | — | PdfArchiveSelectionLabel ExportSelection.All |
| `pdf.selection_question_id_only` | Вопрос ${questionId} | PDF subtitle when exporting one question and text unavailable | ${questionId} | — | Kotlin template; translate words, keep id placeholder |
| `pdf.selection_question_with_text` | Вопрос ${questionId}: $questionText | PDF subtitle when exporting one question with text | ${questionId}, $questionText | — | Kotlin template; questionText already localized snapshot |

## 7. Other real hardcoded / flavor user-facing copy

| key | ru | context | placeholders | gender_mode | notes |
| --- | --- | --- | --- | --- | --- |
| `app_name.accelerated` | Практика (TEST) | accelerated/TEST flavor launcher name | — | — | android/app/src/accelerated/res/values/strings.xml override of app_name; tester-visible |

## 8. Excluded (dead / preview / dev-only)

| excluded | reason |
| --- | --- |
| `ArchiveScreen.kt` hardcoded RU stubs | dead UI; nav uses ArchiveHubScreen |
| `QuestionHistoryScreen.kt` hardcoded RU stubs | dead UI; not in navigation |
| `QuestionInteractivePreviews.kt` sample questions | Compose previews only |
| `PracticeSnackbarHostPreviews.kt` sample snackbar copy | Compose previews only |
| `PracticeSnackbarHost.kt` KDoc examples | developer comments only |
| unit/instrumented test assertions | not user-visible |
| logs / DiagnosticsRecorder / Sentry payloads | developer diagnostics |
| exception messages in Kotlin domain layer | not shown as product copy |

## 9. Export totals

- `TOTAL_ROWS` = 513
- `STRINGS_ROWS` = 443
- `PLURAL_ROWS` = 4
- `QUESTION_ROWS` = 21
- `GENDER_ROWS` = 8
- `MOOD_ROWS` = 30
- `PDF_ROWS` = 6
- `OTHER_HARDCODED_ROWS` = 1
- `EXCLUDED_DEAD_OR_DEV_COPY` = 8 categories listed in §8
- `UNRESOLVED_COPY` = none (all Locale-01 user-facing buckets covered)
- `OUTPUT_PATH` = `docs/localization/RU_SOURCE_COPY.md`

`LOCALE_02_RU_COPY_EXPORT = DONE`

