<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import ThreeScene from './components/ThreeScene.vue';
import request from './utils/request.js';
const { get, post } = request;

// 类型定义，解决error类型问题
interface ErrorWithResponse {
  response?: {
    status: number;
    data: any;
  };
  message?: string;
}

onMounted(() => {
  // 这里可以放置一些初始化代码
  console.log('App mounted');
  // 初始加载默认模型
  callTextureMapping();
});

const radio1 = ref('1')

function handleChange(val: string) {
  console.log('radio1', val);
  if (val === '1') {
    calendarValue.value = new Date('2023-10-01')
  } else if (val === '2') {
    calendarValue.value = new Date('2024-10-02')
  }
  // 当选项改变时也调用贴图API
  callTextureMapping();
}
const calendar = ref<any>()

const selectDate = (val: any) => {
  if (!calendar.value) return
  calendar.value.selectDate(val)
}
// 使用el-radio 切换时  更改变量calendarValue的值

const calendarValue = ref(new Date('2023-10-01'));
//模型地址
///////////////////////////////////////////////////
const modelUrl = ref('');
const isLoading = ref(false);
const apiMessage = ref('');

// Dialog控制变量
const textureDialogVisible = ref(false);
const baseImageUrl = 'http://192.168.31.237:8000/';
const texturePaths = [
  '/texture/20250522/top.jpg',
  '/texture/20250522/side.jpg'
];

// 打开纹理预览对话框
const showTexturePreview = () => {
  textureDialogVisible.value = true;
};

// 监听日期变化
watch(calendarValue, () => {
  callTextureMapping();
});

// 添加API健康检查函数
const checkApiHealth = async () => {
  try {
    const healthRes = await get('/api/ship/health');
    console.log('API健康状态:', healthRes);
    return healthRes.status === 'UP';
  } catch (error) {
    console.error('API健康检查失败:', error);
    return false;
  }
};

// 船舶纹理贴图API调用
const callTextureMapping = async () => {
  isLoading.value = true;
  apiMessage.value = '处理中...';
  
  try {
    // 先检查API健康状态
    // const apiHealthy = await checkApiHealth();
    // if (!apiHealthy) {
    //   apiMessage.value = '后端服务不可用，请检查服务状态';
    //   isLoading.value = false;
    //   return;
    // }
    
    // 从日期中提取纹理日期格式
    const date = calendarValue.value;
    // const textureDate = `${date.getFullYear()}${(date.getMonth()+1).toString().padStart(2, '0')}${date.getDate().toString().padStart(2, '0')}`;
    
    // 根据radio选择不同的模型
    //nst shipModel = radio1.value === '1' ? '02_chuizhi' : 'alternative_model';
    
    // console.log('发送请求参数:', { shipModel, textureDate });
//     {
//   "shipModel": "http://192.168.31.237:8000//model/02_chuizhi.ply",
//   "textureDate": "http://192.168.31.237:8000//texture/20250522/top.jpg"
// }
    let param = {
      shipModel: "/model/02_chuizhi.ply",
      textureDate: [
        '/texture/20250522/top.jpg',
        '/texture/20250522/side.jpg'
      ]
    }
  try {
      const res = await post('/api/ship/texture-mapping', param);
      
      console.log('纹理贴图处理结果:', res);
      
      if (res.success) {
        // 更新模型URL，这里是服务器返回的模型URL地址
        // 确保URL是完整路径
        const baseUrl = 'http://localhost:8080';
        
        // 确保modelUrl正确构建，避免重复的斜杠
        const serverPath = res.modelUrl.startsWith('/') ? res.modelUrl : `/${res.modelUrl}`;
        
        // 添加时间戳参数，防止缓存
        const timestamp = new Date().getTime();
        modelUrl.value = `${baseUrl}${serverPath}?t=${timestamp}`;
        
        console.log('完整模型URL构建:', modelUrl.value);
        console.log('模型路径检查:');
        console.log('- 基础URL:', baseUrl);
        console.log('- 服务器路径:', serverPath);
        console.log('- 时间戳:', timestamp);
        console.log('- 最终URL:', modelUrl.value);
        
        apiMessage.value = res.message || '处理成功';
      } else {
        apiMessage.value = res.message || '处理失败';
        console.error('服务器返回错误:', res);
      }
    } catch (requestError: unknown) {
      const reqErr = requestError as ErrorWithResponse;
      console.error('请求处理失败:', reqErr);
      apiMessage.value = `请求失败: ${reqErr.message || '未知错误'}`;
      
      // 尝试检查路径配置
      try {
        console.log('尝试检查路径配置...');
        const pathCheckRes = await get('/api/ship/check-paths');
        console.log('路径检查结果:', pathCheckRes);
      } catch (pathCheckError: unknown) {
        const pathErr = pathCheckError as ErrorWithResponse;
        console.error('路径检查失败:', pathErr);
      }
    }
  } catch (error: unknown) {
    const err = error as ErrorWithResponse;
    console.error('纹理贴图处理失败:', err);
    apiMessage.value = '请求失败，请检查服务器';
    
    // 显示更多错误信息以便调试
    if (err.response) {
      console.error('错误状态:', err.response.status);
      console.error('错误数据:', err.response.data);
    }
  } finally {
    isLoading.value = false;
  }
}

const monthMap = ['一月', '二月', '三月', '四月', '五月', '六月', 
                '七月', '八月', '九月', '十月', '十一月', '十二月']

// 格式化显示
const formatChineseDate = (date:any) => {
  console.log('date111', date)
  return date
}

const forceReloadModel = () => {
  // Implementation of forceReloadModel function
  console.log('Force reload model');
  callTextureMapping();
}
</script>

<template>
  <div class="app">
    <ThreeScene :modelUrl="modelUrl"/>

    <div class="btn-box">
      <el-radio-group v-model="radio1" @change="handleChange">
        <el-radio value="1" size="large">模型一</el-radio>
        <el-radio value="2" size="large">模型二</el-radio>
      </el-radio-group>
      
      <div class="action-buttons">
        <el-button type="primary" @click="callTextureMapping" :loading="isLoading">
          重新生成模型
        </el-button>
        <el-button type="warning" @click="forceReloadModel" v-if="modelUrl">
          强制重新加载
        </el-button>
        <el-button type="info" @click="showTexturePreview">
          预览纹理图片
        </el-button>
      </div>
      
      <div class="status-message" :class="{'error': apiMessage.includes('失败')}">
        {{ apiMessage }}
      </div>
    </div>
    <div class="calendar-box">
      <el-calendar ref="calendar" v-model="calendarValue">
        <template #header="{ date }">
          <span>日历</span>
          <span>{{ formatChineseDate(date) }}</span>
          <el-button-group>
            <el-button size="small" @click="selectDate('prev-year')">
              上一年
            </el-button>
            <el-button size="small" @click="selectDate('prev-month')">
              上一月
            </el-button>
            <el-button size="small" @click="selectDate('today')">今日</el-button>
            <el-button size="small" @click="selectDate('next-month')">
              下一月
            </el-button>
            <el-button size="small" @click="selectDate('next-year')">
              下一年
            </el-button>
          </el-button-group>
        </template>
        <template #date-cell="{ data }">
      <p :class="data.isSelected ? 'is-selected' : ''">
        {{ data.day.split('-')[2] }}
        {{ data.isSelected ? '✔️' : '' }}
      </p>
    </template>
      </el-calendar>
    </div>

    <!-- 纹理图片预览对话框 -->
    <el-dialog
      v-model="textureDialogVisible"
      title="纹理图片预览"
      width="80%"
      :modal="false"
      :append-to-body="true"
      destroy-on-close
    >
      <div class="texture-preview-container">
        <div class="texture-item" v-for="(path, index) in texturePaths" :key="index">
          <h3>{{ index === 0 ? '顶视图纹理' : '侧视图纹理' }}</h3>
          <div class="image-container">
            <img :src="baseImageUrl + path" :alt="`纹理图片 ${index + 1}`" />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="textureDialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  position: relative;
  width: 100%;
  .btn-box {
    position: absolute;
    z-index: 99;
    top: 20px;
    right: 20%;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .status-message {
    margin-top: 10px;
    font-size: 14px;
    color: #67C23A;
    &.error {
      color: #F56C6C;
    }
  }

  .calendar-box {
    position: absolute;
    z-index: 99;
    top: 20px;
    left: 0;
    width: 500px;
    height: 400px
    ;
    margin: 0!important;
    padding: 0!important;
  }
  .el-calendar{
    color: black!important;
  }
}

/* 纹理预览样式 */
.texture-preview-container {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 20px;
  justify-content: center;
}

.texture-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 45%;
}

.texture-item h3 {
  margin-bottom: 10px;
  font-size: 18px;
  color: #333;
}

.image-container {
  width: 100%;
  border: 1px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.image-container img {
  width: 100%;
  display: block;
}

/* 移除dialog的默认遮罩样式 */
:deep(.el-dialog__wrapper) {
  background-color: transparent !important;
}
</style>
