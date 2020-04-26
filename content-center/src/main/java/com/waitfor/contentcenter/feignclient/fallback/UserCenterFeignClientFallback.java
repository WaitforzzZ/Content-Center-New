package com.waitfor.contentcenter.feignclient.fallback;

import com.waitfor.contentcenter.domain.dto.user.UserDTO;
import com.waitfor.contentcenter.feignclient.UserCenterFeignClient;
import org.springframework.stereotype.Component;

@Component
public class UserCenterFeignClientFallback implements UserCenterFeignClient {
    @Override
    public UserDTO findById(Integer id) {
        UserDTO userDTO = new UserDTO();
        userDTO.setWxNickname("流控/降级返回的用户");
        return userDTO;
    }
}
