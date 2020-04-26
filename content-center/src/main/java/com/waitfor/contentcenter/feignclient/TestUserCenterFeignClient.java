package com.waitfor.contentcenter.feignclient;

import com.waitfor.contentcenter.domain.dto.user.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

//@FeignClient(name = "user-center", configuration = UserCenterFeignConfiguration.class)
@FeignClient(name = "user-center")
public interface TestUserCenterFeignClient {
    /**
     * Feign声明式的HTTP客户端（只要声明一个接口，
     * Feign通过你定义的接口自动的给你构造请求的目标地址，并帮助你请求）
     * http://user-center/users/{id}
     * @param userDTO
     * @return
     */
    @GetMapping("/q")
    public UserDTO query(@SpringQueryMap UserDTO userDTO);
}
