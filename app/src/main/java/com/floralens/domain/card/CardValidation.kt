package com.floralens.domain.card

import com.floralens.data.culture.CulturePolicy

object CardValidation {
    /** Reject rather than silently truncate. Editor shows these limits before saving. */
    fun validate(content: CardContent, ownerId: Long, photoReadable: Boolean): ValidatedCardContent {
        require(content.plantId > 0 && content.observationId > 0 && content.plantId == ownerId) { "观察不属于这株植物" }
        require(photoReadable) { "观察照片已失效，请重新选择或添加观察" }
        require(content.nickname.isNotBlank() && content.nickname.length <= 100) { "植物昵称需为 1–100 字" }
        require(content.chineseSpeciesName.length <= 200 && content.scientificName.length <= 300) { "名称过长，请先整理植物档案（名称最多 200 字，学名 300 字）" }
        require((content.note?.length ?: 0) <= 600) { "此观察备注超过诗笺 600 字容量；请选另一条观察或先编辑备注，原文不会自动截断" }
        require(content.observedDate.year in 1..9999) { "观察日期超出支持范围" }
        content.approvedCulture?.let {
            require(it.speciesKey == content.speciesKey && CulturePolicy.eligible(it, false)) { "未批准的文化资料不能保存或分享" }
        }
        content.natureExpression?.let {
            require(it.identityKey == content.speciesKey && com.floralens.domain.culture.NatureTextPolicy.valid(it)) { "创作文字无效，请重新选择内容" }
        }
        return ValidatedCardContent(content)
    }
}
