package com.tutoroo.mapper;

import com.tutoroo.entity.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NotificationMapper {
    void save(NotificationEntity notification);
    List<NotificationEntity> findAllByUserId(Long userId);
    long countUnreadByUserId(Long userId);
    void markAsRead(@Param("userId") Long userId, @Param("notificationId") Long notificationId);
    void markAllAsRead(Long userId);
}