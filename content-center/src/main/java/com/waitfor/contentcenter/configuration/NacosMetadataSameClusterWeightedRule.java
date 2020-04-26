package com.waitfor.contentcenter.configuration;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.core.Balancer;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancerRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.alibaba.nacos.NacosDiscoveryProperties;
import org.springframework.cloud.alibaba.nacos.ribbon.NacosServer;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Nacos权重，ribbon自带的负载均衡算法不支持，自定义负载均衡算法
 */
@Slf4j
public class NacosMetadataSameClusterWeightedRule extends AbstractLoadBalancerRule {
    @Autowired
    private NacosDiscoveryProperties nacosDiscoveryProperties;

    @Override
    public void initWithNiwsConfig(IClientConfig iClientConfig) {
        //读取配置文件，并初始化NacosWeightedRule
    }

    @Override
    public Server choose(Object o) {
        try {
            // 负载均衡规则：优先选择同集群下，符合metadata的实例
            // 如果没有，就选择所有集群下，符合metadata的实例

            // 1. 查询所有实例 A
            // 2. 筛选元数据匹配的实例 B
            // 3. 筛选出同cluster下元数据匹配的实例 C
            // 4. 如果C为空，就用B
            // 5. 随机选择实例
            // 拿到配置文件的集群名称 BJ
            String clusterName = nacosDiscoveryProperties.getClusterName();
            String targetVersion = nacosDiscoveryProperties.getMetadata().get("target-version");
            //ribbon的入口
            BaseLoadBalancer loadBalancer = (BaseLoadBalancer) this.getLoadBalancer();
            log.info("lb = {}", loadBalancer);
            // 想要请求的微服务的名称
            String name = loadBalancer.getName();
            // 拿到服务发现的相关API
            NamingService namingService = nacosDiscoveryProperties.namingServiceInstance();
            // 1. 找到指定服务的所有实例 A
            List<Instance> instances = namingService.selectInstances(name, true);
            List<Instance> metadataMatchInstances = instances;

            // 如果配置了版本映射，那么只调用元数据匹配的实例
            if(StringUtils.isNotBlank(targetVersion)){
                metadataMatchInstances = instances.stream()
                        .filter(instance -> Objects.equals(targetVersion,instance.getMetadata().get("version")))
                        .collect(Collectors.toList());
                if(CollectionUtils.isEmpty(metadataMatchInstances)){
                    log.warn("未找到元数据匹配的目标实例！请检查配置。targetVersion = {}, instance = {}", targetVersion, instances);
                    return null;
                }
            }
            List<Instance> clusterMetadataMatchInstances = metadataMatchInstances;
            if(StringUtils.isNotBlank(clusterName)){
                clusterMetadataMatchInstances = clusterMetadataMatchInstances.stream()
                        .filter(instance -> Objects.equals(clusterName,instance.getClusterName()))
                        .collect(Collectors.toList());
                if(CollectionUtils.isEmpty(clusterMetadataMatchInstances)){
                    clusterMetadataMatchInstances = metadataMatchInstances;
                    log.warn("发生跨集群的调用, name = {}, clusterName = {}, instances = {}",
                            name,
                            clusterName,
                            instances
                    );
                }
            }
            // 4. 基于权重的负载均衡算法， 返回一个实例
            Instance instance = ExtendsBalancer.getHostByRandomWeight2(clusterMetadataMatchInstances);
            log.info("选择的实例是 port = {}, instance = {}",instance.getPort(),instance);
            return new NacosServer(instance);
        } catch (NacosException e) {
            log.error("发生异常了", e);
            return null;
        }

    }
}
