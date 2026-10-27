package com.floralens.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.data.chat.PlantChatConsentStore
import com.floralens.data.chat.PlantChatContextResolver
import com.floralens.data.chat.PlantChatException
import com.floralens.data.chat.PlantChatRepository
import com.floralens.data.chat.plantChatMessage
import com.floralens.data.culture.NatureServiceConfig
import com.floralens.domain.chat.PlantChatContext
import com.floralens.domain.chat.PlantChatMessage
import com.floralens.domain.chat.PlantChatPolicy
import com.floralens.domain.chat.PlantChatRole
import com.floralens.domain.chat.PlantChatSource
import com.floralens.domain.chat.PlantChatTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlantChatUiState(
    val loadingContext: Boolean = true,
    val context: PlantChatContext? = null,
    val messages: List<PlantChatMessage> = emptyList(),
    val isReplying: Boolean = false,
    val error: String? = null,
    val retryQuestion: String? = null,
    val showConsent: Boolean = false,
    val showConfiguration: Boolean = false,
    val showPrivacy: Boolean = false,
    val configured: Boolean = false,
)

@HiltViewModel
class PlantChatViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val contexts: PlantChatContextResolver,
    private val repository: PlantChatRepository,
    private val consent: PlantChatConsentStore,
    private val serviceConfig: NatureServiceConfig,
) : ViewModel() {
    private val source = PlantChatSource.fromRoute(saved.get<String>("source"))
    private val speciesKey = saved.get<String>("speciesKey")
    private val plantId = saved.get<Long>("plantId") ?: 0L
    private val captureId = saved.get<String>("captureId")
    private val mutable = MutableStateFlow(PlantChatUiState(configured = serviceConfig.available()))
    val state = mutable.asStateFlow()
    private var pendingQuestion: String? = null
    private var replyJob: Job? = null
    private var nextId = 1L

    init {
        viewModelScope.launch {
            val context = source?.let { contexts.resolve(it, speciesKey, plantId, captureId) }
            mutable.update { it.copy(loadingContext = false, context = context,
                error = if (context == null) "植物上下文已经失效，请返回原页面重新进入。" else null) }
        }
    }

    fun ask(question: String) {
        val clean = question.trim()
        if (!PlantChatPolicy.validQuestion(clean) || mutable.value.isReplying || mutable.value.context == null) {
            if (clean.isNotEmpty() && !PlantChatPolicy.validQuestion(clean)) mutable.update { it.copy(error = plantChatMessage("input")) }
            return
        }
        if (!consent.accepted()) {
            pendingQuestion = clean
            mutable.update { it.copy(showConsent = true, error = null) }
            return
        }
        send(clean, appendUser = true)
    }

    fun acceptAndAsk() {
        consent.grant()
        val question = pendingQuestion
        pendingQuestion = null
        mutable.update { it.copy(showConsent = false) }
        if (question != null) send(question, appendUser = true)
    }

    fun dismissConsent() {
        pendingQuestion = null
        mutable.update { it.copy(showConsent = false) }
    }

    fun retry() {
        val question = mutable.value.retryQuestion ?: return
        if (!mutable.value.isReplying) send(question, appendUser = false)
    }

    fun cancelReply() {
        replyJob?.cancel()
        replyJob = null
        mutable.update { it.copy(isReplying = false, error = plantChatMessage("cancelled"), retryQuestion = null) }
    }

    private fun send(question: String, appendUser: Boolean) {
        val context = mutable.value.context ?: return
        val before = mutable.value.messages
        val historyMessages = if (appendUser) before else before.dropLastWhile { it.role == PlantChatRole.USER }
        val history = historyMessages.map { PlantChatTurn(it.role, it.text) }
        val shown = if (appendUser) before + PlantChatMessage(nextId++, PlantChatRole.USER, question) else before
        mutable.update { it.copy(messages = shown, isReplying = true, error = null, retryQuestion = null) }
        replyJob = viewModelScope.launch {
            try {
                val answer = repository.reply(context, history, question)
                mutable.update { it.copy(messages = it.messages + PlantChatMessage(nextId++, PlantChatRole.ASSISTANT, answer), isReplying = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = (e as? PlantChatException)?.reason ?: "network"
                mutable.update { it.copy(isReplying = false, error = plantChatMessage(reason), retryQuestion = question,
                    showConfiguration = reason in setOf("configuration", "authorization")) }
            } finally {
                replyJob = null
            }
        }
    }

    fun clearSession() = mutable.update { it.copy(messages = emptyList(), error = null, retryQuestion = null) }
    fun showPrivacy() = mutable.update { it.copy(showPrivacy = true) }
    fun dismissPrivacy() = mutable.update { it.copy(showPrivacy = false) }
    fun revokeConsent() {
        consent.revoke()
        serviceConfig.clearSession()
        mutable.update { it.copy(showPrivacy = false, error = "已撤回花间问答授权；下次发送前会再次征求同意。") }
    }
    fun showConfiguration() = mutable.update { it.copy(showConfiguration = true) }
    fun dismissConfiguration() = mutable.update { it.copy(showConfiguration = false) }
    fun saveConfiguration(url: String, token: String) {
        runCatching { serviceConfig.save(url.trim(), token.trim()) }
            .onSuccess { mutable.update { it.copy(configured = true, showConfiguration = false, error = null) } }
            .onFailure { error -> mutable.update { it.copy(error = error.message ?: "服务配置无效，请检查地址和访问凭证。") } }
    }
}
