package com.example.searchAPI.swagger;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
//@EnableAsync

@OpenAPIDefinition(
        info = @Info(title = "Search API 명세서",
                description = "Search API 명세서입니다",
                version = "v1"))
public class SwaggerConfig implements WebMvcConfigurer {
//    @Bean
//    public Docket api() {
//        return new Docket(DocumentationType.SWAGGER_2)
//                .select()
//                .apis(RequestHandlerSelectors.basePackage("controller"))
//                .paths(PathSelectors.any())
//                .build()
//                .apiInfo(apiInfo());
//    }
//
//    private ApiInfo apiInfo() {
//        return new ApiInfoBuilder()
//                .title("API Documentation")
//                .description("API documentation for your project")
//                .version("1.0")
//                .build();
//    }
}
