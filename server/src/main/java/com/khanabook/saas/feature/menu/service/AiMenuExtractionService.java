package com.khanabook.saas.feature.menu.service;

import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text-Only AI Menu Extraction Engine.
 * Extracts categories, menu items, pricing, variants, and veg/non-veg flags
 * from raw OCR text without requiring expensive multimodal vision models.
 * Falls back to an offline heuristic parser when no LLM API key is configured.
 */
@Service
@RequiredArgsConstructor
public class AiMenuExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AiMenuExtractionService.class);

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final PushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:₹|rs\\.?|inr)?\\s*(\\d+(?:\\.\\d{1,2})?)(?:/-)?", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NON_VEG_KEYWORDS = Set.of(
            "chicken", "murgh", "murg", "mutton", "lamb", "fish", "prawn", "prawns",
            "egg", "anda", "meat", "pork", "beef", "crab", "keema", "gosht"
    );
    private static final Set<String> VEG_EXPLICIT_KEYWORDS = Set.of(
            "paneer", "veg", "vegetarian", "mushroom", "mashroom", "aloo", "gobi", "dal", "soya", "palak"
    );
    private static final Set<String> NOISE_KEYWORDS = Set.of(
            "menu", "welcome", "gst", "tax", "fssai", "address", "phone", "contact",
            "zomato", "swiggy", "www.", "thank", "visit", "again", "serving", "since"
    );

    // DTO records
    public record ExtractedVariant(String name, BigDecimal price) {}
    public record ExtractedItem(String name, String foodType, BigDecimal basePrice, String description, List<ExtractedVariant> variants) {}
    public record ExtractedCategory(String name, List<ExtractedItem> items) {}
    public record ExtractedMenuResponse(List<ExtractedCategory> categories, int totalItemsExtracted) {}
    public record BulkImportResult(int categoriesCreated, int itemsCreated, int variantsCreated) {}

    /**
     * Extract menu items from raw text.
     * Uses text LLM if configured; otherwise uses smart heuristic text parser.
     */
    public ExtractedMenuResponse extractMenuFromText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ExtractedMenuResponse(Collections.emptyList(), 0);
        }

        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                ExtractedMenuResponse aiResponse = callGeminiTextExtraction(rawText);
                if (aiResponse != null && aiResponse.totalItemsExtracted() > 0) {
                    return aiResponse;
                }
            } catch (Exception e) {
                log.warn("Gemini text extraction failed; falling back to heuristic parser: {}", e.getMessage());
            }
        }

        return parseWithHeuristics(rawText);
    }

    private ExtractedMenuResponse callGeminiTextExtraction(String rawText) throws Exception {
        String prompt = "You are an expert restaurant menu digitizer for Indian restaurants. "
                + "Parse the following raw menu text into structured categories, menu items, prices, variants (e.g. Half/Full), "
                + "and foodType ('veg' or 'non-veg').\n"
                + "Strict requirements:\n"
                + "1. Correct typos in dish names (e.g. 'Panner' -> 'Paneer', 'Biryani', 'Roti').\n"
                + "2. Identify Veg vs Non-Veg accurately based on ingredients.\n"
                + "3. Output ONLY a valid JSON object matching this exact schema (no markdown, no backticks, no extra text):\n"
                + "{\n"
                + "  \"categories\": [\n"
                + "    {\n"
                + "      \"name\": \"Starters\",\n"
                + "      \"items\": [\n"
                + "        {\n"
                + "          \"name\": \"Paneer Tikka\",\n"
                + "          \"foodType\": \"veg\",\n"
                + "          \"basePrice\": 220.00,\n"
                + "          \"description\": \"\",\n"
                + "          \"variants\": [{\"name\": \"Half\", \"price\": 120.00}, {\"name\": \"Full\", \"price\": 220.00}]\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n\n"
                + "Menu Text to parse:\n" + rawText;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(20000);
        RestTemplate restTemplate = new RestTemplate(factory);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String responseJson = restTemplate.postForObject(url, entity, String.class);
        if (responseJson == null || responseJson.isBlank()) return null;

        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode candidate = root.path("candidates").path(0);
        String text = candidate.path("content").path("parts").path(0).path("text").asText();
        if (text.isBlank()) return null;

        return parseJsonToMenuResponse(text);
    }

    private ExtractedMenuResponse parseJsonToMenuResponse(String jsonText) {
        try {
            String cleaned = jsonText.trim();
            if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
            if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
            cleaned = cleaned.trim();

            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode categoriesNode = root.path("categories");
            if (!categoriesNode.isArray()) return null;

            List<ExtractedCategory> categories = new ArrayList<>();
            int totalItems = 0;

            for (JsonNode catNode : categoriesNode) {
                String catName = catNode.path("name").asText("General").trim();
                JsonNode itemsNode = catNode.path("items");
                List<ExtractedItem> items = new ArrayList<>();

                if (itemsNode.isArray()) {
                    for (JsonNode itemNode : itemsNode) {
                        String name = itemNode.path("name").asText().trim();
                        if (name.isBlank()) continue;

                        String foodType = itemNode.path("foodType").asText("veg").toLowerCase();
                        BigDecimal basePrice = new BigDecimal(itemNode.path("basePrice").asText("0.00"));
                        String description = itemNode.path("description").asText("");

                        List<ExtractedVariant> variants = new ArrayList<>();
                        JsonNode varNode = itemNode.path("variants");
                        if (varNode.isArray()) {
                            for (JsonNode v : varNode) {
                                String vName = v.path("name").asText();
                                BigDecimal vPrice = new BigDecimal(v.path("price").asText("0.00"));
                                if (!vName.isBlank()) {
                                    variants.add(new ExtractedVariant(vName, vPrice));
                                }
                            }
                        }

                        items.add(new ExtractedItem(name, foodType, basePrice, description, variants));
                        totalItems++;
                    }
                }

                if (!items.isEmpty()) {
                    categories.add(new ExtractedCategory(catName, items));
                }
            }

            return new ExtractedMenuResponse(categories, totalItems);
        } catch (Exception e) {
            log.warn("Failed to deserialize LLM JSON output: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Resilient offline heuristic parser that groups lines into categories and items with pricing.
     */
    public ExtractedMenuResponse parseWithHeuristics(String rawText) {
        String[] lines = rawText.split("\\r?\\n");
        Map<String, List<ExtractedItem>> categoryMap = new LinkedHashMap<>();
        String currentCategory = "Main Menu";

        int totalCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() < 2) continue;

            String lower = trimmed.toLowerCase();
            if (NOISE_KEYWORDS.stream().anyMatch(lower::contains) && !trimmed.matches(".*\\d+.*")) {
                continue;
            }

            // Category detection: short line, uppercase or category indicator, no price
            boolean isPricePresent = PRICE_PATTERN.matcher(trimmed).find();
            if (!isPricePresent && (trimmed.length() <= 30 && (trimmed.equals(trimmed.toUpperCase()) || isCategoryHeader(trimmed)))) {
                currentCategory = capitalizeWords(trimmed.replaceAll("[^a-zA-Z\\s]", "").trim());
                if (currentCategory.isBlank()) currentCategory = "General";
                continue;
            }

            // Item detection with price
            Matcher matcher = PRICE_PATTERN.matcher(trimmed);
            List<BigDecimal> foundPrices = new ArrayList<>();
            int firstPriceStart = -1;

            while (matcher.find()) {
                if (firstPriceStart == -1) firstPriceStart = matcher.start();
                try {
                    foundPrices.add(new BigDecimal(matcher.group(1)));
                } catch (Exception ignored) {}
            }

            if (!foundPrices.isEmpty() && firstPriceStart > 0) {
                String itemName = trimmed.substring(0, firstPriceStart).replaceAll("[^a-zA-Z0-9\\s()'-]", " ").trim();
                itemName = itemName.replaceAll("\\s+", " ");
                itemName = cleanItemName(itemName);
                if (itemName.length() >= 2) {
                    String foodType = determineFoodType(itemName);
                    BigDecimal basePrice = foundPrices.get(foundPrices.size() - 1);
                    List<ExtractedVariant> variants = new ArrayList<>();

                    if (foundPrices.size() == 2) {
                        variants.add(new ExtractedVariant("Half", foundPrices.get(0)));
                        variants.add(new ExtractedVariant("Full", foundPrices.get(1)));
                        basePrice = foundPrices.get(1);
                    }

                    ExtractedItem item = new ExtractedItem(capitalizeWords(itemName), foodType, basePrice, "", variants);
                    categoryMap.computeIfAbsent(currentCategory, k -> new ArrayList<>()).add(item);
                    totalCount++;
                }
            }
        }

        List<ExtractedCategory> categories = new ArrayList<>();
        for (Map.Entry<String, List<ExtractedItem>> entry : categoryMap.entrySet()) {
            categories.add(new ExtractedCategory(entry.getKey(), entry.getValue()));
        }

        return new ExtractedMenuResponse(categories, totalCount);
    }

    /**
     * Bulk save extracted categories and items transactionally.
     */
    @Transactional
    public BulkImportResult bulkImportMenu(Long restaurantId, ExtractedMenuResponse payload) {
        int categoriesCreated = 0;
        int itemsCreated = 0;
        int variantsCreated = 0;
        long now = System.currentTimeMillis();

        for (ExtractedCategory catDto : payload.categories()) {
            String catName = catDto.name().trim();
            if (catName.isBlank()) continue;

            // Find existing category or create new
            Category category = categoryRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(restaurantId, 0L).stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()) && c.getName().equalsIgnoreCase(catName))
                    .findFirst()
                    .orElse(null);

            if (category == null) {
                category = new Category();
                category.setName(catName);
                category.setRestaurantId(restaurantId);
                category.setDeviceId("ai-import");
                category.setLocalId(System.currentTimeMillis() + categoriesCreated);
                category.setIsVeg(catDto.items().stream().allMatch(i -> "veg".equalsIgnoreCase(i.foodType())));
                category.setIsActive(true);
                category.setCreatedAt(now);
                category.setUpdatedAt(now);
                category.setServerUpdatedAt(now);
                category = categoryRepository.save(category);
                categoriesCreated++;
            }

            for (ExtractedItem itemDto : catDto.items()) {
                String itemName = itemDto.name().trim();
                if (itemName.isBlank()) continue;

                MenuItem item = new MenuItem();
                item.setName(itemName);
                item.setRestaurantId(restaurantId);
                item.setCategoryId(category.getId());
                item.setServerCategoryId(category.getId());
                item.setFoodType(itemDto.foodType() != null ? itemDto.foodType() : "veg");
                item.setBasePrice(itemDto.basePrice() != null ? itemDto.basePrice() : BigDecimal.ZERO);
                item.setDescription(itemDto.description());
                item.setIsAvailable(true);
                item.setDeviceId("ai-import");
                item.setLocalId(System.currentTimeMillis() + itemsCreated);
                item.setCreatedAt(now);
                item.setUpdatedAt(now);
                item.setServerUpdatedAt(now);
                MenuItem savedItem = menuItemRepository.save(item);
                itemsCreated++;

                if (itemDto.variants() != null && !itemDto.variants().isEmpty()) {
                    int sort = 0;
                    for (ExtractedVariant varDto : itemDto.variants()) {
                        ItemVariant variant = new ItemVariant();
                        variant.setRestaurantId(restaurantId);
                        variant.setMenuItemId(savedItem.getId());
                        variant.setServerMenuItemId(savedItem.getId());
                        variant.setVariantName(varDto.name());
                        variant.setPrice(varDto.price());
                        variant.setIsAvailable(true);
                        variant.setSortOrder(sort++);
                        variant.setDeviceId("ai-import");
                        variant.setLocalId(System.currentTimeMillis() + variantsCreated);
                        variant.setCreatedAt(now);
                        variant.setUpdatedAt(now);
                        variant.setServerUpdatedAt(now);
                        itemVariantRepository.save(variant);
                        variantsCreated++;
                    }
                }
            }
        }

        if (itemsCreated > 0) {
            pushNotificationService.pushSyncNow(restaurantId);
        }

        log.info("Bulk menu import completed for restaurant {}: {} categories, {} items, {} variants",
                restaurantId, categoriesCreated, itemsCreated, variantsCreated);
        return new BulkImportResult(categoriesCreated, itemsCreated, variantsCreated);
    }

    private static final List<String> VARIANT_SUFFIXES = List.of("half", "full", "regular", "large", "small", "quarter", "qtr");

    private String cleanItemName(String rawName) {
        String name = rawName.trim();
        for (String suffix : VARIANT_SUFFIXES) {
            if (name.toLowerCase().endsWith(" " + suffix)) {
                name = name.substring(0, name.length() - suffix.length() - 1).trim();
            }
        }
        return name;
    }

    private String determineFoodType(String itemName) {
        String lower = itemName.toLowerCase();
        boolean hasNonVeg = NON_VEG_KEYWORDS.stream().anyMatch(lower::contains);
        boolean hasVeg = VEG_EXPLICIT_KEYWORDS.stream().anyMatch(lower::contains);
        if (hasVeg && !hasNonVeg) {
            return "veg";
        }
        return hasNonVeg ? "non-veg" : "veg";
    }

    private boolean isCategoryHeader(String text) {
        String lower = text.toLowerCase();
        return lower.endsWith("s") || lower.contains("special") || lower.contains("combo")
                || lower.contains("curry") || lower.contains("bread") || lower.contains("rice");
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String word : input.split("\\s+")) {
            if (!word.isBlank()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
