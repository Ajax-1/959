package com.shipmodel.controller;

import com.shipmodel.service.BlenderService;
import com.shipmodel.dto.TextureMappingRequest;
import com.shipmodel.dto.TextureMappingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
// 6.4
import org.springframework.http.MediaType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.FileOutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
// 6.4
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 船舶模型控制器，处理模型相关的API请求
 */
@RestController // 标记为REST控制器
@RequestMapping("/api/ship") // 基础URL路径
@Tag(name = "船舶模型API", description = "船舶模型纹理贴图相关操作") // Swagger API分组标签
public class ShipModelController {

    // 创建日志记录器
    private static final Logger log = LoggerFactory.getLogger(ShipModelController.class);
    
    // 注入服务器基础URL配置
    @Value("${server.base.url:#{null}}")
    private String serverBaseUrl; // 服务器基础URL配置

    // 注入Blender服务
    private final BlenderService blenderService;

    // 构造函数，通过依赖注入BlenderService
    @Autowired
    public ShipModelController(BlenderService blenderService) {
        this.blenderService = blenderService;
    }
    
    /**
     * 健康检查接口，用于测试API服务是否正常运行
     * 
     * @return 包含服务状态信息的响应
     */
    @Operation(summary = "健康检查", description = "检查API服务是否正常运行")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "服务正常", 
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = Map.class)))
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        // 记录API调用日志
        log.info("健康检查API被调用");
        
        // 准备响应数据
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP"); // 服务状态为正常
        response.put("message", "服务正常运行");
        response.put("serverBaseUrl", serverBaseUrl); // 返回当前配置的服务器基础URL
        
        // 返回200 OK状态码和响应数据
        return ResponseEntity.ok(response);
    }
    
    /**
     * 路径配置检查接口，用于验证模型和纹理文件路径配置是否正确
     * 
     * @return 包含路径检查结果的响应
     */
    @Operation(summary = "检查路径配置", description = "检查模型和纹理文件路径配置是否正确")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "路径检查成功", 
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "500", description = "路径检查失败", 
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = Map.class)))
    })
    @GetMapping("/check-paths")
    public ResponseEntity<Map<String, Object>> checkPaths() {
        // 记录API调用日志
        log.info("路径检查API被调用");
        
        // 准备响应数据
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取服务实现类中的路径信息
            response.put("success", true);
            response.put("message", "路径检查完成");
            response.put("info", "请查看服务器日志获取详细路径信息");
            response.put("serverBaseUrl", serverBaseUrl); // 返回当前配置的服务器基础URL
            
            // 触发日志记录，执行一个简单的贴图操作来测试路径配置
            blenderService.executeTextureMappingScript("02_chuizhi", "20250522");
            
            // 返回200 OK状态码和响应数据
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // 记录错误日志
            log.error("路径检查失败: {}", e.getMessage(), e);
            
            // 准备错误响应
            response.put("success", false);
            response.put("message", "路径检查失败");
            response.put("error", e.getMessage());
            
            // 返回500 Internal Server Error状态码和错误响应
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 船舶纹理贴图接口，处理模型贴图请求
     * 
     * @param request 包含模型路径和纹理路径的请求体
     * @return 包含处理结果和输出模型URL的响应
     */
    @Operation(summary = "船舶纹理贴图", description = "根据提供的模型路径和纹理路径，对船舶模型进行纹理贴图处理")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "纹理贴图成功",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TextureMappingResponse.class))),
        @ApiResponse(responseCode = "500", description = "纹理贴图失败",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TextureMappingResponse.class)))
    })
    @PostMapping("/texture-mapping")
    public ResponseEntity<TextureMappingResponse> processTextureMapping(
            @Parameter(description = "纹理贴图请求参数", required = true)
            @RequestBody TextureMappingRequest request) {
        // 记录收到的请求信息
        log.info("接收到纹理贴图请求: {}", request);
        
        try {
            // 获取模型路径
            String modelPath = request.getShipModel();
            
            // 检查请求中纹理路径的格式并记录日志
            if (request.getTextureDate() != null && !request.getTextureDate().isEmpty()) {
                for (String texturePath : request.getTextureDate()) {
                    // 检测内网数据路径格式，例如: /pan/20241216/...
                    Pattern pattern = Pattern.compile("/(pan|sar|irs)/(\\d{8})/");
                    Matcher matcher = pattern.matcher(texturePath);
                    if (matcher.find()) {
                        String imageType = matcher.group(1); // 图像类型：pan、sar或irs
                        String dateStr = matcher.group(2);   // 日期字符串
                        log.info("检测到内网数据路径: 类型={}, 日期={}, 路径={}", imageType, dateStr, texturePath);
                    }
                }
            }
            
            // 记录处理详情日志
            log.info("处理纹理贴图请求: 模型路径={}, 纹理路径数量={}", 
                    modelPath, request.getTextureDate() != null ? request.getTextureDate().size() : 0);
        
            // 调用服务执行Blender脚本进行纹理贴图（使用新方法）
            String outputModelPath = blenderService.executeTextureMappingWithPaths(
                    modelPath, request.getTextureDate());
        
            // 构建响应对象
            TextureMappingResponse response = new TextureMappingResponse();
            response.setSuccess(true);
            response.setMessage("纹理贴图完成");
            response.setModelUrl("/models/" + outputModelPath); // 设置输出模型的URL路径
            
            // 记录成功日志
            log.info("纹理贴图完成: {}", outputModelPath);
        
            // 返回200 OK状态码和成功响应
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // 记录错误日志
            log.error("纹理贴图处理失败: {}", e.getMessage(), e);
            
            // 构建错误响应
            TextureMappingResponse response = new TextureMappingResponse();
            response.setSuccess(false);
            response.setMessage("处理失败: " + e.getMessage());
            
            // 返回500 Internal Server Error状态码和错误响应
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 图片代理API，用于访问SFTP上的图片
     * @param path 图片在SFTP上的路径
     * @return 图片字节数组
     */
    @GetMapping(value = "/api/images", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getImage(@RequestParam String path) {
        try {
            log.info("获取图片请求: {}", path);
            
            // 如果不是SFTP路径，返回错误
            if (!path.startsWith("/mnt/")) {
                log.error("非法路径: {}", path);
                return ResponseEntity.badRequest().build();
            }
            
            // 临时文件路径
            String tempDir = System.getProperty("java.io.tmpdir");
            String fileName = Paths.get(path).getFileName().toString();
            Path localPath = Paths.get(tempDir, fileName);
            
            // 从SFTP下载文件
            try {
                // 创建SSH客户端
                JSch jsch = new JSch();
                Session session = jsch.getSession("root", "10.199.194.144", 5000);
                session.setPassword("205064");
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();
                
                // 连接SFTP
                ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
                sftpChannel.connect();
                
                log.info("SFTP连接成功，开始下载图片: {} -> {}", path, localPath);
                
                // 下载文件
                try (FileOutputStream fos = new FileOutputStream(localPath.toFile())) {
                    sftpChannel.get(path, fos);
                }
                
                // 关闭连接
                sftpChannel.disconnect();
                session.disconnect();
                
                log.info("图片下载成功: {}", localPath);
                
                // 读取图片并返回
                byte[] imageBytes = Files.readAllBytes(localPath);
                
                // 设置响应头
                HttpHeaders headers = new HttpHeaders();
                headers.setCacheControl("max-age=3600");
                
                return ResponseEntity.ok()
                        .headers(headers)
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(imageBytes);
            } catch (JSchException | SftpException | IOException e) {
                log.error("图片访问失败: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            log.error("处理图片请求失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
} 