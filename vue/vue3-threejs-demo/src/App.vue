<script setup lang="ts">
// 导入Vue核心功能
import { ref, onMounted, watch } from 'vue';
// 导入Three.js场景组件
import ThreeScene from './components/ThreeScene.vue';
// 导入请求工具
import request from './utils/request.js';
// 导入Element Plus消息提示组件
import { ElMessage } from 'element-plus';
// 解构获取get和post请求方法
const { get, post } = request;

// 定义错误响应类型接口，用于类型检查和提示
interface ErrorWithResponse {
  response?: {
    status: number;  // 状态码
    data: any;       // 响应数据
  };
  message?: string;  // 错误信息
}

// 组件挂载完成后执行
onMounted(() => {
  // 记录日志，表示组件挂载完成
  console.log('App mounted');
  // 初始化时调用纹理贴图函数加载默认模型
  callTextureMapping();
});

// 新增 - 内网数据接口相关配置
// 初始化选择类型为'1'(SAR)
const radio1 = ref('1');
// 存储从接口获取的数据
const reData = ref();
// 当前选中的日期
const nowDate = ref(new Date());
// 请求URL路径，默认为SAR接口
const url = ref('/sar/listDetailBycondition');
// 内网API服务器基础地址
let baseUrl = 'http://172.14.10.218:8999';

// 处理图像类型切换的函数
function handleChange(val: string) {
  // 记录选择值
  console.log('radio1', val);
  // 根据选择的值切换不同类型的数据接口
  if (val === '1') {
    // SAR雷达图像
    url.value = '/sar/listDetailBycondition';
  } else if (val === '2') {
    // IRS红外图像
    url.value = '/irs/listDetailBycondition';
  } else if (val === '3') {
    // PAN可见光图像
    url.value = '/pan/listDetailBycondition';
  }
  // 调用获取数据的函数，更新模型
  callTextureMapping();
}

// 日历组件引用
const calendar = ref<any>();

// 日历选择函数
const selectDate = (val: any) => {
  // 检查日历组件是否已加载
  if (!calendar.value) return;
  // 调用日历组件的选择日期方法
  calendar.value.selectDate(val);
}

// 日历当前选中的日期
const calendarValue = ref(new Date('2023-10-01'));

// 模型URL地址
const modelUrl = ref('');
// 加载状态标识
const isLoading = ref(false);
// API消息提示
const apiMessage = ref('');

// Dialog控制变量
// 控制纹理预览对话框的显示状态
const textureDialogVisible = ref(false);
// 纹理图片路径列表，使用ref使其成为响应式数据
const texturePaths = ref([]);
// 内网数据服务器基础地址
const serverBaseUrl = ref('http://30.249.201.203/mnt/MT/MT_DATA/MT_Dat_DATA');

// 打开纹理预览对话框的函数
const showTexturePreview = () => {
  // 设置对话框为显示状态
  textureDialogVisible.value = true;
};

// 监听日期变化，获取内网数据
watch(
  // 监听日期变化
  nowDate,
  // 回调函数，接收新值和旧值
  async (newV, oldV) => {
    // 只有当日期变化时才执行
    if (newV != oldV) {
      // 获取日期对象
      let date = newV;
      // 格式化年份
      const year = date.getFullYear();
      // 格式化月份（补零）
      const month = (date.getMonth() + 1).toString().padStart(2, '0');
      // 格式化日期（补零）
      const day = date.getDate().toString().padStart(2, '0');
      // 组合完整日期字符串，格式为YYYYMMDD
      const formattedDate = `${year}${month}${day}`;
      
      // 准备请求参数
      const postData = {
        dateStr: formattedDate,  // 日期字符串
        objType: "尼米兹级",      // 对象类型
      };
      
      // 记录请求信息
      console.log('url', url.value);
      console.log('postData', postData);
      
      try {
        // 发送请求获取数据
        const resultPost = await post(`${baseUrl}${url.value}`, postData);
        // 记录返回结果
        console.log('resultPost', resultPost);
        
        // 解构获取结果和状态码
        const { result, retCode } = resultPost;
        // 检查是否成功并有数据
        if (retCode == 2000 && result && result.length > 0) {
          // 存储返回的数据
          reData.value = result;
          // 提取PanobjPath字段，即图片路径
          const paths = result.map(item => item.PanobjPath);
          // 更新纹理路径列表
          texturePaths.value = paths;
          
          // 自动调用纹理贴图处理
          callTextureMapping();
        } else {
          // 显示无数据提示
          ElMessage({
            message: '暂无数据',
            type: 'warning'
          });
        }
      } catch (error) {
        // 记录错误信息
        console.error('获取数据失败:', error);
        // 显示错误提示
        ElMessage({
          message: '获取数据失败',
          type: 'error'
        });
      }
    }
  },
  // 配置深度监听
  { deep: true }
);

// 同步nowDate和calendarValue，保持两者一致
watch(calendarValue, (newVal) => {
  // 当calendarValue变化时，更新nowDate
  nowDate.value = newVal;
});

// 船舶纹理贴图API调用 - 修改为使用内网真实数据
const callTextureMapping = async () => {
  // 设置加载状态为true
  isLoading.value = true;
  // 更新处理状态消息
  apiMessage.value = '处理中...';
  
  try {
    // 检查是否有纹理数据
    if (!texturePaths.value || texturePaths.value.length === 0) {
      // 如果没有纹理数据，更新提示信息
      apiMessage.value = '暂无纹理数据，请先选择日期获取数据';
      // 结束加载状态
      isLoading.value = false;
      return;
    }
    
    // 准备请求参数，使用获取的真实数据路径
    let param = {
      shipModel: "/model/02_chuizhi.ply", // 模型路径保持不变
      textureDate: texturePaths.value     // 使用从内网获取的真实路径
    }
    
    try {
      // 发送纹理贴图处理请求
      const res = await post('/api/ship/texture-mapping', param);
      
      // 记录处理结果
      console.log('纹理贴图处理结果:', res);
      
      // 检查处理是否成功
      if (res.success) {
        // 构建后端服务器基础URL
        const baseUrl = 'http://localhost:8080';
        
        // 确保modelUrl正确构建，避免重复的斜杠
        const serverPath = res.modelUrl.startsWith('/') ? res.modelUrl : `/${res.modelUrl}`;
        
        // 添加时间戳参数，防止缓存
        const timestamp = new Date().getTime();
        // 构建完整的模型URL
        modelUrl.value = `${baseUrl}${serverPath}?t=${timestamp}`;
        
        // 记录URL构建信息
        console.log('完整模型URL构建:', modelUrl.value);
        console.log('模型路径检查:');
        console.log('- 基础URL:', baseUrl);
        console.log('- 服务器路径:', serverPath);
        console.log('- 时间戳:', timestamp);
        console.log('- 最终URL:', modelUrl.value);
        
        // 更新成功消息
        apiMessage.value = res.message || '处理成功';
      } else {
        // 更新失败消息
        apiMessage.value = res.message || '处理失败';
        // 记录错误信息
        console.error('服务器返回错误:', res);
      }
    } catch (requestError: unknown) {
      // 类型转换，以便访问错误属性
      const reqErr = requestError as ErrorWithResponse;
      // 记录请求错误
      console.error('请求处理失败:', reqErr);
      // 更新错误消息
      apiMessage.value = `请求失败: ${reqErr.message || '未知错误'}`;
      
      // 尝试检查路径配置
      try {
        console.log('尝试检查路径配置...');
        // 调用路径检查API
        const pathCheckRes = await get('/api/ship/check-paths');
        // 记录检查结果
        console.log('路径检查结果:', pathCheckRes);
      } catch (pathCheckError: unknown) {
        // 类型转换，以便访问错误属性
        const pathErr = pathCheckError as ErrorWithResponse;
        // 记录路径检查错误
        console.error('路径检查失败:', pathErr);
      }
    }
  } catch (error: unknown) {
    // 类型转换，以便访问错误属性
    const err = error as ErrorWithResponse;
    // 记录贴图处理错误
    console.error('纹理贴图处理失败:', err);
    // 更新错误消息
    apiMessage.value = '请求失败，请检查服务器';
    
    // 显示更多错误信息以便调试
    if (err.response) {
      console.error('错误状态:', err.response.status);
      console.error('错误数据:', err.response.data);
    }
  } finally {
    // 无论成功或失败，都结束加载状态
    isLoading.value = false;
  }
}

// 月份名称映射
const monthMap = ['一月', '二月', '三月', '四月', '五月', '六月', 
                '七月', '八月', '九月', '十月', '十一月', '十二月']

// 日期格式化显示函数
const formatChineseDate = (date:any) => {
  // 记录日期
  console.log('date111', date)
  // 返回日期本身（可以在此处添加格式化逻辑）
  return date
}

// 强制重新加载模型的函数
const forceReloadModel = () => {
  // 记录强制重载操作
  console.log('Force reload model');
  // 调用贴图函数重新加载
  callTextureMapping();
}
</script>

<template>
  <div class="app">
    <!-- Three.js场景组件，传入模型URL -->
    <ThreeScene :modelUrl="modelUrl"/>

    <!-- 控制按钮区域 -->
    <div class="btn-box">
      <!-- 图像类型切换单选按钮组 -->
      <el-radio-group v-model="radio1" @change="handleChange">
        <el-radio value="1" size="large">SAR</el-radio>
        <el-radio value="2" size="large">红外</el-radio>
        <el-radio value="3" size="large">可见光</el-radio>
      </el-radio-group>
      
      <!-- 操作按钮区域 -->
      <div class="action-buttons">
        <!-- 生成模型按钮，显示加载状态 -->
        <el-button type="primary" @click="callTextureMapping" :loading="isLoading">
          生成模型
        </el-button>
        <!-- 强制重新加载按钮，仅在有模型时显示 -->
        <el-button type="warning" @click="forceReloadModel" v-if="modelUrl">
          强制重新加载
        </el-button>
        <!-- 预览纹理图片按钮，仅在有纹理路径时显示 -->
        <el-button type="info" @click="showTexturePreview" v-if="texturePaths.length > 0">
          预览纹理图片
        </el-button>
      </div>
      
      <!-- 状态消息区域，根据消息内容动态添加错误样式 -->
      <div class="status-message" :class="{'error': apiMessage.includes('失败')}">
        {{ apiMessage }}
      </div>
    </div>
    
    <!-- 日历组件区域 -->
    <div class="calendar-box">
      <el-calendar ref="calendar" v-model="calendarValue">
        <!-- 自定义日历头部 -->
        <template #header="{ date }">
          <span>日历</span>
          <span>{{ formatChineseDate(date) }}</span>
          <!-- 日历导航按钮 -->
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
        <!-- 自定义日期单元格 -->
        <template #date-cell="{ data }">
      <p :class="data.isSelected ? 'is-selected' : ''">
        {{ data.day.split('-')[2] }} <!-- 显示日期数字 -->
        {{ data.isSelected ? '✔️' : '' }} <!-- 选中日期显示勾选标记 -->
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
      <!-- 纹理图片容器 -->
      <div class="texture-preview-container">
        <!-- 遍历所有纹理图片路径 -->
        <div class="texture-item" v-for="(path, index) in texturePaths" :key="index">
          <h3>纹理图片 {{ index + 1 }}</h3>
          <div class="image-container">
            <!-- 拼接完整的图片URL -->
            <img :src="`${serverBaseUrl}${path}`" :alt="`纹理图片 ${index + 1}`" />
          </div>
        </div>
      </div>
      <!-- 对话框底部 -->
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
  display: flex; /* 使用弹性布局 */
  flex-direction: column; /* 垂直排列 */
  align-items: center; /* 水平居中 */
  justify-content: center; /* 垂直居中 */
  height: 100vh; /* 占满视口高度 */
  position: relative; /* 相对定位 */
  width: 100%; /* 占满宽度 */
  .btn-box {
    position: absolute; /* 绝对定位 */
    z-index: 99; /* 控制层叠顺序 */
    top: 20px; /* 距顶部20px */
    right: 20%; /* 距右侧20% */
    display: flex; /* 使用弹性布局 */
    flex-direction: column; /* 垂直排列 */
    align-items: center; /* 水平居中 */
  }

  .status-message {
    margin-top: 10px; /* 上边距 */
    font-size: 14px; /* 字体大小 */
    color: #67C23A; /* 成功状态颜色 */
    &.error {
      color: #F56C6C; /* 错误状态颜色 */
    }
  }

  .calendar-box {
    position: absolute; /* 绝对定位 */
    z-index: 99; /* 控制层叠顺序 */
    top: 20px; /* 距顶部20px */
    left: 0; /* 靠左对齐 */
    width: 500px; /* 宽度 */
    height: 400px /* 高度 */
    ;
    margin: 0!important; /* 取消外边距 */
    padding: 0!important; /* 取消内边距 */
  }
  .el-calendar{
    color: black!important; /* 日历文本颜色 */
  }
}

/* 纹理预览样式 */
.texture-preview-container {
  display: flex; /* 使用弹性布局 */
  flex-direction: row; /* 水平排列 */
  flex-wrap: wrap; /* 允许换行 */
  gap: 20px; /* 间距 */
  justify-content: center; /* 居中对齐 */
}

.texture-item {
  display: flex; /* 使用弹性布局 */
  flex-direction: column; /* 垂直排列 */
  align-items: center; /* 水平居中 */
  width: 45%; /* 宽度 */
}

.texture-item h3 {
  margin-bottom: 10px; /* 下边距 */
  font-size: 18px; /* 字体大小 */
  color: #333; /* 文字颜色 */
}

.image-container {
  width: 100%; /* 宽度占满父容器 */
  border: 1px solid #ddd; /* 边框 */
  border-radius: 8px; /* 圆角 */
  overflow: hidden; /* 隐藏溢出内容 */
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1); /* 阴影效果 */
}

.image-container img {
  width: 100%; /* 图片宽度 */
  display: block; /* 块级显示 */
}

/* 移除dialog的默认遮罩样式 */
:deep(.el-dialog__wrapper) {
  background-color: transparent !important; /* 透明背景 */
}
</style>
