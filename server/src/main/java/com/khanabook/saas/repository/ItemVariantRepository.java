package com.khanabook.saas.repository;

import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemVariantRepository extends SyncRepository<ItemVariant, Long> {


}
