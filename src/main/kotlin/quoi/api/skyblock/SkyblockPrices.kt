package quoi.api.skyblock

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import quoi.QuoiMod.mc
import quoi.config.ConfigSystem
import quoi.config.configPath
import quoi.utils.ChatUtils.modMessage
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

object SkyblockPrices {
    private const val BAZAAR_URL = "https://api.hypixel.net/skyblock/bazaar"
    private const val ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items"
    private const val LOWEST_BINS_URL = "https://lb.tricked.dev/lowestbins"
    private const val CACHE_TIME_MS = 30 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val updating = AtomicBoolean(false)
    private val pendingCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val cacheFile = File(configPath, "skyblock_prices.json")

    @Volatile
    private var lastUpdate = 0L
    @Volatile
    private var bazaarSellPrices = emptyMap<String, Double>()
    @Volatile
    private var bazaarBuyPrices = emptyMap<String, Double>()
    @Volatile
    private var lowestBins = emptyMap<String, Double>()
    @Volatile
    private var skyblockItems = emptyMap<String, SkyblockItem>()

    init {
        loadCache()
    }

    val isFresh: Boolean
        get() = System.currentTimeMillis() - lastUpdate <= CACHE_TIME_MS

    fun ensureFresh(onDone: (Boolean) -> Unit = {}) {
        if (isFresh) {
            onDone(true)
            return
        }
        update(onDone)
    }

    fun update(onDone: (Boolean) -> Unit = {}) {
        synchronized(pendingCallbacks) {
            pendingCallbacks += onDone
        }
        if (!updating.compareAndSet(false, true)) return

        scope.launch {
            val success = runCatching {
                val bazaar = getJson(BAZAAR_URL).asJsonObject
                val items = getJson(ITEMS_URL).asJsonObject
                val bins = getJson(LOWEST_BINS_URL).asJsonObject

                val bazaarPrices = parseBazaar(bazaar)
                bazaarSellPrices = bazaarPrices.sell
                bazaarBuyPrices = bazaarPrices.buy
                skyblockItems = parseItems(items)
                lowestBins = parseLowestBins(bins)
                lastUpdate = System.currentTimeMillis()
                saveCache()
            }.onFailure {
                mc.execute {
                    modMessage("&cAuto Croesus price update failed: ${it.message ?: it::class.simpleName}")
                }
            }.isSuccess

            updating.set(false)
            val callbacks = synchronized(pendingCallbacks) {
                pendingCallbacks.toList().also { pendingCallbacks.clear() }
            }
            mc.execute {
                callbacks.forEach { it(success) }
            }
        }
    }

    fun buyPrice(itemId: String, bazaarPriceType: BazaarPriceType = BazaarPriceType.InstaSell): Double? {
        val bazaar = when (bazaarPriceType) {
            BazaarPriceType.SellOffer -> bazaarBuyPrices[itemId]
            BazaarPriceType.InstaSell -> bazaarSellPrices[itemId]
        }
        if (bazaar != null) return bazaar
        return lowestBins[itemId]
    }

    fun itemIdByName(name: String): String? =
        skyblockItems.values.firstOrNull { it.name == name && !it.id.startsWith("STARRED_") }?.id

    private fun getJson(url: String): com.google.gson.JsonElement {
        val response = client.send(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()} from $url")
        }
        return JsonParser.parseString(response.body())
    }

    private fun parseBazaar(root: JsonObject): BazaarPrices {
        val products = root["products"]?.asJsonObject ?: return BazaarPrices()
        val sell = mutableMapOf<String, Double>()
        val buy = mutableMapOf<String, Double>()
        products.entrySet().forEach { (itemId, dataElement) ->
            val data = dataElement.asJsonObject
            val quickStatus = data["quick_status"]?.asJsonObject

            val sellSummary = data["sell_summary"]?.asJsonArray
            val sellFallback = quickStatus?.get("sellPrice")?.asDouble ?: 0.0
            val sellSampled = sellSummary
                ?.take(5)
                ?.mapNotNull { it.asJsonObject["pricePerUnit"]?.asDouble }
                ?.takeIf { it.isNotEmpty() }
                ?.average()
            sell[itemId] = sellSampled ?: sellFallback

            val buySummary = data["buy_summary"]?.asJsonArray
            val buyFallback = quickStatus?.get("buyPrice")?.asDouble ?: 0.0
            val buySampled = buySummary
                ?.take(5)
                ?.mapNotNull { it.asJsonObject["pricePerUnit"]?.asDouble }
                ?.takeIf { it.isNotEmpty() }
                ?.average()
            buy[itemId] = buySampled ?: buyFallback
        }
        return BazaarPrices(sell, buy)
    }

    private fun parseLowestBins(root: JsonObject): Map<String, Double> =
        root.entrySet().associate { (id, value) -> id to value.asDouble }

    private fun parseItems(root: JsonObject): Map<String, SkyblockItem> {
        val items = root["items"]?.asJsonArray ?: return emptyMap()
        return items.mapNotNull { element ->
            val item = element.asJsonObject
            val id = item["id"]?.asString ?: return@mapNotNull null
            val name = item["name"]?.asString ?: return@mapNotNull null
            id to SkyblockItem(id, name)
        }.toMap()
    }

    private fun loadCache() {
        val cache = ConfigSystem.load(cacheFile) { PriceCache() }
        lastUpdate = cache.lastUpdate
        bazaarSellPrices = cache.bazaarSellPrices.ifEmpty { cache.bazaarSellOfferPrices.ifEmpty { cache.bazaarPrices } }
        bazaarBuyPrices = cache.bazaarBuyPrices.ifEmpty { cache.bazaarInstaSellPrices.ifEmpty { cache.bazaarPrices } }
        lowestBins = cache.lowestBins
        skyblockItems = cache.skyblockItems
    }

    private fun saveCache() {
        ConfigSystem.save(
            cacheFile,
            PriceCache(
                lastUpdate = lastUpdate,
                bazaarSellPrices = bazaarSellPrices,
                bazaarBuyPrices = bazaarBuyPrices,
                lowestBins = lowestBins,
                skyblockItems = skyblockItems,
            )
        )
    }

    data class PriceCache(
        val lastUpdate: Long = 0L,
        val bazaarPrices: Map<String, Double> = emptyMap(),
        val bazaarSellOfferPrices: Map<String, Double> = emptyMap(),
        val bazaarInstaSellPrices: Map<String, Double> = emptyMap(),
        val bazaarSellPrices: Map<String, Double> = emptyMap(),
        val bazaarBuyPrices: Map<String, Double> = emptyMap(),
        val lowestBins: Map<String, Double> = emptyMap(),
        val skyblockItems: Map<String, SkyblockItem> = emptyMap(),
    )

    data class BazaarPrices(
        val sell: Map<String, Double> = emptyMap(),
        val buy: Map<String, Double> = emptyMap(),
    )

    enum class BazaarPriceType {
        SellOffer,
        InstaSell,
    }

    data class SkyblockItem(val id: String, val name: String)
}
