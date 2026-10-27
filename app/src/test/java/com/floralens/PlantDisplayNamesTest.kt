package com.floralens

import com.floralens.ui.presentation.PlantDisplayNames
import com.floralens.ui.presentation.ProductMessages
import org.junit.Assert.*
import org.junit.Test

class PlantDisplayNamesTest {
    @Test fun knownScientificNamesPreferChineseWithoutMutatingInput() {
        val scientific = " Ginkgo   biloba "
        assertEquals("银杏", PlantDisplayNames.chinese(scientific, "Maidenhair tree"))
        assertEquals(" Ginkgo   biloba ", scientific)
        assertEquals("桂花", PlantDisplayNames.chinese("OSMANTHUS FRAGRANS", "Sweet Osmanthus"))
        assertEquals("山茶", PlantDisplayNames.chinese("Camellia japonica", "Japanese camellia"))
        assertEquals("木犀科 · 木犀属", PlantDisplayNames.find("Osmanthus fragrans")?.classification)
    }
    @Test fun sourceCheckedCatalogNamesUseExactSpeciesMappings() {
        val cases = mapOf(
            "Prunus mume" to "梅花",
            "Nelumbo nucifera" to "荷花",
            "Prunus persica" to "桃花",
            "Punica granatum" to "石榴",
            "Gardenia jasminoides" to "栀子",
            "Chimonanthus praecox" to "蜡梅",
            "Prunus armeniaca" to "杏花",
            "Lagerstroemia indica" to "紫薇",
            "Camellia sinensis" to "茶树",
            "Litchi chinensis" to "荔枝",
            "Ziziphus jujuba" to "枣树",
            "Jasminum sambac" to "茉莉",
            "Hibiscus syriacus" to "木槿",
            "Hibiscus mutabilis" to "木芙蓉",
            "Eriobotrya japonica" to "枇杷",
            "Camellia japonica" to "山茶",
            "Magnolia grandiflora" to "广玉兰",
        )
        cases.forEach { (scientific, expected) ->
            assertEquals(scientific, expected, PlantDisplayNames.chinese(scientific, null))
            assertNotNull(scientific, PlantDisplayNames.find(scientific))
        }
    }
    @Test fun unknownNamesAreNotGuessedOrMatchedByGenus() {
        assertEquals("未知植物", PlantDisplayNames.chinese("Camellia unknown", "Camellia"))
        assertEquals("未知植物", PlantDisplayNames.chinese("Ginkgo", "Ginkgo"))
        assertEquals("未知植物", PlantDisplayNames.chinese(null, null))
        assertNull(PlantDisplayNames.find("Camellia japonica var. rusticana"))
        assertEquals("校园里的树", PlantDisplayNames.chinese(null, "校园里的树"))
    }
    @Test fun confidenceOnlyFormatsThisRecognitionAndHandlesInvalidInput() {
        assertEquals("识别可信度 92%", PlantDisplayNames.confidence(.92f))
        assertEquals("识别可信度 100%", PlantDisplayNames.confidence(1.4f))
        assertEquals("识别可信度 0%", PlantDisplayNames.confidence(-.2f))
        assertEquals("识别可信度暂不可用", PlantDisplayNames.confidence(Float.NaN))
    }
    @Test fun rawServiceAndStorageErrorsCannotReachProductCopy() {
        val fallback = "暂时无法保存，请稍后重试。"
        listOf("HTTP 403 key=private", "SQLiteException /data/user/0", "API Error", "未批准的文化资料不能保存或分享").forEach {
            assertEquals(fallback, ProductMessages.error(IllegalStateException(it), fallback))
        }
        assertEquals("需要相应权限，请前往系统设置允许后重试。", ProductMessages.error(SecurityException("denied"), fallback))
        assertEquals("存储空间不足，请腾出空间后重试。", ProductMessages.error(IllegalStateException("disk full"), fallback))
        assertEquals("观察已删除，请重新选择照片", ProductMessages.error(IllegalArgumentException("观察已删除，请重新选择照片"), fallback))
        assertEquals("观察已发生变化，请查看更新预览后重试", ProductMessages.error(IllegalStateException("观察已发生变化，请查看更新预览后重试"), fallback))
    }
}
