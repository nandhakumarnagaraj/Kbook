package com.khanabook.saas.feature.staff.data;

public class RoleTemplate {
    private Long id;
    private Long restaurantId;
    private String name;
    private String description;
    private String permissions;
    private Boolean isDefault = false;
    private Long createdBy;
    private Long createdAt;
    private Long updatedAt;

    public RoleTemplate() {}

    public RoleTemplate(Long restaurantId, String name, String description, String permissions, Long createdBy) {
        this.restaurantId = restaurantId;
        this.name = name;
        this.description = description;
        this.permissions = permissions;
        this.createdBy = createdBy;
        this.isDefault = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
