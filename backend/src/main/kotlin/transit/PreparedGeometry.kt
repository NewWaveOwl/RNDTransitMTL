package transit

import kotlinx.serialization.json.JsonElement
import kotlin.math.*

/** Immutable, predecoded polygon rings. No GeoJSON allocation occurs during point queries. */
internal class PreparedGeometry(geometry: JsonElement) {
    private class Ring(raw: JsonElement) {
        private val vertices=raw.items().map { row ->
            val numbers=row.items()
            val x=numbers.getOrNull(0).number() ?: error("Invalid polygon longitude")
            val y=numbers.getOrNull(1).number() ?: error("Invalid polygon latitude")
            require(x.isFinite() && y.isFinite())
            x to y
        }.also { require(it.size>=4) { "Polygon rings need four vertices." } }
        private val west=vertices.minOf { it.first }; private val east=vertices.maxOf { it.first }
        private val south=vertices.minOf { it.second }; private val north=vertices.maxOf { it.second }
        fun contains(p: Point, check: ()->Unit): Boolean {
            check()
            if(p.longitude !in west..east || p.latitude !in south..north) return false
            var inside=false
            for(i in vertices.indices) {
                if(i%256==0) check()
                val (xi,yi)=vertices[i]; val (xj,yj)=vertices[(i+vertices.size-1)%vertices.size]
                val cross=(p.longitude-xi)*(yj-yi)-(p.latitude-yi)*(xj-xi)
                if(abs(cross)<1e-12 && p.longitude in min(xi,xj)..max(xi,xj) && p.latitude in min(yi,yj)..max(yi,yj)) return true
                if((yi>p.latitude)!=(yj>p.latitude) && p.longitude<(xj-xi)*(p.latitude-yi)/(yj-yi)+xi) inside=!inside
            }
            return inside
        }
    }
    private val polygons: List<List<Ring>>
    init {
        val coordinates=geometry.field("coordinates") ?: error("Missing polygon")
        val raw=when(geometry.field("type").text()) {
            "Polygon" -> listOf(coordinates)
            "MultiPolygon" -> coordinates.items()
            else -> error("Expected polygon geometry")
        }
        polygons=raw.map { polygon -> polygon.items().map(::Ring).also { require(it.isNotEmpty()) } }
        require(polygons.isNotEmpty())
    }
    fun contains(point: Point, check: ()->Unit = {}): Boolean = polygons.any { rings ->
        rings.first().contains(point,check) && rings.drop(1).none { it.contains(point,check) }
    }
}
