package com.github.wumo.http

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonElement

object JsonUtil {
  val json = Json(JsonConfiguration.Stable.copy(prettyPrint = true))
  
  private val maps = MapSerializer(String.serializer(), String.serializer())
  
  fun Map<String, Any>.toJson() =
    json.stringify(maps, entries.associate { (k, v)-> k to v.toString() })
  
  fun String.json() = json.parseJson(this)
  operator fun JsonElement.get(vararg path: String): JsonElement {
    var element = this
    for(p in path)
      element = element.jsonObject[p]!!
    return element
  }
  
  fun JsonElement.tryGet(vararg path: String): JsonElement? {
    var element: JsonElement? = this
    for(p in path) {
      if(element == null) return null
      element = element.jsonObject[p]
    }
    return element
  }
}