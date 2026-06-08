package com.loopers.user.application;

import com.loopers.user.domain.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserReader {

    private final UserRepository userRepository;

    public void ensureExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
    }
}
