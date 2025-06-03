package com.shipmodel.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI shipModelOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("船舶模型纹理贴图API")
                        .description("基于Blender的船舶模型纹理贴图系统API文档")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("dev@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("更多关于船舶模型纹理贴图系统")
                        .url("https://example.com/docs"));
    }
} 