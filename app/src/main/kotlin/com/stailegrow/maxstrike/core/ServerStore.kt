package com.stailegrow.maxstrike.core

import android.content.Context
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.model.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ServerMergeResult(
    val servers: List<ProxyConfig>,
    val added: Int,
    val removed: Int,
    val kept: Int,
)

/**
 * Серверы и подписки на диске — Android-аналог Core/ServerStore.swift.
 *
 * Хранение устроено проще, чем на маке: вместо полной посерверной
 * сериализации каждый сервер хранится как его исходная ссылка
 * (`sourceLink`, её всегда сохраняет LinkParser) + id + subscriptionID, а
 * при загрузке просто разбирается заново тем же LinkParser — тем самым
 * гарантированно тем же кодом, что и живой парсинг, без риска, что ручная
 * JSON-схема разойдётся с полями ProxyConfig.
 *
 * Экран, как и на маке, знает только про StateFlow'ы ниже — про файл на
 * диске он не в курсе.
 */
object ServerStore {

    private const val FILE_NAME = "servers.json"
    private const val HISTORY_LENGTH = 24

    sealed class QuickAddResult {
        data class SubscriptionAdded(val name: String, val servers: Int) : QuickAddResult()
        data class ServersAdded(val count: Int) : QuickAddResult()
        data class Failure(val text: String) : QuickAddResult()

        val message: String
            get() = when (this) {
                is SubscriptionAdded -> L.t("Подписка «$name» — узлов: $servers", "Subscription “$name” — $servers nodes")
                is ServersAdded -> if (count == 0) L.t("Новых узлов нет — всё это уже добавлено.", "No new nodes — all of this is already added.") else L.t("Добавлено узлов: $count", "Nodes added: $count")
                is Failure -> text
            }
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var storageFile: File? = null
    private var autoRefreshJob: Job? = null

    private val _servers = MutableStateFlow<List<ProxyConfig>>(emptyList())
    val servers: StateFlow<List<ProxyConfig>> = _servers.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _selectedID = MutableStateFlow<String?>(null)
    val selectedID: StateFlow<String?> = _selectedID.asStateFlow()

    private val _refreshingIDs = MutableStateFlow<Set<String>>(emptySet())
    val refreshingIDs: StateFlow<Set<String>> = _refreshingIDs.asStateFlow()

    private val _pings = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val pings: StateFlow<Map<String, Int?>> = _pings.asStateFlow()

    private val _pingHistory = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val pingHistory: StateFlow<Map<String, List<Int>>> = _pingHistory.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    /** Вызывать один раз при старте приложения, до первого обращения к стору. */
    fun init(context: Context) {
        if (storageFile != null) return
        storageFile = File(context.filesDir, FILE_NAME)
        load()
    }

    val manualServers: List<ProxyConfig>
        get() = _servers.value.filter { it.subscriptionID == null }

    fun serversIn(subscription: Subscription?): List<ProxyConfig> =
        _servers.value.filter { it.subscriptionID == subscription?.id }

    fun subscriptionFor(server: ProxyConfig): Subscription? =
        server.subscriptionID?.let { id -> _subscriptions.value.firstOrNull { it.id == id } }

    fun select(id: String?) {
        _selectedID.value = id
        persist()
    }

    // MARK: - Быстрое добавление

    /**
     * Разбирает произвольный текст и делает то, что он означает: ссылка на
     * подписку — добавляет подписку, ссылки узлов — добавляет узлы. Гадать,
     * куда вставлять, человеку не нужно.
     */
    suspend fun quickAdd(raw: String): QuickAddResult {
        val value = raw.trim()
        if (value.isEmpty()) return QuickAddResult.Failure(L.t("Пусто — нечего добавлять.", "Empty — nothing to add."))

        val lowered = value.lowercase()
        if (lowered.startsWith("http://") || lowered.startsWith("https://")) {
            val subscription = addSubscription(url = value)
            val ok = refresh(subscription)
            val stored = _subscriptions.value.firstOrNull { it.id == subscription.id }
            if (!ok || stored == null) {
                val message = stored?.lastError ?: L.t("Не удалось загрузить подписку.", "Could not load the subscription.")
                removeSubscription(subscription.id)
                return QuickAddResult.Failure(message)
            }
            return QuickAddResult.SubscriptionAdded(stored.displayName, serversIn(stored).size)
        }

        if (value.contains("://")) {
            val parsed = LinkParser.parseMany(value)
            if (parsed.configs.isEmpty()) {
                return QuickAddResult.Failure(parsed.errors.firstOrNull() ?: L.t("Ссылки не распознались.", "The links were not recognised."))
            }
            return QuickAddResult.ServersAdded(add(parsed.configs))
        }

        return QuickAddResult.Failure(L.t("Это не похоже ни на ссылку подписки, ни на ссылку узла.", "This looks like neither a subscription link nor a node link."))
    }

    // MARK: - Ручное добавление

    fun add(incoming: List<ProxyConfig>): Int {
        val existingKeys = _servers.value.map { it.identityKey }.toMutableSet()
        val next = _servers.value.toMutableList()
        var added = 0
        for (config in incoming) {
            if (existingKeys.add(config.identityKey)) {
                next.add(config)
                added++
            }
        }
        if (added > 0) {
            _servers.value = next
            if (_selectedID.value == null) _selectedID.value = next.firstOrNull()?.id
            persist()
        }
        return added
    }

    fun remove(ids: Set<String>) {
        _servers.value = _servers.value.filterNot { ids.contains(it.id) }
        if (_selectedID.value != null && ids.contains(_selectedID.value)) {
            _selectedID.value = _servers.value.firstOrNull()?.id
        }
        persist()
    }

    // MARK: - Подписки

    fun addSubscription(url: String, name: String = ""): Subscription {
        val subscription = Subscription(url = url.trim(), name = name.trim())
        _subscriptions.value = _subscriptions.value + subscription
        persist()
        return subscription
    }

    /** Переименование — Android-аналог updateSubscription() на маке: имя
     *  видно только пользователю, пустое поле возвращает то, что прислала
     *  панель (displayName сам подставит хост из url). */
    fun renameSubscription(id: String, name: String) {
        _subscriptions.value = _subscriptions.value.map {
            if (it.id == id) it.copy(name = name.trim()) else it
        }
        persist()
    }

    /** Удаляет подписку вместе с её серверами — иначе в списке остаются
     *  висяки, которые больше некому обновлять. */
    fun removeSubscription(id: String) {
        _subscriptions.value = _subscriptions.value.filterNot { it.id == id }
        val orphans = _servers.value.filter { it.subscriptionID == id }.map { it.id }.toSet()
        _servers.value = _servers.value.filterNot { orphans.contains(it.id) }
        if (_selectedID.value != null && orphans.contains(_selectedID.value)) {
            _selectedID.value = _servers.value.firstOrNull()?.id
        }
        persist()
    }

    suspend fun refresh(subscription: Subscription): Boolean {
        if (_refreshingIDs.value.contains(subscription.id)) return false
        _refreshingIDs.value = _refreshingIDs.value + subscription.id
        try {
            var stored = _subscriptions.value.firstOrNull { it.id == subscription.id } ?: return false

            try {
                val payload = withContext(Dispatchers.IO) { SubscriptionFetcher.fetch(stored.url) }
                val parsed = LinkParser.parseMany(payload.links.joinToString("\n")).configs

                val result = merge(existing = _servers.value, fetched = parsed, subscriptionID = stored.id)
                _servers.value = result.servers

                val title = payload.title
                stored = stored.copy(
                    name = if (stored.name.isEmpty() && !title.isNullOrEmpty()) title else stored.name,
                    announce = payload.announce,
                    rawUserInfo = payload.rawUserInfo,
                    usedBytes = payload.usedBytes,
                    totalBytes = payload.totalBytes,
                    expiresAt = payload.expiresAt,
                    lastUpdated = System.currentTimeMillis(),
                    lastError = null,
                    updateIntervalHours = payload.updateIntervalHours
                        ?.takeIf { stored.updateIntervalHours > 0 } ?: stored.updateIntervalHours,
                )

                if (_selectedID.value == null || _servers.value.none { it.id == _selectedID.value }) {
                    _selectedID.value = _servers.value.firstOrNull()?.id
                }
            } catch (e: Exception) {
                stored = stored.copy(lastError = e.message ?: L.t("Не удалось загрузить подписку.", "Could not load the subscription."))
            }

            _subscriptions.value = _subscriptions.value.map { if (it.id == stored.id) stored else it }
            persist()
            return stored.lastError == null
        } finally {
            _refreshingIDs.value = _refreshingIDs.value - subscription.id
        }
    }

    suspend fun refreshDue(force: Boolean = false) {
        for (subscription in _subscriptions.value) {
            if (force || subscription.isDue()) refresh(subscription)
        }
    }

    /** Что происходит при запуске приложения: сначала подписки — чтобы
     *  задержку мерить уже по актуальному списку, — и только потом замер. */
    suspend fun prepareOnLaunch(measureLatency: Boolean = true) {
        refreshDue(force = true)
        if (measureLatency) pingAll()
    }

    /** Фоновый тик раз в 15 минут, пока приложение живо — Android-аналог
     *  ServerStore.startAutoRefresh() на маке. force=false: решает isDue()
     *  каждой подписки её собственный updateIntervalHours, а не жёсткий
     *  список тут. Не заменяет prepareOnLaunch() (тот всегда форсит при
     *  открытии) — это то, что держит подписки свежими, пока приложение
     *  просто открыто и не перезапускалось часами. */
    fun startAutoRefresh() {
        if (autoRefreshJob != null) return
        autoRefreshJob = ioScope.launch {
            while (true) {
                delay(900_000L)
                refreshDue(force = false)
            }
        }
    }

    // MARK: - Пинги

    suspend fun pingAll() {
        if (_isPinging.value || _servers.value.isEmpty()) return
        _isPinging.value = true
        try {
            withContext(Dispatchers.IO) {
                val servers = _servers.value
                var index = 0
                while (index < servers.size) {
                    val slice = servers.subList(index, minOf(index + 4, servers.size))
                    val measured = slice.map { server ->
                        async { server.id to PingTester.latency(server.address, server.port) }
                    }.awaitAll()
                    for ((id, value) in measured) record(id, value)
                    index += 4
                }
            }
            persist()
        } finally {
            _isPinging.value = false
        }
    }

    suspend fun ping(server: ProxyConfig) {
        val value = withContext(Dispatchers.IO) { PingTester.latency(server.address, server.port) }
        record(server.id, value)
        persist()
    }

    private fun record(id: String, value: Int?) {
        _pings.value = _pings.value + (id to value)
        if (value == null) return
        val history = ((_pingHistory.value[id] ?: emptyList()) + value).takeLast(HISTORY_LENGTH)
        _pingHistory.value = _pingHistory.value + (id to history)
    }

    // MARK: - Диск

    private fun persist() {
        val file = storageFile ?: return
        val serversSnapshot = _servers.value
        val subscriptionsSnapshot = _subscriptions.value
        val selectedSnapshot = _selectedID.value
        val historySnapshot = _pingHistory.value

        ioScope.launch {
            val payload = JSONObject().apply {
                put("version", 1)
                put("servers", JSONArray().apply {
                    for (server in serversSnapshot) {
                        val link = server.sourceLink ?: continue
                        put(JSONObject().apply {
                            put("id", server.id)
                            put("sourceLink", link)
                            server.subscriptionID?.let { put("subscriptionID", it) }
                        })
                    }
                })
                put("subscriptions", JSONArray().apply {
                    for (subscription in subscriptionsSnapshot) put(subscription.toJSON())
                })
                selectedSnapshot?.let { put("selectedID", it) }
                put("pingHistory", JSONObject().apply {
                    for ((id, values) in historySnapshot) put(id, JSONArray(values))
                })
            }
            try {
                file.writeText(payload.toString())
            } catch (e: Exception) {
                // Диск недоступен — переживём до следующего сохранения.
            }
        }
    }

    private fun load() {
        val file = storageFile ?: return
        if (!file.exists()) return
        try {
            val payload = JSONObject(file.readText())

            val subscriptions = mutableListOf<Subscription>()
            payload.optJSONArray("subscriptions")?.let { array ->
                for (i in 0 until array.length()) subscriptions.add(Subscription.fromJSON(array.getJSONObject(i)))
            }

            val servers = mutableListOf<ProxyConfig>()
            payload.optJSONArray("servers")?.let { array ->
                for (i in 0 until array.length()) {
                    val entry = array.getJSONObject(i)
                    val link = entry.optString("sourceLink", "")
                    if (link.isEmpty()) continue
                    try {
                        val parsed = LinkParser.parse(link)
                        val subscriptionID = entry.optString("subscriptionID", "").takeIf { it.isNotEmpty() }
                        servers.add(
                            parsed.copy(
                                id = entry.optString("id", parsed.id),
                                subscriptionID = subscriptionID,
                            ),
                        )
                    } catch (e: Exception) {
                        // Ссылка на диске стала нечитаемой — пропускаем один узел, не всё хранилище.
                    }
                }
            }

            val history = mutableMapOf<String, List<Int>>()
            payload.optJSONObject("pingHistory")?.let { obj ->
                for (key in obj.keys()) {
                    val array = obj.optJSONArray(key) ?: continue
                    history[key] = (0 until array.length()).map { array.optInt(it) }
                }
            }

            _subscriptions.value = subscriptions
            _servers.value = servers
            _selectedID.value = if (payload.has("selectedID")) payload.getString("selectedID") else servers.firstOrNull()?.id
            _pingHistory.value = history
        } catch (e: Exception) {
            // Файл повреждён — начинаем с пустого списка вместо падения приложения.
        }
    }

    // MARK: - Слияние (чистая функция, тестируется без Context)

    /**
     * Приводит серверы подписки к тому, что пришло с панели. Уцелевшим
     * серверам сохраняем id — иначе при каждом обновлении слетал бы выбор в
     * списке. Остальные поля обновляем — переименование и смена ключей на
     * панели должны доезжать до клиента.
     */
    fun merge(existing: List<ProxyConfig>, fetched: List<ProxyConfig>, subscriptionID: String): ServerMergeResult {
        val mine = existing.filter { it.subscriptionID == subscriptionID }
        val others = existing.filter { it.subscriptionID != subscriptionID }

        val byKey = mine.associateBy { it.identityKey }
        val rebuilt = mutableListOf<ProxyConfig>()
        var added = 0
        var kept = 0
        val matchedKeys = mutableSetOf<String>()

        for (raw in fetched) {
            val incoming = raw.copy(subscriptionID = subscriptionID)
            val old = byKey[incoming.identityKey]
            if (old != null) {
                rebuilt.add(incoming.copy(id = old.id))
                matchedKeys.add(incoming.identityKey)
                kept++
            } else {
                rebuilt.add(incoming)
                added++
            }
        }

        val removed = mine.count { !matchedKeys.contains(it.identityKey) }
        return ServerMergeResult(servers = others + rebuilt, added = added, removed = removed, kept = kept)
    }
}
