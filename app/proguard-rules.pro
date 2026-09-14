# R8/минификация включена в release-сборке (см. app/build.gradle.kts,
# isMinifyEnabled = true) — компилятор был отложен намеренно до этого
# захода ("включим ближе к публикации"), теперь самое время: без него
# release-код вообще не оптимизируется, а это одна из главных причин
# тормозов скролла в релизной сборке (developer.android.com/develop/ui
# /compose/performance — "Missing R8 Compiler Optimization").
#
# Нативный мост к Xray-core (app/libs/libXray.aar, генерируется gomobile)
# несёт собственные consumer-правила (-keep class go.** / libXray.** —
# проверено распаковкой .aar), они подключаются автоматически, отдельно
# прописывать их здесь не нужно.
#
# MaxStrikeVpnService реализует libXray.DialerController напрямую (вызовы
# protectFd/... приходят из нативного кода через интерфейс) — как
# объявленный в манифесте Service он и так под стандартным keep-правилом
# AGP для компонентов, но держим явное правило, чтобы это не зависело от
# версии AGP/дефолтных правил.
-keep class com.stailegrow.maxstrike.vpn.MaxStrikeVpnService { *; }
