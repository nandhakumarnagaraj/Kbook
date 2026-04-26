package com.khanabook.lite.pos.domain.manager

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PaymentReturnManager {

    data class ReturnEvent(
        val status: Status,
        val txnId: String?
    )

    enum class Status {
        SUCCESS,
        FAILURE
    }

    private val _events = MutableSharedFlow<ReturnEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events = _events.asSharedFlow()

    fun handleIntent(intent: Intent?) {
        publish(intent?.data ?: return)
    }

    private fun publish(uri: Uri) {
        if (uri.scheme?.lowercase() != "khanabook") return
        if (uri.host?.lowercase() != "payment") return

        val status = when (uri.path?.lowercase()) {
            "/success" -> Status.SUCCESS
            "/failure" -> Status.FAILURE
            else -> return
        }

        _events.tryEmit(
            ReturnEvent(
                status = status,
                txnId = uri.getQueryParameter("txnid")
                    ?: uri.getQueryParameter("txnId")
            )
        )
    }
}
