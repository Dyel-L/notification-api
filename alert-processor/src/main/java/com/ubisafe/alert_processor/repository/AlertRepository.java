package com.ubisafe.alert_processor.repository;

import com.ubisafe.alert_processor.domain.AlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<AlertEntity, String> {
}

