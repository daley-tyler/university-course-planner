package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.plans.AuthEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthEventLogRepository extends JpaRepository<AuthEventLog, Long> {
}
