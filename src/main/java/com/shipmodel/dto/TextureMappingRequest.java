package com.shipmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "纹理贴图请求参数")
public class TextureMappingRequest {
    @Schema(description = "模型文件路径", example = "/model/02_chuizhi.ply", required = true)
    private String shipModel;  // 模型文件路径
    
    @Schema(description = "纹理图片路径列表", example = "[\"/texture/20250522/top.jpg\", \"/texture/20250522/side.jpg\"]", required = true)
    private List<String> textureDate; // 纹理图片路径列表
    
    public String getShipModel() {
        return shipModel;
    }
    
    public void setShipModel(String shipModel) {
        this.shipModel = shipModel;
    }
    
    public List<String> getTextureDate() {
        return textureDate;
    }
    
    public void setTextureDate(List<String> textureDate) {
        this.textureDate = textureDate;
    }

    @Override
    public String toString() {
        return "TextureMappingRequest{" +
                "shipModel='" + shipModel + '\'' +
                ", textureDate=" + textureDate +
                '}';
    }
} 