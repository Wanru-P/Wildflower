package com.floralens.ui.screens.card

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.card.export.CardExporter
import com.floralens.card.render.CardPhotoDecoder
import com.floralens.card.render.CardRenderer
import com.floralens.card.share.CardShare
import com.floralens.data.card.CardContentAssembler
import com.floralens.data.repo.ObservationRepository
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.card.*
import com.floralens.domain.model.Observation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import com.floralens.domain.culture.*

data class CardUiState(val loaded: Boolean = false, val plantExists: Boolean = true,
    val photos: List<Observation> = emptyList(), val selectedId: Long? = null,
    val template: CardTemplateId = CardTemplateId.MODERN_JOURNAL, val preview: Bitmap? = null,
    val error: String? = null, val hasCulture: Boolean = false,
    val plantName: String = "", val scientificName: String = "",
    val templatePreviews: Map<CardTemplateId, Bitmap> = emptyMap(),
    val expressionText: String = "", val expressionSource: String = "", val useNatureInJournal: Boolean = false)
sealed interface CardEvent {
    data class Share(val uri: Uri): CardEvent
    data class CreateDocument(val name: String): CardEvent
}

@HiltViewModel
class BotanicalCardViewModel @Inject constructor(private val saved: SavedStateHandle,
    plants: PlantRepository, observations: ObservationRepository,
    private val assembler: CardContentAssembler, private val renderer: CardRenderer,
    private val decoder: CardPhotoDecoder, private val exporter: CardExporter, private val share: CardShare): ViewModel() {
    private val plantId = checkNotNull(saved.get<Long>("plantId"))
    private val selected = saved.getStateFlow<Long?>("cardObservationId", null)
    private val template = saved.getStateFlow("cardTemplate", CardTemplateId.MODERN_JOURNAL.name)
    private val refresh = MutableStateFlow(0)
    private val _state = MutableStateFlow(CardUiState()); val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(saved.get<Boolean>("cardAwaitingDocument") == true); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _savedUri = MutableStateFlow<Uri?>(null); val savedUri = _savedUri.asStateFlow()
    private val events = Channel<CardEvent>(Channel.BUFFERED); val event = events.receiveAsFlow()
    private var previewContent: CardContent? = null
    private data class DocumentSnapshot(val observationId: Long, val template: CardTemplateId, val hash: String)
    private fun fingerprint(c: CardContent): String {
        // Length-prefix editable fields: punctuation inside a private note must not
        // accidentally collide with a delimiter in a data class's toString().
        val fields = listOf(c.plantId.toString(), c.observationId.toString(), c.nickname, c.chineseSpeciesName,
            c.scientificName, c.photoUri, c.observedDate.toString(), c.note, c.season, c.speciesKey,
            c.approvedCulture?.toString(), c.natureExpression?.identityKey, c.natureExpression?.source?.name,
            c.natureExpression?.text, c.useNatureInJournal.toString(), c.expressionOverridden.toString())
        val encoded = fields.joinToString("") { if (it == null) "-1:" else "${it.length}:$it" }
        return java.security.MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    private fun compose(content: ValidatedCardContent): ValidatedCardContent {
        val c = content.content
        val draft = saved.get<String>("cardCreativeText")
        val expression = if (draft != null) draft.takeIf(String::isNotBlank)?.let { NatureExpression(it, ExpressionSource.USER, c.speciesKey) } else c.natureExpression
        return CardValidation.validate(c.copy(natureExpression = expression, useNatureInJournal = saved.get<Boolean>("cardUseNatureInJournal") == true,
            expressionOverridden = draft != null), plantId, true)
    }

    init {
        viewModelScope.launch {
            combine(plants.observeById(plantId), observations.observeForPlant(plantId), selected, template, refresh) { plant, _, id, type, _ -> Triple(plant, id, CardTemplateId.valueOf(type)) }
                .collectLatest { (plant, id, type) ->
                    if(plant == null) { _state.value = CardUiState(loaded = true, plantExists = false); return@collectLatest }
                    val photos = withContext(Dispatchers.IO) { assembler.photos(plantId) }
                    val chosen = id ?: photos.firstOrNull { it.id == plant.coverObservationId }?.id ?: photos.firstOrNull()?.id
                    val base = CardUiState(true, true, photos, chosen, type, plantName = plant.nickname, scientificName = plant.scientificName)
                    _state.value = base
                    previewContent = null
                    if(chosen != null) {
                        try {
                            val content = compose(assembler.assemble(plantId, chosen))
                            val bitmap = render(content, type, CardTemplate.PREVIEW_WIDTH)
                            previewContent = content.content
                            val expression = content.content.natureExpression
                            val classicalWork = content.content.approvedCulture?.literaryWorks?.firstOrNull()
                                ?.takeIf { type == CardTemplateId.CLASSICAL_POETRY && !content.content.expressionOverridden }
                            val rendered = base.copy(preview = bitmap, hasCulture = content.content.approvedCulture != null,
                                expressionText = expression?.text.orEmpty(), expressionSource = classicalWork?.let {
                                    "作品引文 · ${it.author}《${it.title}》\n编辑文字 · ${expression?.source?.label.orEmpty()}"
                                } ?: expression?.source?.label.orEmpty(),
                                useNatureInJournal = content.content.useNatureInJournal)
                            _state.value = rendered
                            // UI-only miniature previews: same validated content and renderer as export.
                            // A template capacity failure must not invalidate another template's main preview.
                            val thumbnails = templatePreviews(content)
                            _state.value = rendered.copy(templatePreviews = thumbnails)
                        } catch(e: CancellationException) { throw e }
                        catch(e: Exception) { _state.value = base.copy(error = com.floralens.ui.presentation.ProductMessages.error(e, "暂时无法预览诗笺，请检查照片或精简文字后重试。")) }
                    }
                }
        }
    }
    fun refresh() { refresh.value += 1 }
    fun select(id: Long) { if(!_busy.value) { saved["cardObservationId"] = id; _message.value = null } }
    fun template(type: CardTemplateId) { if(!_busy.value) { saved["cardTemplate"] = type.name; _message.value = null } }
    fun feedback(message: String) { _message.value = message }
    // A resume refresh briefly publishes a loading state without rendered text.
    // The saved user draft is authoritative even during that render transition.
    fun creativeTextForEditing(): String = saved.get<String>("cardCreativeText") ?: state.value.expressionText
    fun creativeText(text: String) {
        if (_busy.value) return
        if (text.length > NatureTextPolicy.MAX) { feedback("创作文字最多 120 字；原文不会自动截断"); return }
        saved["cardCreativeText"] = text.trim(); refresh()
    }
    fun automaticText() { if (!_busy.value) { saved.remove<String>("cardCreativeText"); refresh() } }
    fun useNatureInJournal(value: Boolean) { if (!_busy.value) { saved["cardUseNatureInJournal"] = value; refresh() } }
    private suspend fun templatePreviews(content: ValidatedCardContent): Map<CardTemplateId, Bitmap> = withContext(Dispatchers.Default) {
        val photo = decoder.decode(content.content.photoUri)
        val output = mutableMapOf<CardTemplateId, Bitmap>()
        try {
            CardTemplateId.entries.forEach { type ->
                ensureActive()
                try { output[type] = renderer.render(content, type, photo, 160) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Main selection exposes its original renderer error. */ }
            }
            ensureActive()
            output
        } catch (e: Throwable) { output.values.forEach { it.recycle() }; throw e }
        finally { photo.recycle() }
    }
    private suspend fun render(content: ValidatedCardContent, type: CardTemplateId, width: Int): Bitmap = withContext(Dispatchers.Default) {
        val photo = decoder.decode(content.content.photoUri)
        try { renderer.render(content, type, photo, width).also { if(!isActive) { it.recycle(); ensureActive() } } }
        finally { photo.recycle() }
    }
    private fun operation(document: Uri? = null, sharing: Boolean = false, documentSnapshot: DocumentSnapshot? = null) {
        if(_busy.value) return
        val current = state.value
        val id = documentSnapshot?.observationId ?: current.selectedId ?: return
        val type = documentSnapshot?.template ?: current.template
        // Capture before launching the coroutine. Resume refresh may temporarily clear
        // the preview while DocumentsUI delivers its result; never compare against that null.
        val expectedHash = documentSnapshot?.hash ?: previewContent?.let(::fingerprint)
        _busy.value = true; _message.value = null
        viewModelScope.launch {
            var bitmap: Bitmap? = null
            try {
                val content = compose(assembler.assemble(plantId, id))
                check(expectedHash != null && fingerprint(content.content) == expectedHash) { "内容已更新，请查看更新预览后重试" }
                bitmap = render(content, type, CardTemplate.WIDTH)
                check(compose(assembler.assemble(plantId, id)).content == content.content) { "观察已发生变化，请查看更新预览后重试" }
                val output = withContext(Dispatchers.IO) {
                    if(sharing) share.prepare(bitmap) else if(document != null) exporter.saveDocument(bitmap, document) else exporter.saveGallery(bitmap)
                }
                if(sharing) events.send(CardEvent.Share(output))
                else { _savedUri.value = output; _message.value = if(document != null) "PNG 已保存到所选位置" else "PNG 已保存到 Pictures/Wildflower Lens" }
            } catch(e: CancellationException) {
                if(document != null) withContext(NonCancellable + Dispatchers.IO) { exporter.discardCreatedDocument(document) }
                throw e
            }
            catch(e: Exception) {
                if(document != null) withContext(Dispatchers.IO) { exporter.discardCreatedDocument(document) }
                _message.value = com.floralens.ui.presentation.ProductMessages.error(e, "暂时无法生成诗笺，请检查照片、文字长度和存储空间后重试。"); refresh()
            }
            finally { bitmap?.recycle(); _busy.value = false }
        }
    }
    fun saveGallery() = operation()
    fun share() = operation(sharing = true)
    fun shareIntent(uri: Uri) = share.intent(uri)
    fun requestDocument() {
        if(_busy.value || state.value.preview == null) return
        val content = previewContent ?: return
        val id = state.value.selectedId ?: return
        saved["cardDocumentObservation"] = id
        saved["cardDocumentTemplate"] = state.value.template.name
        saved["cardDocumentSnapshot"] = fingerprint(content)
        saved["cardAwaitingDocument"] = true
        _busy.value = true
        viewModelScope.launch { events.send(CardEvent.CreateDocument(exporter.fileName())) }
    }
    fun documentResult(uri: Uri?) {
        if(saved.get<Boolean>("cardAwaitingDocument") != true) return
        val snapshot = runCatching { DocumentSnapshot(checkNotNull(saved.get<Long>("cardDocumentObservation")),
            CardTemplateId.valueOf(checkNotNull(saved.get<String>("cardDocumentTemplate"))), checkNotNull(saved.get<String>("cardDocumentSnapshot"))) }.getOrNull()
        saved["cardAwaitingDocument"] = false; _busy.value = false
        saved.remove<Long>("cardDocumentObservation"); saved.remove<String>("cardDocumentTemplate"); saved.remove<String>("cardDocumentSnapshot")
        if(uri == null) feedback("已取消保存，观察照片未改变")
        else if (snapshot != null) operation(document = uri, documentSnapshot = snapshot)
        else {
            feedback("页面状态已更新，请重新选择保存位置")
            viewModelScope.launch { withContext(Dispatchers.IO) { exporter.discardCreatedDocument(uri) }; refresh() }
        }
    }
}
