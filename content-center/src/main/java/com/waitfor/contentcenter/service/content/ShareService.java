package com.waitfor.contentcenter.service.content;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.waitfor.contentcenter.dao.messaging.RocketmqTransactionLogMapper;
import com.waitfor.contentcenter.domain.dto.content.ShareAuditDTO;
import com.waitfor.contentcenter.domain.dto.messaging.UserAddBonusMsgDTO;
import com.waitfor.contentcenter.domain.entity.messaging.RocketmqTransactionLog;
import com.waitfor.contentcenter.domain.enums.AuditStatusEnum;
import com.waitfor.contentcenter.feignclient.UserCenterFeignClient;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.alibaba.nacos.client.naming.utils.ThreadLocalRandom;
import com.waitfor.contentcenter.dao.content.ShareMapper;
import com.waitfor.contentcenter.domain.dto.content.ShareDTO;
import com.waitfor.contentcenter.domain.dto.user.UserDTO;
import com.waitfor.contentcenter.domain.entity.content.Share;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ShareService {

    private final ShareMapper shareMapper;
    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;
    private final UserCenterFeignClient userCenterFeignClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final RocketmqTransactionLogMapper rocketmqTransactionLogMapper;
    private final Source source;

    public ShareDTO findById(Integer id) {
        // 获取分享详情
        Share share = this.shareMapper.selectByPrimaryKey(id);
        // 发布人id
        Integer userId = share.getUserId();
        //用户中心所有实例的信息
/*		//----v1未接入ribbon
		List<ServiceInstance> instances = discoveryClient.getInstances("user-center");
		String targetUrl = instances.stream()
		    //数据变换
			.map(instance ->instance.getUri().toString()+ "/users/{id}")
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("当前没有实例"));
		List<String> targetUrls = instances.stream()
	    //数据变换
		.map(instance ->instance.getUri().toString()+ "/users/{id}")
		.collect(Collectors.toList());
		int i = ThreadLocalRandom.current().nextInt(targetUrls.size());
		String targetUrl = targetUrls.get(i);
		log.info("请求的目标地址： {}",targetUrl);
		//----未接入ribbon*/

/*        //----v2接入ribbon 存在的问题
        // 1. 代码不可读
        // 2. 复杂的url难以维护：https://user-center/s?ie={ie}&f={f}&rsv_bp=1&rsv_idx=1&tn=baidu&wd=a&rsv_pq=c86459bd002cfbaa&rsv_t=edb19hb%2BvO%2BTySu8dtmbl%2F9dCK%2FIgdyUX%2BxuFYuE0G08aHH5FkeP3n3BXxw&rqlang=cn&rsv_enter=1&rsv_sug3=1&rsv_sug2=0&inputT=611&rsv_sug4=611
        // 3. 难以相应需求的变化，变化很没有幸福感
        // 4. 编程体验不统一
        // 通过RestTemplate调用用户微服务的/user/{userId}??
        UserDTO userDTO = this.restTemplate.getForObject(
                "http://user-center/users/{userId}",
                UserDTO.class, userId);
        //----接入ribbon*/

        //----v3使用Feign重构v2版本的代码，解决上述问题
        UserDTO userDTO = this.userCenterFeignClient.findById(userId);
        //----使用Feign重构代码

        //消息的装配
        ShareDTO shareDTO = new ShareDTO();
        BeanUtils.copyProperties(share, shareDTO);
        shareDTO.setWxNickname(userDTO.getWxNickname());
        return shareDTO;
    }

    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();
        // 用HTTP GET方法去请求，并且返回一个对象
		/*String forObject=restTemplate.getForObject(
				"http://localhost:8080/users/{id}",
				String.class,1);
		System.out.println(forObject);*/
        ResponseEntity<String> forEntity = restTemplate.getForEntity(
                "http://localhost:8080/users/{id}",
                String.class, 1);
        System.out.println(forEntity.getBody());
        //相比getForObject可以拿到http的状态码
        System.out.println(forEntity.getStatusCode());
    }

    /**
     * 版本1
     * @param id
     * @param auditDTO
     * @return
     */
   /* public Share auditById(Integer id, ShareAuditDTO auditDTO) {
        // 1. 查询share是否存在， 不存在或者当前的auditStatus ！= NOT_YET, 那么抛异常
        Share share = this.shareMapper.selectByPrimaryKey(id);
        if(share == null){
            throw new IllegalArgumentException("参数非法！ 该分享不存在!");
        }
        if(!Objects.equals("NOT_YET", share.getAuditStatus())){
            throw new IllegalArgumentException("参数非法！ 该分享已审核通过或审核不通过!");
        }

        // 2. 审核资源， 将状态设为PASS/REJECT
        share.setAuditStatus(auditDTO.getAuditStatusEnum().toString());
        this.shareMapper.updateByPrimaryKey(share);
        // 3. 如果是PASS， 那么发送消息给rocketmq， 让用户中心去消费， 并为发布人添加积分
        //异步执行
        //this.userCenterFeignClient.addBonus(id,500);
        this.rocketMQTemplate.convertAndSend("add-bonus",
                UserAddBonusMsgDTO.builder()
                        .userId(share.getUserId())
                        .bonus(50)
                        .build()
        );

        // 4. 把share写到缓存

        return share;
    }
}*/

    /**
     * 版本2
     * rocketmq分布式事务
     * @param id
     * @param auditDTO
     * @return
     */
    public Share auditById(Integer id, ShareAuditDTO auditDTO) {
        // 1. 查询share是否存在， 不存在或者当前的auditStatus ！= NOT_YET, 那么抛异常
        Share share = this.shareMapper.selectByPrimaryKey(id);
        if(share == null){
            throw new IllegalArgumentException("参数非法！ 该分享不存在!");
        }
        if(!Objects.equals("NOT_YET", share.getAuditStatus())){
            throw new IllegalArgumentException("参数非法！ 该分享已审核通过或审核不通过!");
        }
        // 3. 如果是PASS， 那么发送消息给rocketmq， 让用户中心去消费， 并为发布人添加积分
        if(AuditStatusEnum.PASS.equals(auditDTO.getAuditStatusEnum())){
            //发送半消息
            String transactionId = UUID.randomUUID().toString();
            // 使用stream重构rocketMQTemplate的代码
            this.source.output()
                    .send(MessageBuilder
                            .withPayload(
                                    UserAddBonusMsgDTO.builder()
                                            .userId(share.getUserId())
                                            .bonus(50)
                                            .build()
                            )
                            // header也有妙用...
                            .setHeader(RocketMQHeaders.TRANSACTION_ID, transactionId)
                            .setHeader("share_id", id)
                            .setHeader("dto", JSON.toJSONString(auditDTO))
                            .build()
                    );
           /* this.rocketMQTemplate.sendMessageInTransaction(
                    "tx-add-bonus-group",
                    "add-bonus",
                    MessageBuilder
                            .withPayload(
                                    UserAddBonusMsgDTO.builder()
                                            .userId(share.getUserId())
                                            .bonus(50)
                                            .build()
                            )
                            // header也有妙用...
                            .setHeader(RocketMQHeaders.TRANSACTION_ID, transactionId)
                            .setHeader("share_id", id)
                            .build(),
                    // arg有大用处
                    auditDTO
            );*/
        }
        else {
            this.auditById(id, auditDTO);
        }
        return share;
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditByIdInDB(Integer id, ShareAuditDTO auditDTO) {
        Share share = Share.builder()
                .id(id)
                .auditStatus(auditDTO.getAuditStatusEnum().toString())
                .reason(auditDTO.getReason())
                .build();
        this.shareMapper.updateByPrimaryKeySelective(share);
        // 4. 把share写到缓存
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditByIdWithRocketMqLog(Integer id, ShareAuditDTO auditDTO, String transactionId) {
        this.auditByIdInDB(id, auditDTO);

        this.rocketmqTransactionLogMapper.insertSelective(
                RocketmqTransactionLog.builder()
                        .transactionId(transactionId)
                        .log("审核分享...")
                        .build()
        );
    }
}