package com.floralens

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.ui.components.editorialSpeciesArtwork
import org.junit.Assert.*
import org.junit.Test

/** Public illustrations are explicitly species-scoped, not a generic personal-photo fallback. */
class VisualKitV2AssetTest {
    @Test fun suppliedArtworkIsSpeciesScopedAndBasesAreRealPortraits() {
        assertEquals(R.drawable.wf_editorial_osmanthus_hero, editorialSpeciesArtwork("Osmanthus fragrans"))
        assertEquals(R.drawable.wf_editorial_ginkgo, editorialSpeciesArtwork("Ginkgo biloba"))
        assertNull(editorialSpeciesArtwork("Osmanthus heterophyllus"))
        assertNull(editorialSpeciesArtwork("未知植物"))
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        for (id in listOf(R.drawable.wf_card_base_atlas, R.drawable.wf_card_base_classical, R.drawable.wf_card_base_journal)) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeResource(resources, id, bounds)
            assertEquals(1600, bounds.outWidth); assertEquals(2400, bounds.outHeight)
        }
    }
}
