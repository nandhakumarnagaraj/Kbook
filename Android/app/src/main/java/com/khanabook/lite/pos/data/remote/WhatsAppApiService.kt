package com.khanabook.lite.pos.data.remote


import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface WhatsAppApiService {
    @POST("{phone_number_id}/messages")
    suspend fun sendOtp(
        @Path("phone_number_id") phoneNumberId: String,
        @Header("Authorization") token: String,
        @Body request: WhatsAppRequest
    ): Response<WhatsAppResponse>
}

data class WhatsAppRequest(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String = "template",
    val template: WhatsAppTemplate
)

data class WhatsAppTemplate(
    val name: String,
    val language: Language,
    val components: List<Component>
)

data class Language(
    val code: String = "en"
)

data class Component(
    val type: String,
    val sub_type: String? = null,
    val index: String? = null,
    val parameters: List<Parameter>
)

data class Parameter(
    val type: String = "text",
    val text: String
)

data class WhatsAppResponse(
    val messaging_product: String,
    val contacts: List<ContactResponse>,
    val messages: List<MessageResponse>
)

data class ContactResponse(
    val input: String,
    val wa_id: String
)

data class MessageResponse(
    val id: String
)


