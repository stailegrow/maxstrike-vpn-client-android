#!/usr/bin/env bash
# Собирает нативную библиотеку ядра (libXray.aar, обёртка над Xray-core
# через gomobile) и кладёт её в app/libs/.
#
# Это единственный шаг всей Android-сборки, который нельзя сделать
# удалённо через Claude: он тянет Android NDK и Go-модули напрямую из
# интернета, а песочнице агента такой доступ закрыт политикой безопасности
# (proxy.golang.org, dl.google.com и т.п. недоступны оттуда). Запускать —
# один раз в обычном Терминале на этом маке; дальше .aar просто лежит в
# libs/, и Android Studio подхватывает его как обычную зависимость.
#
# Зависимости: git, go (golang.org), python3 и Android NDK — его ставит
# сама Android Studio: Settings → Languages & Frameworks → Android SDK →
# вкладка SDK Tools → отметь "NDK (Side by side)" и "CMake" → Apply.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${ROOT}/app/libs"
CACHE="${HOME}/Library/Caches/maxstrike/libxray-src"

log() { echo "$1" >&2; }

for tool in git go python3; do
  command -v "$tool" >/dev/null 2>&1 || { log "Не найден '$tool' — установи его и запусти скрипт снова."; exit 1; }
done

# gomobile ищет NDK внутри Android SDK. Если ANDROID_HOME не задан в шелле
# (обычная ситуация для голого Терминала — его выставляет только сама
# Android Studio), берём путь из local.properties, который она сама создаёт
# при первом открытии проекта.
if [ -z "${ANDROID_HOME:-}" ]; then
  PROPS="${ROOT}/local.properties"
  if [ -f "$PROPS" ]; then
    SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$PROPS" | tail -1)"
    [ -n "$SDK_DIR" ] && export ANDROID_HOME="$SDK_DIR"
  fi
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "${ANDROID_HOME:-/nonexistent}" ]; then
  log "Не нашёл Android SDK (ANDROID_HOME). Открой проект в Android Studio хотя бы"
  log "один раз, чтобы она создала local.properties, и запусти скрипт снова."
  exit 1
fi
if [ -z "$(ls -d "${ANDROID_HOME}"/ndk/*/ 2>/dev/null)" ]; then
  log "Не нашёл Android NDK в ${ANDROID_HOME}/ndk."
  log "Поставь его: Android Studio → Settings → Languages & Frameworks → Android SDK →"
  log "вкладка SDK Tools → отметь \"NDK (Side by side)\" и \"CMake\" → Apply."
  exit 1
fi
log "Android SDK: ${ANDROID_HOME}"

mkdir -p "$CACHE" "$DEST"

if [ -d "${CACHE}/.git" ]; then
  log "Обновляю исходники libXray..."
  git -C "$CACHE" fetch --depth 1 origin main >&2
  git -C "$CACHE" reset --hard origin/main >&2
else
  log "Клонирую XTLS/libXray..."
  rm -rf "$CACHE"
  git clone --depth 1 https://github.com/XTLS/libXray.git "$CACHE" >&2
fi

log "Ставлю инструменты сборки (gomobile) и компилирую под Android — это"
log "тянет Go-модули и исходники Xray-core; первый раз может занять 10-20 минут."
( cd "$CACHE" && python3 build/main.py android )

if [ ! -f "${CACHE}/libXray.aar" ]; then
  log "Сборка отработала без ошибки, но libXray.aar не появился — что-то сломалось выше."
  exit 1
fi

cp "${CACHE}/libXray.aar" "${DEST}/libXray.aar"
[ -f "${CACHE}/libXray-sources.jar" ] && cp "${CACHE}/libXray-sources.jar" "${DEST}/libXray-sources.jar"

log ""
log "Готово: ${DEST}/libXray.aar"
log "Дальше: в Android Studio — File → Sync Project with Gradle Files, потом обычная сборка."
