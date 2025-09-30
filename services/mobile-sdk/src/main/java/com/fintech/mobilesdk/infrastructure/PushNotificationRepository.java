package com.fintech.mobilesdk.infrastructure;

import com.fintech.mobilesdk.domain.PushNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PushNotificationRepository extends JpaRepository<PushNotification, UUID> {
}