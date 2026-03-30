package com.khanabook.saas.repository;

import com.khanabook.saas.entity.User;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends SyncRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByLoginId(String loginId);

	Optional<User> findByWhatsappNumber(String whatsappNumber);

	Optional<User> findByGoogleEmail(String googleEmail);

	boolean existsByEmail(String email);

	boolean existsByLoginId(String loginId);

	boolean existsByWhatsappNumber(String whatsappNumber);}
