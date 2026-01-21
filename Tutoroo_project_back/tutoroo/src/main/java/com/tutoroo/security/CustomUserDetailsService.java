package com.tutoroo.security;

import com.tutoroo.entity.UserEntity;
import com.tutoroo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    /**
     * username(아이디)으로 사용자 정보를 조회하여 시큐리티 컨텍스트에 저장할 객체 반환
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        UserEntity user = userMapper.findByUsername(username);

        if (user == null) {
            log.warn("로그인 실패 - 존재하지 않는 사용자: {}", username);
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
        }

        // [핵심] 상태 확인 (탈퇴하거나 정지된 계정 로그인 차단)
        String status = (user.getStatus() != null) ? user.getStatus().toUpperCase() : "ACTIVE";

        if (!"ACTIVE".equals(status)) {
            log.warn("로그인 차단 - 비활성화된 계정: {} 상태: {}", username, status);
            throw new UsernameNotFoundException("사용할 수 없는 계정입니다. (상태: " + status + ")");
        }

        return new CustomUserDetails(user);
    }
}