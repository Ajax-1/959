package com.shipmodel.service.impl;

import com.shipmodel.service.BlenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BlenderServiceImpl implements BlenderService {

    private static final Logger log = LoggerFactory.getLogger(BlenderServiceImpl.class);

    @Value("${blender.executable.path}")
    private String blenderExecutablePath;
    
    @Value("${blender.script.path}")
    private String blenderScriptPath;
    
    @Value("${models.base.dir:}")
    private String modelsBaseDir;
    
    @Value("${textures.base.dir:}")
    private String texturesBaseDir;
    
    @Value("${output.models.dir}")
    private String outputModelsDir;
    
    @Value("${server.base.url:#{null}}")
    private String serverBaseUrl; // 可选配置，服务器基础URL
    
    /**
     * 执行Blender纹理贴图脚本（使用简化参数）
     * 
     * @param shipModel 船型号，用于确定使用哪个白模型
     * @param textureDate 日期，用于确定使用哪个纹理图片
     * @return 输出的模型文件路径
     */
    @Override
    public String executeTextureMappingScript(String shipModel, String textureDate) {
        try {
            // 1. 检查输入文件是否存在
            File modelFile = new File(modelsBaseDir + File.separator + shipModel + ".ply");
            File topTextureFile = new File(texturesBaseDir + File.separator + textureDate + File.separator + "top.jpg");
            File sideTextureFile = new File(texturesBaseDir + File.separator + textureDate + File.separator + "side.jpg");
            
            // 记录输入文件存在性到日志
            log.debug("检查输入文件存在性:");
            log.debug("模型文件: {} - {}", modelFile.getAbsolutePath(), modelFile.exists() ? "存在" : "不存在");
            log.debug("顶部纹理: {} - {}", topTextureFile.getAbsolutePath(), topTextureFile.exists() ? "存在" : "不存在");
            log.debug("侧面纹理: {} - {}", sideTextureFile.getAbsolutePath(), sideTextureFile.exists() ? "存在" : "不存在");
            
            // 确保输出目录存在，不存在则创建
            File outputDir = new File(outputModelsDir);
            if (!outputDir.exists()) {
                log.info("创建输出目录: {}", outputDir.getAbsolutePath());
                outputDir.mkdirs();
            }
            
            // 1. 创建临时修改版本的Blender脚本
            String modifiedScriptPath = createModifiedScript(shipModel, textureDate);
            
            // 2. 生成输出文件名
            String outputFileName = generateOutputFileName(shipModel, textureDate);
            String outputFilePath = outputModelsDir + File.separator + outputFileName;
            
            log.info("将生成的输出文件: {}", outputFilePath);
            
            // 准备传递给脚本的参数
            String modelPath, topTexturePath, sideTexturePath;
            
            // 判断是否配置了服务器基础URL，决定使用HTTP URL还是本地文件路径
            if (serverBaseUrl != null && !serverBaseUrl.isEmpty()) {
                // 使用HTTP URL格式
                modelPath = serverBaseUrl + "/model/" + shipModel + ".ply";
                topTexturePath = serverBaseUrl + "/texture/" + textureDate + "/top.jpg";
                sideTexturePath = serverBaseUrl + "/texture/" + textureDate + "/side.jpg";
                
                // 记录使用的URL路径
                log.info("使用HTTP URL格式的输入文件:");
                log.info("模型URL: {}", modelPath);
                log.info("顶视图URL: {}", topTexturePath);
                log.info("侧视图URL: {}", sideTexturePath);
            } else {
                // 使用本地文件路径
                modelPath = modelsBaseDir + File.separator + shipModel + ".ply";
                topTexturePath = texturesBaseDir + File.separator + textureDate + File.separator + "top.jpg";
                sideTexturePath = texturesBaseDir + File.separator + textureDate + File.separator + "side.jpg";
            }
            
            // 3. 执行Blender命令
            executeBlenderCommand(modifiedScriptPath, modelPath, topTexturePath, sideTexturePath, outputFilePath);
            
            // 返回生成的模型文件路径
            return outputFileName;
            
        } catch (Exception e) {
            // 记录错误并转换为运行时异常
            log.error("执行Blender脚本时发生错误", e);
            throw new RuntimeException("纹理贴图处理失败", e);
        }
    }
    
    /**
     * 执行Blender纹理贴图脚本（使用完整路径）- 支持内网数据格式
     *
     * @param modelPath 模型文件完整路径
     * @param texturePaths 纹理图片完整路径列表
     * @return 输出的模型文件路径
     */
    @Override
    public String executeTextureMappingWithPaths(String modelPath, List<String> texturePaths) {
        try {
            // 记录处理请求信息
            log.info("使用完整路径执行纹理贴图: 模型={}, 纹理={}", modelPath, texturePaths);
            
            // 验证纹理路径列表
            if (texturePaths == null || texturePaths.size() < 2) {
                throw new IllegalArgumentException("至少需要提供两个纹理路径（顶视图和侧视图）");
            }
            
            // 从路径中提取模型名和日期，用于生成输出文件名
            String extractedModelName = extractModelName(modelPath);
            String extractedTextureDate = extractTextureDate(texturePaths.get(0));
            
            // 记录提取的信息
            log.info("从路径提取的信息: 模型名称={}, 纹理日期={}", extractedModelName, extractedTextureDate);
            
            // 确保输出目录存在
            File outputDir = new File(outputModelsDir);
            if (!outputDir.exists()) {
                log.info("创建输出目录: {}", outputDir.getAbsolutePath());
                outputDir.mkdirs();
            }
            
            // 1. 创建临时修改版本的Blender脚本
            String modifiedScriptPath = createModifiedScript(extractedModelName, extractedTextureDate);
            
            // 2. 生成输出文件名
            String outputFileName = generateOutputFileName(extractedModelName, extractedTextureDate);
            String outputFilePath = outputModelsDir + File.separator + outputFileName;
            
            log.info("将生成的输出文件: {}", outputFilePath);
            
            // 构建完整URL或使用提供的路径
            // String fullModelPath = buildFullPath(modelPath);
            // String topTexturePath = buildFullPath(texturePaths.get(0));
            // String sideTexturePath = buildFullPath(texturePaths.get(1));
            String fullModelPath = modelPath;
            String topTexturePath = texturePaths.get(0);
            String sideTexturePath = texturePaths.get(1);
            // 记录处理的文件路径
            log.info("处理的文件路径:");
            log.info("模型路径: {}", fullModelPath);
            log.info("顶视图路径: {}", topTexturePath);
            log.info("侧视图路径: {}", sideTexturePath);
            
            // 3. 执行Blender命令
            executeBlenderCommand(modifiedScriptPath, fullModelPath, topTexturePath, sideTexturePath, outputFilePath);
            
            // 返回生成的模型文件路径
            return outputFileName;
            
        } catch (Exception e) {
            // 记录错误并转换为运行时异常
            log.error("使用完整路径执行Blender脚本时发生错误", e);
            throw new RuntimeException("纹理贴图处理失败", e);
        }
    }
    
    /**
     * 执行Blender命令
     * 
     * @param scriptPath Blender脚本路径
     * @param modelPath 模型路径
     * @param topTexturePath 顶视图纹理路径
     * @param sideTexturePath 侧视图纹理路径
     * @param outputFilePath 输出文件路径
     * @throws Exception 执行过程中可能的异常
     */
    private void executeBlenderCommand(String scriptPath, String modelPath, String topTexturePath, 
                                      String sideTexturePath, String outputFilePath) throws Exception {
            // 创建进程构建器，设置命令行参数
            ProcessBuilder processBuilder = new ProcessBuilder(
                blenderExecutablePath,  // Blender程序路径
                "--background",        // 无界面模式
            "--python", scriptPath, // 指定Python脚本
            "--", // 传递参数给Python脚本
            modelPath, // 模型路径
            topTexturePath, // 顶视图纹理
            sideTexturePath, // 侧视图纹理
            outputFilePath // 输出文件路径
            );
            
            // 设置工作目录为当前目录
            processBuilder.directory(new File(System.getProperty("user.dir")));
            
            // 将错误输出合并到标准输出
            processBuilder.redirectErrorStream(true);
            
            // 记录执行的命令
            log.info("执行Blender命令: {}", processBuilder.command());
            
            // 启动进程
            Process process = processBuilder.start();
            
            // 读取进程输出并记录到日志
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("Blender输出: {}", line);
                }
            }
            
            // 等待进程完成并获取退出码
            int exitCode = process.waitFor();
            log.info("Blender进程退出，状态码: {}", exitCode);
            
            // 检查退出码，非零表示执行失败
            if (exitCode != 0) {
                throw new RuntimeException("Blender执行失败，退出码: " + exitCode);
            }
            
            // 清理临时脚本文件
        Files.deleteIfExists(Paths.get(scriptPath));
            
        // 验证输出文件是否生成成功
        File outputFile = new File(outputFilePath);
        if (!outputFile.exists() || outputFile.length() == 0) {
            log.error("模型文件生成失败，文件不存在或大小为0: {}", outputFilePath);
            throw new RuntimeException("模型文件生成失败");
        }
        
        // 记录成功生成的文件信息
        log.info("成功生成模型文件: {}，文件大小: {} 字节", outputFilePath, outputFile.length());
    }
    
    /**
     * 从模型路径中提取模型名称
     * 
     * @param modelPath 模型文件路径
     * @return 提取的模型名称（不含扩展名）
     */
    private String extractModelName(String modelPath) {
        // 从路径中提取文件名（不含扩展名）
        String fileName = new File(modelPath).getName();
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }
    
    /**
     * 从纹理路径中提取日期
     * 
     * @param texturePath 纹理文件路径
     * @return 提取的日期字符串
     */
    private String extractTextureDate(String texturePath) {
        // 尝试从路径中提取日期格式 - 支持新的内网路径格式
        // 例如: /pan/20241216/JB14_ccd_....../hf.jpg
        Pattern pattern = Pattern.compile("/(\\d{8})/");
        Matcher matcher = pattern.matcher(texturePath);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // 如果没找到日期格式，使用当前日期
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
    
    /**
     * 构建完整路径（如果需要，添加服务器基础URL）
     * 
     * @param path 原始路径
     * @return 构建后的完整路径
     */
    // 在 buildFullPath 方法中添加SFTP路径识别 6.4
    private String buildFullPath(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            // HTTP URL
            return path;
        } else if (path.startsWith("/mnt/")) {
            // SFTP路径，直接返回
            return path;
        } else if (path.startsWith("/")) {
            // 其他绝对路径
            if (serverBaseUrl != null && !serverBaseUrl.isEmpty()) {
                return serverBaseUrl + (serverBaseUrl.endsWith("/") ? path.substring(1) : path);
            }
        }
        return path;
    }
    
    /**
     * 创建修改后的Blender脚本，替换路径参数
     * 
     * @param shipModel 船型号
     * @param textureDate 纹理日期
     * @return 修改后脚本的临时文件路径
     * @throws Exception 创建过程中可能的异常
     */
    private String createModifiedScript(String shipModel, String textureDate) throws Exception {
        // 1. 读取原始脚本内容 - 使用Java 8兼容的方法
        String originalScript = new String(Files.readAllBytes(Paths.get(blenderScriptPath)), StandardCharsets.UTF_8);
        
        // 自从我们修改了Python脚本接受URL参数，这部分不再需要修改脚本内容
        String modifiedScript = originalScript;
            
        // 将修改后的脚本写入临时文件 - 使用Java 8兼容的方法，使用File.separator确保跨平台兼容
        String tempScriptPath = System.getProperty("java.io.tmpdir") + File.separator + "blender_script_" + UUID.randomUUID() + ".py";
        Files.write(Paths.get(tempScriptPath), modifiedScript.getBytes(StandardCharsets.UTF_8));
        
        return tempScriptPath;
    }
    
    /**
     * 生成输出文件名
     * 
     * @param shipModel 船型号
     * @param textureDate 纹理日期
     * @return 生成的输出文件名
     */
    private String generateOutputFileName(String shipModel, String textureDate) {
        // 生成一个时间戳作为文件名的一部分
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return shipModel + "_" + textureDate + "_" + timestamp + ".glb";
    }
} 