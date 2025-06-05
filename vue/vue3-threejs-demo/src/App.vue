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
  //callTextureMapping();
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
// 修改为
const calendarValue = ref(new Date('2024-12-16'));

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
// 内网数据服务器基础地址 6.4
const serverBaseUrl = ref('http://localhost:8080');

// 打开纹理预览对话框的函数
const showTexturePreview = () => {
  // 设置对话框为显示状态
  textureDialogVisible.value = true;
};

// 监听日期变化，获取内网数据新修改的（6.4）
// 监听日期变化，获取内网数据
// 监听日期变化，获取内网数据
watch(
  nowDate,
  async (newV, oldV) => {
    if (newV != oldV) {
      let date = newV;
      const year = date.getFullYear();
      const month = (date.getMonth() + 1).toString().padStart(2, '0');
      const day = date.getDate().toString().padStart(2, '0');
      const formattedDate = `${year}${month}${day}`;
      
      const postData = {
        dateStr: formattedDate,
        objType: "nimizi",
      };
      
      console.log('url', url.value);
      console.log('postData', postData);
      
      // ========== 模拟数据库返回 ==========
      // 根据不同的图像类型构建对应的路径
      let imageType = 'PAN'; // 默认可见光
      if (url.value.includes('sar')) {
        imageType = 'SAR';
      } else if (url.value.includes('irs')) {
        imageType = 'IRS';
      } else if (url.value.includes('pan')) {
        imageType = 'PAN';
      }
      
      // 模拟result数据
      const mockResult = [
        {
          PanobjPath: `/mnt/MT/MT_DATA/MT_Out-Data/${imageType}/${formattedDate}/1.jpg`
        },
        {
          PanobjPath: `/mnt/MT/MT_DATA/MT_Out-Data/${imageType}/${formattedDate}/2.jpg`
        }
      ];
      
      // 模拟API响应
      const resultPost = {
        result: mockResult,
        retCode: 2000
      };
      
      console.log('模拟的resultPost:', resultPost);
      // ========== 模拟结束 ==========
      
      try {
        // 注释掉真实的API调用
        // const resultPost = await post(`${baseUrl}${url.value}`, postData);
        
        const { result, retCode } = resultPost;
        if (retCode == 2000 && result && result.length > 0) {
          reData.value = result;
          
          // 提取所有图片路径
          const allPaths = result.map(item => item.PanobjPath);
          
          // 只取前两张图片作为top和side纹理
          if (allPaths.length >= 2) {
            texturePaths.value = [
              allPaths[0],  // 第一张作为top纹理（1.jpg）
              allPaths[1]   // 第二张作为side纹理（2.jpg）
            ];
            console.log('选取的纹理路径:', texturePaths.value);
          } else if (allPaths.length === 1) {
            // 如果只有一张图片，复制使用
            texturePaths.value = [allPaths[0], allPaths[0]];
            ElMessage.warning('只找到一张图片，将同时用于顶视图和侧视图');
          } else {
            texturePaths.value = [];
            ElMessage.warning('未找到图片数据');
            return;
          }
          
          // TODO: 未来这里可以从result中获取模型路径
          // 例如: modelPath.value = result[0].ModelPath || generateDefaultModelPath();
          
          // 自动调用纹理贴图处理
          callTextureMapping();
        } else {
          ElMessage({
            message: '暂无数据',
            type: 'warning'
          });
        }
      } catch (error) {
        console.error('获取数据失败:', error);
        ElMessage({
          message: '获取数据失败',
          type: 'error'
        });
      }
    }
  },
  { deep: true }
);

// 同步nowDate和calendarValue，保持两者一致
watch(calendarValue, (newVal) => {
  // 当calendarValue变化时，更新nowDate
  nowDate.value = newVal;
});

// 船舶纹理贴图API调用 - 修改为使用内网真实数据 6.4
const callTextureMapping = async () => {
  isLoading.value = true;
  apiMessage.value = '处理中...';
  
  try {
    if (!texturePaths.value || texturePaths.value.length === 0) {
      apiMessage.value = '暂无纹理数据，请先选择日期获取数据';
      isLoading.value = false;
      return;
    }
    
    // 船型号到英文名的映射
    const shipModelNameMap = {
      "nimizi": "nimizi"
      // 未来可以添加更多映射
    };
    
    // 获取当前船型号（目前写死）
    const shipType = "nimizi";
    const shipModelName = shipModelNameMap[shipType] || "default";
    
    // 构建模型路径（目前写死，未来从数据库获取）
    // TODO: 未来这个路径应该从数据库返回的 modelPath 字段获取
    const modelPath = `/mnt/MT/MT_DATA/3d_recon_DATA/${shipType}/${shipModelName}.ply`;
    
    // 准备请求参数
    let param = {
      shipModel: modelPath,         // 模型路径
      textureDate: texturePaths.value  // 纹理路径数组 [top, side]
    }
    
    console.log('发送纹理贴图请求:', param);
    
    try {
      const res = await post('/api/ship/texture-mapping', param);
      
      console.log('纹理贴图处理结果:', res);
      
      if (res.success) {
        const baseUrl = 'http://localhost:8080';
        const serverPath = res.modelUrl.startsWith('/') ? res.modelUrl : `/${res.modelUrl}`;
        const timestamp = new Date().getTime();
        modelUrl.value = `${baseUrl}${serverPath}?t=${timestamp}`;
        
        console.log('完整模型URL:', modelUrl.value);
        apiMessage.value = res.message || '处理成功';
      } else {
        apiMessage.value = res.message || '处理失败';
        console.error('服务器返回错误:', res);
      }
    } catch (requestError: unknown) {
      const reqErr = requestError as ErrorWithResponse;
      console.error('请求处理失败:', reqErr);
      apiMessage.value = `请求失败: ${reqErr.message || '未知错误'}`;
    }
  } catch (error: unknown) {
    const err = error as ErrorWithResponse;
    console.error('纹理贴图处理失败:', err);
    apiMessage.value = '请求失败，请检查服务器';
  } finally {
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
            <!-- 将SFTP路径转换为API路径 -->
            <img :src="`/api/images?path=${encodeURIComponent(path)}`" :alt="`纹理图片 ${index + 1}`" />
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
