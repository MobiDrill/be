package com.mobidrill.backend.domain.user.repository;

import com.mobidrill.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 사용자를 조회한다.
     * @param email : 조회할 이메일
     * @return : 조회된 사용자
     */
    Optional<User> findByEmail(String email);

    /**
     * 동일 이메일의 사용자 존재 여부를 확인한다.
     * @param email : 중복 여부를 확인할 이메일
     * @return : 사용자 존재 여부
     */
    boolean existsByEmail(String email);
}
