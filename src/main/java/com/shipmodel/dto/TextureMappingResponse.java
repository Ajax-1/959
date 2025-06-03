package com.shipmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "纹理贴图处理结果")
public class TextureMappingResponse {
    @Schema(description = "操作是否成功", example = "true")
    private boolean success;
    
    @Schema(description = "处理结果消息", example = "纹理贴图完成")
    private String message;
    
    @Schema(description = "贴图后模型的访问URL", example = "/models/02_chuizhi_20250522_20250522_204702.glb")
    private String modelUrl; // 贴图后模型的访问URL
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getModelUrl() {
        return modelUrl;
    }
    
    public void setModelUrl(String modelUrl) {
        this.modelUrl = modelUrl;
    }
} 