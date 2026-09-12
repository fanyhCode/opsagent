package com.opsagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OpsAgent 后端启动类。
 *
 * @SpringBootApplication 是一个组合注解，它一次性开启了三件事：
 * 1. 自动配置（Spring Boot 根据依赖自动装配 Tomcat、Spring MVC 等组件）
 * 2. 组件扫描（扫描本包及子包下所有被 @Component / @RestController 等标注的类）
 * 3. 配置类能力（允许在本类中定义 Bean）
 *
 * 因为组件扫描以本类所在包为起点，所以后续所有代码都必须放在 com.opsagent 及其子包下。
 */
@SpringBootApplication
public class OpsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsAgentApplication.class, args);
    }
}
