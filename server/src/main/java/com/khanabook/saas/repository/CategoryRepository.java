package com.khanabook.saas.repository;

import com.khanabook.saas.entity.Category;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends SyncRepository<Category, Long> {

}
