package com.waitfor.contentcenter;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.waitfor.contentcenter.dao.content.ShareMapper;
import com.waitfor.contentcenter.domain.dto.user.UserDTO;
import com.waitfor.contentcenter.domain.entity.content.Share;
import com.waitfor.contentcenter.feignclient.TestBaiduFeignClient;
import com.waitfor.contentcenter.feignclient.TestUserCenterFeignClient;
import com.waitfor.contentcenter.sentineltest.TestControllerBlockHandlerClass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TestController {

	private final ShareMapper shareMapper;
	private final DiscoveryClient discoveryClient;
	private final TestUserCenterFeignClient testUserCenterFeignClient;
	@GetMapping("/test")
	public List<Share> testInsert(){
		Share build = Share.builder()
			.auditStatus("s")
			.author("zl")
			.createTime(new Date())
			.updateTime(new Date())
			.build();
		this.shareMapper.insertSelective(build);
		List<Share> selectAll = this.shareMapper.selectAll();
		return selectAll;
	}

	/**
	 * 测试：服务发现， 证明内容中心总能找到用户中心
	 * @return 用户中心所有实例的地址信息
	 */
	@GetMapping("/test2")
	public List<ServiceInstance> getInstances(){
		// 查询指定服务的所有实例信息
		// consul/eureka/zookeeper...
		return this.discoveryClient.getInstances("user-center");
	}

	@GetMapping("/test-get")
	public UserDTO query(UserDTO userDTO){
		return this.testUserCenterFeignClient.query(userDTO);
	}

	@Autowired
	private TestBaiduFeignClient testBaiduFeignClient;
	@GetMapping("baidu")
	public String index(){
		return this.testBaiduFeignClient.index();
	}

	@Autowired
	private TestService testService;
	@GetMapping("test-a")
	public String testA(){
		this.testService.common();
		return "test-a";
	}

	@GetMapping("test-b")
	public String testB(){
		this.testService.common();
		return "test-b";
	}

	@GetMapping("test-host")
	@SentinelResource("hot")
	public String testHost(
			@RequestParam(required = false) String a,
			@RequestParam(required = false) String b
	){
		return a+" "+b;
	}

	@GetMapping("test-add-flow-rule")
	public String testHost(){
		this.initFlowQpsRule();
		return "success";
	}

	private void initFlowQpsRule() {
		List<FlowRule> rules = new ArrayList<>();
		FlowRule rule = new FlowRule("/shares/1");
		// set limit qps to 20
		rule.setCount(20);
		rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
		rule.setLimitApp("default");
		rules.add(rule);
		FlowRuleManager.loadRules(rules);
	}

	// 版本1
	@GetMapping("/test-sentinel-api")
	public String testSentinelAPI(
			@RequestParam(required = false) String a){
		String resourceName = "test-sentinel-api";
		// 实现调用来源
		ContextUtil.enter(resourceName,"test-wfw");
		// 定义一个sentinel保护的资源(让资源受到监控)， 资源的名称test-sentinel-api（可以随便写只要唯一）
		Entry entry = null;
		try {
			entry = SphU.entry(resourceName);
			// 被保护的业务逻辑
			if(StringUtils.isNotBlank(a)){
				throw new IllegalArgumentException("a不能为空");
			}
			return a;
		}
		// 如果被保护的资源被限流或者降级了， 就会抛BlockException
		catch (BlockException e) {
			log.warn("限流，或者降级了",e);
			return "限流，或者降级了";
		}
		catch (IllegalArgumentException e){
			// 统计IllegalArgumentException 【发生次数、发生占比...】
			Tracer.trace(e);//对我们想要的异常进行统计
			return "参数非法";
		}
		finally {
			if(entry != null) {
				// 退出entry
				entry.exit();
			}
			ContextUtil.exit();
		}

	}

	// 对版本1进行重构
	@GetMapping("/test-sentinel-resource")
	@SentinelResource(
			value = "test-sentinel-api",
			blockHandler = "block",
			blockHandlerClass = TestControllerBlockHandlerClass.class,
			fallback = "fallback"
	)// 注解方式不支持来源
	public String testSentinelResource(
			@RequestParam(required = false) String a){
		// 被保护的业务逻辑
		if(StringUtils.isNotBlank(a)){
			throw new IllegalArgumentException("a cannot be blank.");
		}
		return a;
	}

	/**
	 * 1.5处理降级
	 * sentinel 1.6 可以处理Throwable
	 * @param a
	 * @param e
	 * @return
	 */
	public String fallback(String a, BlockException e){
		return "限流，或者降级了 fallback";
	}

	@Autowired
	private RestTemplate restTemplate;

	@GetMapping("/test-rest-template-sentinel/{userId}")
	public UserDTO test(@PathVariable Integer userId){
		return this.restTemplate
				.getForObject(
						"http://user-center/users/{userId}}",
						UserDTO.class, userId);
	}

	/**
	 * RestTemplate实现Token传递
	 * @param userId
	 * @return
	 */
	@GetMapping("/tokenRelay/{userId}")
	public ResponseEntity<UserDTO> tokenRelay(@PathVariable Integer userId, HttpServletRequest request){
		String token = request.getHeader("X-Token");
		HttpHeaders headers = new HttpHeaders();
		headers.add("X-Token", token);

		return this.restTemplate
				.exchange(
						"http://user-center/users/{userId}",
						HttpMethod.GET,
						new HttpEntity<>(headers),
						UserDTO.class,
						userId
				);
	}

	/*@Autowired
	private Source source;
	@GetMapping("/test-stream")
	public String testStream(){
		this.source.output()
			.send(
					MessageBuilder
					.withPayload("消息体")
					.build()
			);
		return "success";
	}

	@Autowired
	private MySource mySource;
	@GetMapping("/test-stream-2")
	public String testStream2(){
		this.mySource.output()
				.send(
						MessageBuilder
								.withPayload("消息体")
								.build()
				);
		return "success";
	}*/
}
