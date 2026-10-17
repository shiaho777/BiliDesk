package app.bilidesk.core.model

const val REGION_PAGE_SIZE = 20

data class RegionPageOutcome(
    val nextDisplayId: Int,
    val noMore: Boolean,
    val addedBvids: List<String>,
)

fun afterRegionPage(displayId: Int, fetchedBvids: List<String>, seen: Set<String>): RegionPageOutcome {
    if (fetchedBvids.isEmpty()) {
        return RegionPageOutcome(nextDisplayId = displayId, noMore = true, addedBvids = emptyList())
    }
    val added = fetchedBvids.filter { it !in seen }
    val noMore = fetchedBvids.size < REGION_PAGE_SIZE || added.isEmpty()
    return RegionPageOutcome(nextDisplayId = displayId + 1, noMore = noMore, addedBvids = added)
}
