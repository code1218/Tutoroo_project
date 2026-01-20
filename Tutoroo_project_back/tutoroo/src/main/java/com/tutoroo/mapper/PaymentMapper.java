package com.tutoroo.mapper;

import com.tutoroo.entity.PaymentEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface PaymentMapper {
    void save(PaymentEntity payment);
    PaymentEntity findByImpUid(String impUid);

    // [New] 유저별 결제 내역 조회
    List<PaymentEntity> findAllByUserId(Long userId);
}