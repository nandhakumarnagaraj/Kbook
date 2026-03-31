package com.khanabook.saas.sync.dto.payload;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import com.khanabook.saas.entity.*;
import org.springframework.beans.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class SyncMapper {
    private static final Logger log = LoggerFactory.getLogger(SyncMapper.class);

    public static <S extends BaseSyncEntity, T> T map(S source, Class<T> targetClass) {
        if (source == null) return null;
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            
            if (source instanceof Category entity && target instanceof CategoryDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
            } else if (source instanceof MenuItem entity && target instanceof MenuItemDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setCategoryId(entity.getCategoryId());
                dto.setServerCategoryId(entity.getServerCategoryId());
            } else if (source instanceof ItemVariant entity && target instanceof ItemVariantDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setMenuItemId(entity.getMenuItemId());
                dto.setServerMenuItemId(entity.getServerMenuItemId());
            } else if (source instanceof Bill entity && target instanceof BillDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
            } else if (source instanceof BillItem entity && target instanceof BillItemDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setBillId(entity.getBillId());
                dto.setServerBillId(entity.getServerBillId());
                dto.setMenuItemId(entity.getMenuItemId());
                dto.setServerMenuItemId(entity.getServerMenuItemId());
                dto.setVariantId(entity.getVariantId());
                dto.setServerVariantId(entity.getServerVariantId());
            } else if (source instanceof BillPayment entity && target instanceof BillPaymentDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setBillId(entity.getBillId());
                dto.setServerBillId(entity.getServerBillId());
            } else if (source instanceof StockLog entity && target instanceof StockLogDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
                dto.setMenuItemId(entity.getMenuItemId());
                dto.setVariantId(entity.getVariantId());
                dto.setServerMenuItemId(entity.getServerMenuItemId());
                dto.setServerVariantId(entity.getServerVariantId());
            } else if (source instanceof RestaurantProfile entity && target instanceof RestaurantProfileDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
            } else if (source instanceof User entity && target instanceof UserDTO dto) {
                dto.setId(entity.getId());
                dto.setLocalId(entity.getLocalId());
                dto.setServerUpdatedAt(entity.getServerUpdatedAt());
            }

            return target;
        } catch (Exception e) {
            throw new RuntimeException("Failed to map Entity to DTO", e);
        }
    }

    public static <S extends BaseSyncEntity, T> List<T> mapList(List<S> sourceList, Class<T> targetClass) {
        if (sourceList == null) return new ArrayList<>();
        List<T> result = new ArrayList<>();
        for (S source : sourceList) {
            T mapped = map(source, targetClass);
            if (mapped != null) result.add(mapped);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <S, T extends BaseSyncEntity> T mapToEntity(S source, Class<T> targetClass) {
        if (source == null) return null;
        try {
            T target;
            if (targetClass.equals(Category.class)) target = (T) new Category();
            else if (targetClass.equals(MenuItem.class)) target = (T) new MenuItem();
            else if (targetClass.equals(ItemVariant.class)) target = (T) new ItemVariant();
            else if (targetClass.equals(Bill.class)) target = (T) new Bill();
            else if (targetClass.equals(BillItem.class)) target = (T) new BillItem();
            else if (targetClass.equals(BillPayment.class)) target = (T) new BillPayment();
            else if (targetClass.equals(StockLog.class)) target = (T) new StockLog();
            else if (targetClass.equals(RestaurantProfile.class)) target = (T) new RestaurantProfile();
            else if (targetClass.equals(User.class)) target = (T) new User();
            else target = targetClass.getDeclaredConstructor().newInstance();

            BeanUtils.copyProperties(source, target);

            if (source instanceof CategoryDTO dto) {
                Category entity = (Category) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
            } else if (source instanceof MenuItemDTO dto) {
                MenuItem entity = (MenuItem) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setCategoryId(dto.getCategoryId());
                entity.setServerCategoryId(dto.getServerCategoryId());
            } else if (source instanceof ItemVariantDTO dto) {
                ItemVariant entity = (ItemVariant) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setMenuItemId(dto.getMenuItemId());
                entity.setServerMenuItemId(dto.getServerMenuItemId());
            } else if (source instanceof BillDTO dto) {
                Bill entity = (Bill) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
            } else if (source instanceof BillItemDTO dto) {
                BillItem entity = (BillItem) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setBillId(dto.getBillId());
                entity.setServerBillId(dto.getServerBillId());
                entity.setMenuItemId(dto.getMenuItemId());
                entity.setServerMenuItemId(dto.getServerMenuItemId());
                entity.setVariantId(dto.getVariantId());
                entity.setServerVariantId(dto.getServerVariantId());
            } else if (source instanceof BillPaymentDTO dto) {
                BillPayment entity = (BillPayment) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setBillId(dto.getBillId());
                entity.setServerBillId(dto.getServerBillId());
            } else if (source instanceof StockLogDTO dto) {
                StockLog entity = (StockLog) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setMenuItemId(dto.getMenuItemId());
                entity.setVariantId(dto.getVariantId());
                entity.setServerMenuItemId(dto.getServerMenuItemId());
                entity.setServerVariantId(dto.getServerVariantId());
            } else if (source instanceof RestaurantProfileDTO dto) {
                RestaurantProfile entity = (RestaurantProfile) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                entity.setDailyOrderCounter(dto.getDailyOrderCounter());
                entity.setLifetimeOrderCounter(dto.getLifetimeOrderCounter());
                entity.setLastResetDate(dto.getLastResetDate());
                // Copy all other fields via BeanUtils is already handled above
            } else if (source instanceof UserDTO dto) {
                User entity = (User) target;
                entity.setId(dto.getId());
                entity.setLocalId(dto.getLocalId());
                entity.setServerUpdatedAt(dto.getServerUpdatedAt());
                if (dto.getIsActive() == null) {
                    entity.setIsActive(true);
                }
            }

            return target;
        } catch (Exception e) {
            log.error("Failed to map DTO to Entity: {} - Class: {}", e.getMessage(), source.getClass().getName());
            throw new RuntimeException("Failed to map DTO to Entity", e);
        }
    }

    public static <S, T extends BaseSyncEntity> List<T> mapToEntityList(List<S> sourceList, Class<T> targetClass) {
        if (sourceList == null) return new ArrayList<>();
        List<T> result = new ArrayList<>();
        for (S source : sourceList) {
            T mapped = mapToEntity(source, targetClass);
            if (mapped != null) result.add(mapped);
        }
        return result;
    }
}
