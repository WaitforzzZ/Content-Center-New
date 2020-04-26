package com.waitfor.usercenter;

import org.springframework.web.client.RestTemplate;

public class SentinelTest {
    public static void main1(String[] args) throws InterruptedException {
        RestTemplate restTemplate = new RestTemplate();
        for (int i = 0;i < 1000; i++) {
            String object = restTemplate.getForObject("http://localhost:8080/actuator/sentinel", String.class);
            Thread.sleep(500);
        }
    }

    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();
        for (int i = 0;i < 1000; i++) {
            String object = restTemplate.getForObject("http://localhost:8080/test-a/common", String.class);
            System.out.println("-----"+object+"-----");
        }
    }
}
