package com.apigen.repository;

import com.apigen.domain.Connection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    Optional<Connection> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
