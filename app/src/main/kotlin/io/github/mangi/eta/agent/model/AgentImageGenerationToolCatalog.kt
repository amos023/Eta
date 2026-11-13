package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** 生图工具的声明；执行逻辑在 AgentImageGenerationTools。 */
internal object AgentImageGenerationToolCatalog {
    const val TOOL_NAME = "generate_image"

    fun appendTo(tools: JSONArray, enabled: Boolean) {
        if (!enabled) return
        tools.put(
            AgentToolSchema.function(
                name = TOOL_NAME,
                description = "调用已配置的生图模型生成图片。用户要求画图、生成图片、出图时使用；" +
                    "返回的图片会直接显示在聊天里。需要多张就调多次或提高 count。",
                parameters = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put(
                                "prompt",
                                JSONObject()
                                    .put("type", "string")
                                    .put("maxLength", 4000)
                                    .put("description", "生图提示词；把用户要求整理成模型能直接用的描述"),
                            )
                            .put(
                                "size",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "图片尺寸，宽x高，例如 1024x1024；auto 表示由服务商决定"),
                            )
                            .put(
                                "count",
                                JSONObject()
                                    .put("type", "integer")
                                    .put("minimum", 1)
                                    .put("maximum", 4)
                                    .put("description", "生成张数，默认 1"),
                            )
                            .put(
                                "model",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "可选，指定生图模型；省略时用输入栏里选中的生图模型"),
                            ),
                    )
                    .put("required", JSONArray(listOf("prompt"))),
            ),
        )
    }
}