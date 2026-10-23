# Murglar Pass Bypass

LSPosed-модуль (libxposed API 102) для `com.badmanners.murglar2` (проверено на Murglar 6.9.325). Разблокирует весь гейт подписки: Murglar Pass, Premium, платные темы (включая AMOLED) и Pass-источники.

Как это работает: подписка приходит с сервера (`POST /api/user/me`, подпись RSA), поэтому модуль хукает геттеры ниже проверки подписи — `User.hasActiveSubscription/hasNoAdsPremium/getSubscriptionActiveTill`, `SignedUser.isPassLimitExceeded` и `Signature#verify` (единственное её использование в приложении — проверка профиля).

## Установка

1. Установи APK, включи модуль в LSPosed Manager со скопом `com.badmanners.murglar2`.
2. Войди в Murglar под бесплатным аккаунтом (без входа гейты закрыты).

## Сборка

```sh
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

CI (`.github/workflows/build.yml`, `ubuntu-latest`) собирает каждый push/PR, готовый APK лежит в артефактах.
