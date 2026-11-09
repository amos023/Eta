package io.github.mangi.eta.ui.pages.providers

import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.typeLabel
import java.text.Collator
import java.util.Locale

/** 提供商列表的一次性整理规则；执行后把结果写回 sort_order，之后列表按新顺序展示。 */
internal enum class ProviderSortRule { NAME, TYPE, STATUS }

/**
 * 按规则计算新顺序。
 * [ProviderSortRule.STATUS] 以「有没有模型」判断是否已配置：零模型的提供商通常只是占位或未启用，
 * 统一排到已配置的后面；同组内按名称排，保证结果稳定可预期。
 */
internal fun List<ProviderSetting>.sortedByRule(rule: ProviderSortRule): List<ProviderSetting> {
    // 中文按拼音、其余按各自语言规则；PRIMARY 强度让大小写与全半角不影响先后。
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    val byName = Comparator<ProviderSetting> { first, second ->
        collator.compare(first.name, second.name).takeIf { it != 0 }
            ?: first.name.compareTo(second.name)
    }
    val comparator: Comparator<ProviderSetting> = when (rule) {
        ProviderSortRule.NAME -> byName
        ProviderSortRule.TYPE -> compareBy<ProviderSetting> { it.typeLabel }.then(byName)
        ProviderSortRule.STATUS -> compareBy<ProviderSetting> { if (it.models.isEmpty()) 1 else 0 }
            .then(byName)
    }
    // 末位兜底 id：即使重名也能得到确定的顺序。
    return sortedWith(comparator.thenBy { it.id })
}