# Дизайн-спецификация: Core Player Android

**Дата**: 2026-10-08  
**Статус**: Согласовано  
**Платформа**: Android (Native Kotlin, Jetpack Compose, Media3/ExoPlayer)  
**Репозиторий бэкенда**: https://github.com/Skenls/core-player  

---

## 1. Цели и назначение проекта

Разработка нативного Android-клиента для музыкального плеера **Core Player**, обладающего полным функционалом веб-версии и современным адаптивным интерфейсом, в точности повторяющим макет из `helpers/`:
* Глубокая темная тема с эффектами размытия (Glassmorphism / Apple Music style).
* Надежное фоновое воспроизведение через Android Foreground Service (`MediaSessionService`) без остановки воспроизведения при выключении экрана или переходе в другие приложения.
* Полная синхронизация с FastAPI-бэкендом Core Player (авторизация по сессионным cookie, стриминг с HTTP Range 206, загрузка обложек, плейлисты, избранное).
* Синхронизированный караоке-текст песен (построчный автоскролл таймкодов LRC).
* Нижний плавающий мини-плеер в виде капсулы с быстрым управлением и раскрытием в полноэкранный Now Playing.

---

## 2. Инструменты разработки и сборки

* **Язык**: Kotlin 2.0+
* **Минимальная версия Android**: Android 8.0 (API Level 26)
* **Целевая версия Android**: Android 14 / 15 (API Level 34–35)
* **Java/JDK**: OpenJDK 17 или 21
* **Инструменты командной строки**:
  * ADB: `/Users/skenls/platform-tools/adb`
  * Сборщик: Gradle Wrapper (`./gradlew`)
* **Команды сборки и запуска**:
  * Сборка отладочного APK: `./gradlew assembleDebug`
  * Установка на смартфон: `/Users/skenls/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`

---

## 3. Архитектура приложения

Приложение строится по архитектурному паттерну **Clean MVVM / MVI** в рамках единого модуля `app`:

```
app/src/main/java/com/coreplayer/app/
├── CorePlayerApp.kt                 # Application класс (инициализация Coil, CookieJar)
├── MainActivity.kt                  # Главная активность, хост для Jetpack Compose
│
├── data/                            # Слой данных
│   ├── api/
│   │   ├── CorePlayerApi.kt         # Retrofit интерфейс к FastAPI
│   │   └── NetworkModule.kt         # Настройка OkHttpClient + PersistentCookieJar
│   ├── model/                       # DTO и доменные модели (User, Track, Album, Artist, Playlist, Lyrics)
│   ├── repository/
│   │   ├── AuthRepository.kt        # Авторизация и хранение сессии
│   │   ├── LibraryRepository.kt     # Загрузка артистов, альбомов, плейлистов, избранного
│   │   ├── LyricsRepository.kt      # Запрос и парсинг LRC лирики
│   │   └── SettingsRepository.kt    # DataStore Preferences (URL сервера, битрейт)
│
├── player/                          # Фоновая аудиослужба
│   ├── PlaybackService.kt           # MediaSessionService (Media3)
│   ├── PlayerControllerManager.kt   # Обвязка над MediaController для Compose UI
│   └── AudioFocusHelper.kt          # Обработка звонков и звуковых фокусов
│
├── ui/                              # Представление (Jetpack Compose)
│   ├── theme/
│   │   ├── Color.kt                 # Темная палитра (#0B0F17, #131A26, акцент #FF5722)
│   │   ├── Theme.kt                 # CorePlayerTheme
│   │   └── Type.kt                  # Типографика (жирный логотип core, заголовки)
│   ├── components/
│   │   ├── FloatingMiniPlayer.kt    # Плавающая капсула плеера с оранжевой кнопкой
│   │   ├── BottomNavBar.kt          # Нижняя панель (Home, Search, Library)
│   │   ├── ArtistCard.kt            # Квадратная карточка артиста со скруглениями
│   │   ├── PlaylistCard.kt          # Карточка плейлиста с полупрозрачным Play
│   │   └── FavoritesBanner.kt       # Широкий пейзажный баннер «Мои треки»
│   ├── screens/
│   │   ├── home/HomeScreen.kt       # Экран в точности по макету helpers
│   │   ├── nowplaying/
│   │   │   ├── NowPlayingBottomSheet.kt # Полноэкранный плеер
│   │   │   └── SyncedLyricsView.kt  # Караоке-текст песни с автоскроллом
│   │   ├── search/SearchScreen.kt   # Поиск треков/альбомов/артистов
│   │   ├── library/LibraryScreen.kt # Альбомы, плейлисты, избранное
│   │   └── login/LoginScreen.kt     # Экран ввода URL сервера и логина
│   └── navigation/
│       └── AppNavigation.kt         # Навигационный граф (Compose Navigation)
```

---

## 4. Контракт взаимодействия с Core Player Backend

Приложение общается с сервером Core Player (FastAPI) по REST API:

| Метод | Эндпоинт | Назначение |
|---|---|---|
| `POST` | `/api/auth/login` | Авторизация (логин + пароль), установка session cookie |
| `GET` | `/api/auth/me` | Проверка валидности текущей сессии |
| `POST` | `/api/auth/logout` | Сброс сессии |
| `GET` | `/api/stream/{track_id}?format=mp3` | Стриминг аудиодорожки с поддержкой Range 206 |
| `GET` | `/api/stream/cover/{cover_id}?size=300` | Получение обложки для Coil |
| `GET` | `/api/library/artists` | Список артистов (для карусели «Недавние артисты») |
| `GET` | `/api/library/albums` | Список альбомов |
| `GET` | `/api/library/playlists` | Список плейлистов пользователя |
| `GET` | `/api/library/favorites/ids` | Список ID избранных треков |
| `POST` | `/api/library/favorites/toggle` | Добавить/удалить трек из избранного |
| `GET` | `/api/library/search?q={query}` | Глобальный поиск |
| `GET` | `/api/lyrics/{track_id}` | Получение синхронизированного текста песен (LRC) |

---

## 5. Аудиоподсистема (Media3 + ExoPlayer)

1. **Жизненный цикл**:
   * `PlaybackService` наследуется от `androidx.media3.session.MediaSessionService`.
   * При старте трека служба переводится в Foreground режим (`startForeground`) со шторкой `MediaNotification`.
   * Система Android автоматически отрисовывает плеер в системной панели уведомлений и на экране блокировки с обложкой, кнопками Play/Pause/Prev/Next.
2. **Формат и качество**:
   * Потоковый стриминг через `DefaultMediaSourceFactory` с поддержкой HTTP Range.
   * Передача Cookie в заголовках OkHttp DataSource.
   * Воспроизведение треков напрямую по сети без сохранения на диск (по выбору пользователя).
3. **Лирика**:
   * Регулярное выражение парсинга LRC: `^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$`.
   * Преобразование в массив элементов `LyricLine(timestampMs: Long, text: String)`.
   * В UI текущая строка вычисляется по позиции плеера и плавно подсвечивается и скроллится к центру экрана.

---

## 6. UI-дизайн и соответствие макету `helpers/`

1. **Экран Home**:
   * **Шапка**: Крупный логотип **`core`** белым цветом вверху слева.
   * **Секция «Недавние артисты»**: горизонтальный ряд скругленных карточек (фото исполнителя, под ним имя и надпись «Артист»).
   * **Секция «Избранное >»**: большой пейзажный горизонтальный баннер «Мои треки» с подзаголовком (количество треков) и иконкой лайка (сердечко).
   * **Секция «Плейлисты >»**: горизонтальный список карточек плейлистов с обложками, полупрозрачной кнопкой воспроизведения в левом нижнем углу обложки, названиями («Вечерний вайб», «Chill vibes», «Инди атмосфера») и количеством треков.
2. **Плавающий мини-плеер**:
   * Парящая капсула (Pill card) с размытым темным фоном над нижней панелью навигации.
   * Слева: круглая оранжевая кнопка Play/Pause.
   * По центру: название трека и исполнитель.
   * Справа: кнопка «Избранное» (сердце).
3. **Нижний бар навигации**:
   * 3 вкладки: **Home**, **Search**, **Library** со стильными монохромными иконками.
4. **Полноэкранный плеер (Now Playing Bottom Sheet)**:
   * Открывается по тапу на мини-плеер.
   * Крупная обложка альбома с динамическим размытием на фоне.
   * Прогресс-бар с перемоткой.
   * Кнопки Shuffle, Previous, Play/Pause, Next, Repeat.
   * Вкладка текста песни (караоке-режим с автоскроллом).

---

## 7. План верификации

1. **Сборка проекта**:
   * Успешная компиляция `./gradlew assembleDebug`.
2. **Проверка подключения**:
   * Прохождение авторизации по логину/паролю и сохранение сессии в CookieJar.
3. **Проверка аудио**:
   * Стриминг трека из бэкенда Core Player, корректная работа шторки уведомлений и продолжение воспроизведения при блокировке экрана.
4. **Проверка UI**:
   * Визуальное сопоставление экрана Home с макетом `helpers/ChatGPT Image 8 окт. 2026 г., 11_34_30.png`.
   * Работа мини-плеера и синхронизированного караоке-текста.
