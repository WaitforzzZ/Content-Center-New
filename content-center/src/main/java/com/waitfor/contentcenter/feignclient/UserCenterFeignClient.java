package com.waitfor.contentcenter.feignclient;

import com.waitfor.contentcenter.domain.dto.user.UserDTO;
import com.waitfor.contentcenter.feignclient.fallback.UserCenterFeignClientFallback;
import com.waitfor.contentcenter.feignclient.fallbackFactory.UserCenterFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

//@FeignClient(name = "user-center", configuration = UserCenterFeignConfiguration.class)
@FeignClient(name = "user-center",
        // fallback = UserCenterFeignClientFallback.class,
        fallbackFactory = UserCenterFeignClientFallbackFactory.class// 功能比fallback强， 可以拿到异常
)
public interface UserCenterFeignClient {
    /**
     * Feign声明式的HTTP客户端（只要声明一个接口，
     * Feign通过你定义的接口自动的给你构造请求的目标地址，并帮助你请求）
     * http://user-center/users/{id}
     * @param id
     * @return
     */
    @GetMapping("/users/{id}")
    UserDTO findById(@PathVariable Integer id);
}
