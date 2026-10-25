package com.ghostdisplay.app.browser

class TabPersistenceRepository(
    private val sessions: BrowserSessionDao,
    private val tabs: BrowserTabDao,
    private val history: TabHistoryDao,
) : TabStore {

    val tabsFlow = tabs.observeAll()
    val historyFlow = history.observeLatest(200)

    override suspend fun session(): BrowserSessionState? = sessions.any()?.toModel()

    override suspend fun upsertSession(session: BrowserSessionState) {
        sessions.upsert(session.toEntity())
    }

    override suspend fun openTabs(): List<TabRecord> = tabs.all().map { it.toModel() }

    override suspend fun upsertTab(tab: TabRecord) {
        tabs.upsert(tab.toEntity())
    }

    override suspend fun deleteTab(id: String) {
        tabs.delete(id)
    }

    override suspend fun log(event: TabLogEvent) {
        history.insert(event.toEntity())
    }

    override suspend fun history(limit: Int): List<TabLogEvent> =
        history.latest(limit).map { it.toModel() }
}
