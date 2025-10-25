# Рабочее место учителя

Android-приложение на базе Jetpack Compose для авторизации в проекте «Кубышка».

## Возможности
- Экран приветствия с предложением войти в систему.
- Ввод логина и пароля и отправка данных в API проекта.
- Отображение сообщений об ошибке и успешного входа.
- Сохранение `session_key` после успешной авторизации с помощью DataStore.

## API
Запросы выполняются по адресу `http://217.25.239.54:8004/API/` в формате JSON с заголовком `Content-Type: application/json`.

## Стек
- Kotlin
- Jetpack Compose (Material 3)
- Retrofit + Kotlin Serialization
- Android DataStore

## Сборка
Стандартный Android-проект Gradle. Для сборки используйте Android Studio или `./gradlew assembleDebug` при наличии Android SDK.
