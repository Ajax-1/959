package com.shipmodel.config;

// 导入Spring配置相关注解
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
// 导入Spring属性注入注解
import org.springframework.beans.factory.annotation.Value;
// 导入Java文件工具类
import java.io.File;

/**
 * Web配置类，用于设置跨域请求访问以及静态资源处理
 */
@Configuration // 标记为配置类
public class WebConfig implements WebMvcConfigurer {
    
    // 注入输出模型目录的配置值
    @Value("${output.models.dir}")
    private String outputModelsDir;

    /**
     * 配置跨域请求访问规则
     * 
     * @param registry 跨域配置注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 所有路径
                .allowedOrigins(
                    // 允许来自以下域名的请求
                    "http://localhost:5173", // Vue开发服务器
                    "http://localhost:8080", // 服务器自身
                    "http://127.0.0.1:5173", // Vue开发服务器(IP访问)
                    "http://127.0.0.1:8080", // 服务器自身(IP访问)
                    "http://172.14.10.218:8999", // 内网服务器
                    "http://30.249.201.203" // 内网数据服务器
                ) 
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的HTTP方法
                .allowedHeaders("*") // 允许所有头部信息
                .allowCredentials(true) // 允许发送Cookie
                .maxAge(3600); // 预检请求的有效期，单位秒
    }
    
    /**
     * 配置静态资源处理
     * 增加对输出模型目录的直接访问支持
     * 
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 确保输出目录存在
        File outputDir = new File(outputModelsDir);
        if (!outputDir.exists()) {
            outputDir.mkdirs(); // 创建目录
        }
        
        // 配置对模型输出目录的URL访问映射
        registry.addResourceHandler("/models/**") // 定义访问的URL路径
                .addResourceLocations("file:" + outputModelsDir + "/") // 映射到实际的文件系统路径
                .setCachePeriod(0); // 禁用缓存，确保能获取最新的模型文件
    }
} 