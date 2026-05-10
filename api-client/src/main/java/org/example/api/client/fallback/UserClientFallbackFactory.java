package org.example.api.client.fallback;

import lombok.extern.slf4j.Slf4j;
import org.example.api.client.UserClient;
import org.example.api.po.User;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        log.error("user-service 调用失败，触发 Sentinel 降级", cause);
        return new UserClient() {
            @Override
            public User findByUsername(String username) {
                return null;
            }

            @Override
            public User findByEmail(String email) {
                return null;
            }

            @Override
            public void save(User user) {
            }

            @Override
            public User getById(Long id) {
                return null;
            }
        };
    }
}
