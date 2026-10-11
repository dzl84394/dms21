package com.dms21.serviceb.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 文档元信息配置
 *
 * 引入 springdoc-openapi-starter-webmvc-ui 后零配置即可用，本类只负责把文档首页的
 * 标题/描述/版本改成业务可读内容（否则默认显示 "OpenAPI definition"）。
 *
 * 访问地址：
 *   Swagger UI 页面：http://localhost:8082/swagger-ui.html
 *   OpenAPI JSON：  http://localhost:8082/v3/api-docs
 *
 * 示例：接口方法上加 @Operation(summary = "...") 即可在 UI 上显示中文接口说明
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI serviceBOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("serviceB API")
                        .description("serviceB 接口文档：对外提供 /hello，并可通过 Eureka 反向调用 serviceA（/callA）")
                        .version("1.0.0")
                        .contact(new Contact().name("dms21")));
    }
}
