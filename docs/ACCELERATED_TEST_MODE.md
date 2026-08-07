# Ускоренный тестовый режим (Accelerated APK)

## Назначение

Accelerated APK позволяет проверить полный цикл практики на физическом устройстве без ожидания семи реальных суток. Виртуальное время масштабируется через `TimeProvider`, логика цикла не изменяется.

## Пакеты и базы данных

| Вариант | Application ID | База данных |
|---------|----------------|-------------|
| Production | `com.me4hik.praktika` | `praktika.db` |
| Accelerated | `com.me4hik.praktika.accelerated` | `praktika_accelerated.db` |

Оба APK могут быть установлены одновременно. Production-база **никогда** не используется accelerated-приложением.

## Сборка

```powershell
cd <PROJECT_ROOT>\android
.\gradlew.bat assembleProductionDebug assembleAcceleratedDebug assembleProductionRelease
```

Доступные variants: `productionDebug`, `productionRelease`, `acceleratedDebug`.  
`acceleratedRelease` **отключён**.

APK:

- `app/build/outputs/apk/production/debug/`
- `app/build/outputs/apk/accelerated/debug/`
- `app/build/outputs/apk/production/release/`

## Установка

```powershell
$adb = "<ANDROID_SDK>\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\production\debug\app-production-debug.apk
& $adb install -r app\build\outputs\apk\accelerated\debug\app-accelerated-debug.apk
```

## Управление через ADB

```powershell
$adb = "<ANDROID_SDK>\platform-tools\adb.exe"
$package = "com.me4hik.praktika.accelerated"
$receiver = "com.me4hik.praktika.AcceleratedCommandReceiver"
$component = "$package/$receiver"
$action = "com.me4hik.praktika.accelerated.ACCELERATED_COMMAND"

# Диагностика
& $adb shell am broadcast -n $component -a $action --es command dump

# Скорость (60, 240 или 600)
& $adb shell am broadcast -n $component -a $action --es command set_speed --ei multiplier 240

# Виртуальные часы
& $adb shell am broadcast -n $component -a $action --es command pause_clock
& $adb shell am broadcast -n $component -a $action --es command resume_clock
& $adb shell am broadcast -n $component -a $action --es command advance_minutes --el minutes 240

# Ядро практики
& $adb shell am broadcast -n $component -a $action --es command start_practice
& $adb shell am broadcast -n $component -a $action --es command reconcile
& $adb shell am broadcast -n $component -a $action --es command pause_practice
& $adb shell am broadcast -n $component -a $action --es command resume_practice
& $adb shell am broadcast -n $component -a $action --es command skip_available
& $adb shell am broadcast -n $component -a $action --es command step_event
```

## Диагностика

Logcat tags: `AcceleratedClock`, `AcceleratedCommand`, `AcceleratedDiag`, `AcceleratedDriver`.

Команда `dump` выводит virtualNow, speed, pause state, PracticeState и текущий occurrence.

## Полный reset accelerated sandbox

```powershell
& $adb shell am force-stop com.me4hik.praktika.accelerated
& $adb shell pm clear com.me4hik.praktika.accelerated
& $adb shell am start -W -n com.me4hik.praktika.accelerated/com.me4hik.praktika.MainActivity
```

После reset: seed 21+3+1, virtual clock paused at 08:00, speed 240×.

**Запрещено:** `pm clear com.me4hik.praktika` для сброса теста.

## Process death

```powershell
& $adb shell am force-stop com.me4hik.praktika.accelerated
# повторный запуск Activity — virtual clock продолжается в том же boot
```

После перезагрузки телефона virtual clock автоматически на паузе до `resume_clock`.

## Безопасность

- Accelerated receiver **отсутствует** в production APK
- Production release не содержит accelerated-классов
- Reset не затрагивает production-данные
- Ускоренный режим не включается в обычном `productionDebug`
