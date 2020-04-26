package com.waitfor.contentcenter.configuration;

import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.context.annotation.Configuration;
import ribbonconfiguration.RibbonConfiguration;
/*@Configuration
@RibbonClient(name="user-center",configuration = RibbonConfiguration.class)
public class UserCenterRibbonConfiguration {

}*/
//ribbon全局配置
@Configuration
@RibbonClients(defaultConfiguration = RibbonConfiguration.class)
public class UserCenterRibbonConfiguration {

}
