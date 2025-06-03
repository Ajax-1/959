package com.shipmodel.service;

import java.util.List;

public interface BlenderService {
    /**
     * 执行Blender纹理贴图脚本
     *
     * @param shipModel 船型号，用于确定使用哪个白模型
     * @param textureDate 日期，用于确定使用哪个纹理图片
     * @return 输出的模型文件路径
     */
    String executeTextureMappingScript(String shipModel, String textureDate);
    
    /**
     * 执行Blender纹理贴图脚本（使用完整路径）
     *
     * @param modelPath 模型文件完整路径
     * @param texturePaths 纹理图片完整路径列表
     * @return 输出的模型文件路径
     */
    String executeTextureMappingWithPaths(String modelPath, List<String> texturePaths);
} 