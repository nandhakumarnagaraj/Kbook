package com.khanabook.lite.pos.feature.settings.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TutorialVideo(
    val title: String,
    val fileName: String
)

data class TutorialListUiState(
    val loading: Boolean = true,
    val videos: List<TutorialVideo> = emptyList()
)

/**
 * Loads the tutorial catalogue from the CDN manifest. A bundled fallback keeps the three videos
 * that are already published working even before `videos.json` is uploaded, so staff never see an
 * empty screen just because the manifest has not been deployed yet.
 */
class TutorialListViewModel : ViewModel() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow(TutorialListUiState())
    val uiState: StateFlow<TutorialListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = TutorialListUiState(loading = true)
        viewModelScope.launch {
            val remote = withContext(Dispatchers.IO) { fetchManifest() }
            val videos = remote ?: FALLBACK_VIDEOS
            Log.d(
                TAG,
                if (remote != null) "Loaded ${videos.size} tutorial(s) from manifest"
                else "Manifest unavailable; showing ${videos.size} bundled tutorial(s)"
            )
            _uiState.value = TutorialListUiState(loading = false, videos = videos)
        }
    }

    private fun fetchManifest(): List<TutorialVideo>? = try {
        val request = Request.Builder().url(MANIFEST_URL).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Manifest HTTP ${response.code}")
                return null
            }
            val body = response.body?.string().orEmpty()
            val array = JSONObject(body).optJSONArray("videos") ?: return null
            val result = mutableListOf<TutorialVideo>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                val file = item.optString("file").trim()
                if (title.isEmpty() || !isSafeFileName(file)) continue
                result += TutorialVideo(title = title, fileName = file)
            }
            result.takeIf { it.isNotEmpty() }
        }
    } catch (error: Exception) {
        Log.w(TAG, "Manifest fetch failed: ${error.message}")
        null
    }

    private fun isSafeFileName(file: String): Boolean =
        file.length <= 120 &&
            SAFE_FILE_NAME.matches(file) &&
            !file.contains("..")

    override fun onCleared() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        super.onCleared()
    }

    private companion object {
        const val TAG = "KHANABOOK_WEB"
        const val MANIFEST_URL = "https://kbook.iadv.cloud/cdn/tutorials/videos.json"
        val SAFE_FILE_NAME = Regex("[A-Za-z0-9._-]+")
        val FALLBACK_VIDEOS = listOf(
            TutorialVideo("Create a Quick Bill", "create-quickbill.webm"),
            TutorialVideo("Cancel Bill / Change Payment Mode", "cancel-bill-change-payment-mode.webm"),
            TutorialVideo("Continue with Google", "continue-with-google.webm")
        )
    }
}
