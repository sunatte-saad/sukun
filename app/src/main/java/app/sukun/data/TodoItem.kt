package app.sukun.data

import org.json.JSONArray
import org.json.JSONObject

data class TodoItem(
    val id: Long,
    val text: String,
    val completed: Boolean = false
)

fun List<TodoItem>.toTodoJson(): String {
    val array = JSONArray()
    forEach { item ->
        JSONObject().also { obj ->
            obj.put("id", item.id)
            obj.put("text", item.text)
            obj.put("completed", item.completed)
            array.put(obj)
        }
    }
    return array.toString()
}

fun String.toTodoList(): List<TodoItem> {
    if (isBlank()) return emptyList()
    return try {
        val array = JSONArray(this)
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            TodoItem(
                id = obj.getLong("id"),
                text = obj.getString("text"),
                completed = obj.optBoolean("completed", false)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
