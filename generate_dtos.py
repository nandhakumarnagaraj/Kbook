import os
import re

entities = ['RestaurantProfile', 'User', 'Category', 'MenuItem', 'ItemVariant', 'StockLog', 'Bill', 'BillItem', 'BillPayment']
entity_dir = 'server/src/main/java/com/khanabook/saas/entity'
dto_dir = 'server/src/main/java/com/khanabook/saas/sync/dto/payload'
os.makedirs(dto_dir, exist_ok=True)

for entity in entities:
    with open(os.path.join(entity_dir, f"{entity}.java"), 'r') as f:
        content = f.read()
    
    # Extract fields (improved to catch the type and name more accurately)
    # This regex is simple but should work for this project's style.
    # It avoids capturing @Column and other annotations as part of the type.
    fields_matches = re.findall(r'private\s+([A-Za-z0-9_<>.]+)\s+([a-zA-Z0-9_]+)', content)
    
    dto_content = f"""package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.khanabook.saas.entity.*;
import java.math.BigDecimal;

@Data
public class {entity}DTO {{
    @JsonProperty("serverId")
    private Long id;

    @JsonProperty("localId")
    private Long localId;

    private String deviceId;
    private Long restaurantId;
    private Long updatedAt;
    private Boolean isDeleted;
    private Long serverUpdatedAt;
    private Long createdAt;

"""
    for t, n in fields_matches:
        # Skip base fields (already added manually for better control)
        if n in ['id', 'localId', 'deviceId', 'restaurantId', 'updatedAt', 'isDeleted', 'serverUpdatedAt', 'createdAt']:
            continue
        
        # Security: Ignore sensitive fields in DTO
        if n in ['passwordHash', 'pinHash']:
            dto_content += f"    @JsonIgnore\n"
        
        dto_content += f"    private {t} {n};\n"
        
    dto_content += "}\n"
    
    with open(os.path.join(dto_dir, f"{entity}DTO.java"), 'w') as f:
        f.write(dto_content)

print("DTOs updated with imports and JSON annotations.")
