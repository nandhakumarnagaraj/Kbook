package com.khanabook.saas.core.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the whole API.
 *
 * <p>Previously this bean was restricted to {@code @Profile("!prod")}, which left the production
 * document with springdoc's placeholder title ("OpenAPI definition"), no description and — most
 * importantly — no bearer security scheme. Swagger UI then offered no "Authorize" button, so every
 * protected endpoint could not be exercised from the docs even though the document itself was
 * publicly served in production.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "Bearer Authentication";

    /**
     * Paths that {@code SecurityConfig} serves without authentication. These are matched as
     * prefixes so that, for example, {@code /auth/signup/request} is covered by
     * {@code /auth/signup}.
     */
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/auth/login",
            "/auth/refresh",
            "/auth/signup",
            "/auth/google",
            "/auth/check-user",
            "/auth/reset-password",
            "/auth/forgot-password",
            "/public/",
            "/cdn/",
            "/payments/easebuzz/webhook",
            "/payments/easebuzz/payout/webhook",
            "/payments/easebuzz/refund/webhook",
            "/payments/easebuzz/sub-merchant/webhook",
            "/payments/easebuzz/return",
            "/actuator/health");

    @Bean
    OpenAPI khanaBookOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KhanaBook SaaS API")
                        .description("Multi-Tenant SaaS backend for KhanaBook Lite")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, createAPIKeyScheme()));
    }

    /**
     * Marks every non-public operation as requiring the bearer token, so Swagger UI shows the
     * lock icon (and the "Authorize" dialog) exactly on the endpoints that really need a JWT.
     * Applying it per operation instead of globally keeps the public endpoints — login, signup,
     * the Easebuzz webhooks — free of a misleading lock.
     */
    @Bean
    OpenApiCustomizer bearerSecurityCustomizer() {
        return openApi -> {
            addEasebuzzPayloadSchemas(openApi);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperations().forEach(operation -> {
                        if (!isPublic(path)) {
                            operation.addSecurityItem(
                                    new SecurityRequirement().addList(BEARER_SCHEME));
                        }
                        if (isEasebuzzPath(path)) {
                            replaceGenericPayloadSchemas(operation);
                        }
                    }));
        };
    }

    /**
     * Map-based payment endpoints are intentionally kept wire-compatible for old POS clients.
     * Springdoc cannot infer keys from a Map, so this adds documentation-only schemas to every
     * Easebuzz operation while leaving deserialization and response JSON untouched.
     */
    private static void addEasebuzzPayloadSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        openApi.getComponents().addSchemas("EasebuzzRequestPayload", easebuzzRequestSchema());
        openApi.getComponents().addSchemas("EasebuzzResponsePayload", easebuzzResponseSchema());
    }

    private static Schema<?> easebuzzRequestSchema() {
        return new ObjectSchema()
                .description("Fields used by Easebuzz payment, refund, settlement, routing and webhook operations. See the operation description for required fields.")
                .addProperty("restaurantId", new IntegerSchema().format("int64"))
                .addProperty("billId", new IntegerSchema().format("int64"))
                .addProperty("amount", new NumberSchema().description("Amount in INR"))
                .addProperty("refundAmount", new NumberSchema().description("Refund amount in INR"))
                .addProperty("reason", new StringSchema())
                .addProperty("customerName", new StringSchema())
                .addProperty("customerEmail", new StringSchema().format("email"))
                .addProperty("customerPhone", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("years", new IntegerSchema().description("FSSAI renewal duration, 1 through 5"))
                .addProperty("fssaiNumber", new StringSchema().description("14-digit FSSAI licence number"))
                .addProperty("otp", new StringSchema())
                .addProperty("txnid", new StringSchema().description("Easebuzz transaction ID"))
                .addProperty("merchantRequestId", new StringSchema())
                .addProperty("customerVpa", new StringSchema().description("Customer UPI VPA"))
                .addProperty("beneficiaryDetails", new ObjectSchema().additionalProperties(new StringSchema()))
                .addProperty("subMerchantId", new StringSchema())
                .addProperty("merchantEmail", new StringSchema().format("email"))
                .addProperty("eventType", new StringSchema())
                .addProperty("url", new StringSchema().format("uri"))
                .addProperty("intervalUnit", new StringSchema())
                .addProperty("intervalValue", new IntegerSchema())
                .addProperty("maxAttempts", new IntegerSchema())
                .addProperty("hash", new StringSchema().description("Easebuzz signature hash"))
                .addProperty("status", new StringSchema())
                .addProperty("event", new StringSchema())
                .addProperty("data", new ObjectSchema().description("Gateway event payload"));
    }

    private static Schema<?> easebuzzResponseSchema() {
        return new ObjectSchema()
                .description("Easebuzz operation result. Fields vary by operation; absent fields are not returned.")
                .addProperty("status", new StringSchema().description("success, failure, error, pending, or a gateway status"))
                .addProperty("message", new StringSchema())
                .addProperty("error", new StringSchema())
                .addProperty("transactionId", new StringSchema())
                .addProperty("txnid", new StringSchema())
                .addProperty("paymentUrl", new StringSchema().format("uri"))
                .addProperty("paymentLink", new StringSchema().format("uri"))
                .addProperty("subMerchantId", new StringSchema())
                .addProperty("subMerchantStatus", new StringSchema())
                .addProperty("kycStatus", new StringSchema())
                .addProperty("kycUrl", new StringSchema().format("uri"))
                .addProperty("refundId", new StringSchema())
                .addProperty("refundStatus", new StringSchema())
                .addProperty("amount", new NumberSchema())
                .addProperty("paidAmount", new NumberSchema())
                .addProperty("refundedAmount", new NumberSchema())
                .addProperty("remainingRefundableAmount", new NumberSchema())
                .addProperty("paymentStatus", new StringSchema())
                .addProperty("bankStatus", new StringSchema())
                .addProperty("data", new ObjectSchema().description("Gateway-specific result data"))
                .addProperty("success", new BooleanSchema());
    }

    private static void replaceGenericPayloadSchemas(io.swagger.v3.oas.models.Operation operation) {
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            operation.getRequestBody().getContent().forEach((mediaType, content) -> {
                if (isGenericObject(content.getSchema())) {
                    content.setSchema(ref("EasebuzzRequestPayload"));
                }
            });
        }
        if (operation.getResponses() != null) {
            operation.getResponses().forEach((code, response) -> {
                if (response.getContent() != null) {
                    response.getContent().forEach((mediaType, content) -> {
                        if (isGenericObject(content.getSchema())) {
                            content.setSchema(ref("EasebuzzResponsePayload"));
                        }
                    });
                }
            });
        }
    }

    private static boolean isGenericObject(Schema<?> schema) {
        return schema != null && "object".equals(schema.getType())
                && schema.get$ref() == null && (schema.getProperties() == null || schema.getProperties().isEmpty());
    }

    private static Schema<?> ref(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }

    private static boolean isEasebuzzPath(String path) {
        return path.startsWith("/payments/easebuzz")
                || path.startsWith("/admin/sub-merchants")
                || path.startsWith("/admin/payment-routing")
                || path.startsWith("/business/refunds")
                || path.startsWith("/business/settlements")
                || path.startsWith("/business/chargebacks");
    }

    static boolean isPublic(String path) {
        if (path == null) {
            return false;
        }
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(prefix ->
                path.equals(prefix) || path.startsWith(prefix));
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");
    }
}
