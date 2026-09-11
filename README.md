# Max Strike (Android)

Android-клиент VPN на движке [Xray-core](https://github.com/XTLS/Xray-core)
(VLESS, транспорт XHTTP) — младший брат [macOS-версии Max Strike](https://github.com/stailegrow/maxstrike-vpn-client-macos).

**Статус: самое начало.** Сейчас в репозитории только пустой каркас проекта —
экран запускается, ядро ещё не подключено. План работы и архитектура — в
отдельном документе вне этого репозитория (как и на macOS-версии, план не
входит в публичный код).

## Открыть проект

Нужен Android Studio (текущая стабильная версия) на маке или любой другой
машине. Открыть эту папку как проект — `File → Open`.

Если при первом синке Android Studio пожалуется на отсутствующий
`gradle-wrapper.jar` — это ожидаемо: бинарник обёртки не коммитился (его
негде было скачать при генерации каркаса). Android Studio предложит создать
его сама через `Gradle → Regenerate Wrapper` или синк с её встроенным Gradle —
соглашайся.

## Стек

- Kotlin + Jetpack Compose, Material 3
- Xray-core через gomobile-обёртку (`libXray`, MIT, план Б — `AndroidLibXrayLite`, LGPL-3.0)
- `android.net.VpnService` для туннелирования — без root, без привилегированного хелпера
