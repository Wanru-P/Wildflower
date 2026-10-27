package com.floralens

import com.floralens.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import com.floralens.data.youtube.YouTubeService

class VideoAvailabilityTest {
    @Test fun completedEmptyLookupIsNotAServiceError() = runBlocking {
        val service = object : YouTubeService {
            override suspend fun sectionsFor(commonName: String, scientificName: String) = emptyList<VideoSection>()
        }
        assertEquals(VideoAvailability.EMPTY, service.lookup("桂花", "Osmanthus fragrans").availability)
    }
    @Test fun missingServiceNeverPromisesConnectingWillProvideVideos() {
        listOf(true, false).forEach { online -> listOf(true, false).forEach { connected ->
            assertEquals(VideoAvailability.UNAVAILABLE, cachedVideoAvailability(false, online, connected))
        } }
    }
    @Test fun offlineChoiceIsDifferentFromNetworkFailure() {
        assertEquals(VideoAvailability.OFFLINE_MODE, cachedVideoAvailability(true, false, true))
        assertEquals(VideoAvailability.NO_NETWORK, cachedVideoAvailability(true, true, false))
    }
    @Test fun scienceCacheDoesNotPretendACompletedVideoQuery() {
        assertEquals(VideoAvailability.UNAVAILABLE, cachedVideoAvailability(true, true, true))
        assertNotEquals(VideoAvailability.EMPTY.message, VideoAvailability.UNAVAILABLE.message)
    }
    @Test fun userMessagesContainNoCredentialsOrImplementationFields() {
        VideoAvailability.entries.mapNotNull { it.message }.forEach { message ->
            listOf("HTTP", "API", "Key", "StackTrace", "联网后可以继续浏览").forEach { assertFalse(message.contains(it)) }
        }
    }
}
