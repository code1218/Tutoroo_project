package com.tutoroo.service;

import com.tutoroo.dto.AuthDTO;
import com.tutoroo.entity.MembershipTier;
import com.tutoroo.entity.UserEntity;
import com.tutoroo.exception.ErrorCode;
import com.tutoroo.exception.TutorooException;
import com.tutoroo.jwt.JwtTokenProvider; // [수정] 패키지 경로 변경됨
import com.tutoroo.mapper.UserMapper;
import com.tutoroo.util.FileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final FileStore fileStore;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuthenticationManagerBuilder authenticationManagerBuilder; // [추가] 정석적인 로그인 검증을 위해 필요

    // --- [1] 로그인 ---
    @Transactional
    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest request) {
        // 1. Login ID/PW 를 기반으로 AuthenticationToken 생성
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(request.username(), request.password());

        // 2. 실제 검증 (UserDetailsService -> PasswordEncoder)
        // 기존 코드처럼 수동으로 passwordEncoder.matches를 써도 되지만, AuthenticationManager를 쓰는 것이 보안 컨텍스트상 안전합니다.
        // 하지만 님의 기존 로직(수동 체크 + 상태 확인)을 유지하면서 TokenProvider 메서드만 맞추겠습니다.

        UserEntity user = userMapper.findByUsername(request.username());

        // 유저 존재 여부 및 비밀번호 확인
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new TutorooException(ErrorCode.INVALID_PASSWORD);
        }

        // 계정 상태 확인
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new TutorooException("비활성화된 계정입니다. 관리자에게 문의하세요.", ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // 3. 토큰 발급 (Authentication 객체 생성 후 전달)
        // JwtTokenProvider가 변경되었으므로 createAccessToken -> generateAccessToken으로 변경하고, 매개변수로 Authentication을 받습니다.
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, Collections.singletonList(new SimpleGrantedAuthority(user.getRole()))
        );

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        // 4. 리프레시 토큰 Redis 저장 (유효기간 7일 ~ 14일)
        redisTemplate.opsForValue().set(
                "RT:" + user.getUsername(),
                refreshToken,
                14,
                TimeUnit.DAYS
        );

        return AuthDTO.LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .isNewUser(false)
                .build();
    }

    // --- [2] 일반 회원가입 ---
    @Transactional
    public void register(AuthDTO.JoinRequest request, MultipartFile profileImage) {
        // 1. 중복 확인
        if (userMapper.findByUsername(request.username()) != null) {
            throw new TutorooException(ErrorCode.DUPLICATE_ID);
        }

        // 2. 프로필 이미지 저장 (FileStore 기존 로직 유지)
        String profileImageUrl = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                String originalFilename = profileImage.getOriginalFilename();
                String ext = (originalFilename != null && originalFilename.contains("."))
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";

                // [중요] 기존 FileStore가 byte[]를 받도록 되어 있으므로 유지
                profileImageUrl = fileStore.storeFile(profileImage.getBytes(), ext);
            } catch (Exception e) {
                log.error("프로필 이미지 저장 실패: {}", e.getMessage());
            }
        }

        // 3. 엔티티 생성
        UserEntity newUser = UserEntity.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .gender(request.gender())
                .age(request.age())
                .phone(request.phone())
                .email(request.email())
                .parentPhone(request.parentPhone())
                .profileImage(profileImageUrl)
                .role("ROLE_USER")
                .status("ACTIVE")
                .membershipTier(MembershipTier.BASIC)
                .totalPoint(0)
                .pointBalance(0)
                .level(1)
                .exp(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userMapper.save(newUser);
    }

    // --- [3] 소셜 로그인 추가 정보 입력 ---
    @Transactional
    public AuthDTO.LoginResponse completeSocialSignup(String username, AuthDTO.SocialSignupRequest request, MultipartFile profileImage) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null) {
            throw new TutorooException(ErrorCode.USER_NOT_FOUND);
        }

        // 1. 추가 정보 업데이트
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setAge(request.age());
        user.setGender(request.gender());
        user.setParentPhone(request.parentPhone());
        user.setRole("ROLE_USER"); // 정회원 전환

        // 2. 프로필 이미지 업데이트
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                String originalFilename = profileImage.getOriginalFilename();
                String ext = (originalFilename != null && originalFilename.contains("."))
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";

                String imageUrl = fileStore.storeFile(profileImage.getBytes(), ext);
                user.setProfileImage(imageUrl);
            } catch (Exception e) {
                log.error("소셜 가입 프로필 저장 실패: {}", e.getMessage());
            }
        }

        // [중요] 기존 Mapper 메서드명 유지 (update -> updateSocialUser)
        userMapper.updateSocialUser(user);

        // 3. 토큰 발급
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, Collections.singletonList(new SimpleGrantedAuthority(user.getRole()))
        );

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        redisTemplate.opsForValue().set("RT:" + user.getUsername(), refreshToken, 14, TimeUnit.DAYS);

        return AuthDTO.LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .isNewUser(false)
                .build();
    }

    // --- [4] 중복 확인 ---
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        return userMapper.findByUsername(username) == null;
    }

    // --- [5] 아이디 찾기 ---
    @Transactional(readOnly = true)
    public AuthDTO.AccountInfoResponse findUsername(AuthDTO.FindIdRequest request) {
        // [중요] 기존 Mapper 메서드명 유지
        UserEntity user = userMapper.findByNameAndEmailAndPhone(
                request.name(), request.email(), request.phone());

        if (user == null) {
            throw new TutorooException(ErrorCode.USER_NOT_FOUND);
        }

        return new AuthDTO.AccountInfoResponse(user.getUsername(), "회원님의 아이디를 찾았습니다.", true);
    }

    // --- [6] 임시 비밀번호 발송 ---
    @Transactional
    public void sendTemporaryPassword(AuthDTO.ResetPasswordRequest request) {
        UserEntity user = userMapper.findByUsernameAndEmail(request.username(), request.email());
        if (user == null) throw new TutorooException(ErrorCode.USER_NOT_FOUND);

        String tempPw = UUID.randomUUID().toString().substring(0, 8);
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(tempPw));

        // 기존 EmailService 사용
        emailService.sendTemporaryPassword(user.getEmail(), tempPw);
    }

    // --- [7] 이메일 인증 요청 ---
    public void requestEmailVerification(String email) {
        emailService.sendVerificationCode(email);
    }

    // --- [8] 이메일 인증 검증 ---
    public boolean verifyEmailCode(String email, String code) {
        return emailService.verifyCode(email, code);
    }

    // --- [9] 토큰 재발급 ---
    @Transactional
    public AuthDTO.LoginResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new TutorooException("유효하지 않은 리프레시 토큰입니다.", ErrorCode.INVALID_AUTH_CODE);
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        String savedRt = redisTemplate.opsForValue().get("RT:" + username);

        if (savedRt == null || !savedRt.equals(refreshToken)) {
            throw new TutorooException("리프레시 토큰이 만료되었거나 일치하지 않습니다.", ErrorCode.INVALID_AUTH_CODE);
        }

        UserEntity user = userMapper.findByUsername(username);
        if (user == null) throw new TutorooException(ErrorCode.USER_NOT_FOUND);

        // 새 토큰 발급
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, Collections.singletonList(new SimpleGrantedAuthority(user.getRole()))
        );

        String newAccessToken = jwtTokenProvider.generateAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        redisTemplate.opsForValue().set("RT:" + username, newRefreshToken, 14, TimeUnit.DAYS);

        return AuthDTO.LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .username(username)
                .name(user.getName())
                .role(user.getRole())
                .isNewUser(false)
                .build();
    }

    // --- [10] 로그아웃 (New) ---
    public void logout(String accessToken, String refreshToken) {
        if (jwtTokenProvider.validateToken(accessToken)) {
            redisTemplate.opsForValue().set(
                    "BL:" + accessToken,
                    "logout",
                    30,
                    TimeUnit.MINUTES
            );
        }
        if (refreshToken != null) {
            try {
                String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
                if (username != null) {
                    redisTemplate.delete("RT:" + username);
                }
            } catch (Exception e) {
                // 토큰 만료 등 파싱 에러 무시
            }
        }
    }
}