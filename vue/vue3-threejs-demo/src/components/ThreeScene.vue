<template>
  <div ref="canvasContainer" class="canvas-container">
    <!-- 加载提示 -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <div class="loading-text">模型加载中...</div>
    </div>

    <!-- 错误消息 -->
    <div v-if="apiMessage" class="error-message">
      {{ apiMessage }}
    </div>

    <!-- 相机参数悬浮窗 -->
    <div class="camera-panel">
      <h3>Camera Parameters</h3>
      <div class="param-group">
        <div class="param-item">
          <span class="label">Position:</span>
          <span class="value">{{ cameraParams.position }}</span>
        </div>
        <div class="param-item">
          <span class="label">Rotation:</span>
          <span class="value">{{ cameraParams.rotation }}</span>
        </div>
        <div class="param-item">
          <span class="label">FOV:</span>
          <span class="value">{{ cameraParams.fov }}°</span>
        </div>
        <div class="param-item">
          <span class="label">Aspect:</span>
          <span class="value">{{ cameraParams.aspect }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader'
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader'

const props = defineProps({
  modelUrl: {
    type: String,
    default: ''
  }
})

const canvasContainer = ref(null)
const loading = ref(false)
const cameraParams = reactive({
  position: '0, 0, 0',
  rotation: '0, 0, 0',
  fov: 0,
  aspect: 0
})

const apiMessage = ref('');

// Three.js 核心对象
let scene = null
let camera = null
let renderer = null
let controls = null
let model = null
let animationFrameId = null

// 初始化场景
function initThree() {
  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000)
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  
  // 初始化渲染器
  renderer.setSize(window.innerWidth, window.innerHeight)
  renderer.setPixelRatio(window.devicePixelRatio)
  renderer.setClearColor(0x000000, 0.1) // 设置半透明背景
  canvasContainer.value.appendChild(renderer.domElement)

  // 初始化轨道控制器
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.05

  // 添加基础灯光
  const ambientLight = new THREE.AmbientLight(0xffffff, 0.8)
  scene.add(ambientLight)
  const directionalLight = new THREE.DirectionalLight(0xffffff, 0.6)
  directionalLight.position.set(10, 10, 10)
  scene.add(directionalLight)

  // 设置相机初始位置
  camera.position.set(0, 0, 5)
}

// 更新相机参数显示
function updateCameraParams() {
  // 格式化位置数据
  cameraParams.position = `${camera.position.x.toFixed(2)}, ${camera.position.y.toFixed(2)}, ${camera.position.z.toFixed(2)}`
  
  // 将弧度转换为角度并格式化
  const rotation = camera.rotation
  const rotationDeg = {
    x: THREE.MathUtils.radToDeg(rotation.x).toFixed(2),
    y: THREE.MathUtils.radToDeg(rotation.y).toFixed(2),
    z: THREE.MathUtils.radToDeg(rotation.z).toFixed(2)
  }
  cameraParams.rotation = `${rotationDeg.x}°, ${rotationDeg.y}°, ${rotationDeg.z}°`
  
  // 其他参数
  cameraParams.fov = camera.fov.toFixed(2)
  cameraParams.aspect = camera.aspect.toFixed(2)
}

// 清除现有模型
function clearCurrentModel() {
  if (model) {
    scene.remove(model)
    if (model.geometry) model.geometry.dispose()
    if (model.material) {
      if (Array.isArray(model.material)) {
        model.material.forEach(material => material.dispose())
      } else {
        model.material.dispose()
      }
    }
    model = null
  }
}

// 加载模型
async function loadModel(url) {
  if (!url) {
    console.warn('模型URL为空，无法加载模型')
    return
  }
  
  console.log('开始加载模型，URL:', url)
  loading.value = true
  apiMessage.value = '' // 清空之前的错误信息
  clearCurrentModel()
  
  const loader = new GLTFLoader()
  const dracoLoader = new DRACOLoader()
  dracoLoader.setDecoderPath('https://www.gstatic.com/draco/versioned/decoders/1.5.6/')
  loader.setDRACOLoader(dracoLoader)
  
  // 添加加载管理器以便监控加载进度和错误
  const loadingManager = new THREE.LoadingManager(
    // onLoad
    () => {
      console.log('加载完成')
    },
    // onProgress
    (url, itemsLoaded, itemsTotal) => {
      console.log(`正在加载: ${url}. 已加载 ${itemsLoaded}/${itemsTotal}`)
    },
    // onError
    (url) => {
      console.error(`加载出错: ${url}`)
      apiMessage.value = `加载资源失败: ${url}`
    }
  )
  loader.manager = loadingManager
  
  try {
    console.log('使用GLTFLoader加载URL:', url)
    
    // 使用fetch先检查URL是否可访问
    try {
      const response = await fetch(url, { method: 'HEAD' })
      if (!response.ok) {
        console.error(`URL不可访问: ${url}, 状态码: ${response.status}`)
        apiMessage.value = `模型文件不可访问，状态码: ${response.status}`
        throw new Error(`URL不可访问: ${response.statusText}`)
      }
    } catch (fetchError) {
      console.error('检查URL可访问性失败:', fetchError)
      // 继续尝试加载，可能是CORS问题
    }
    
    const gltf = await loader.loadAsync(url)
    console.log('模型加载成功:', gltf)
    model = gltf.scene
    scene.add(model)
    
    // 自动调整相机位置
    const box = new THREE.Box3().setFromObject(model)
    const center = box.getCenter(new THREE.Vector3())
    const size = box.getSize(new THREE.Vector3())
    const maxDim = Math.max(size.x, size.y, size.z)
    const fov = camera.fov * (Math.PI / 180)
    let cameraZ = Math.abs(maxDim / 2 * Math.tan(fov * 2))
    
    camera.position.set(center.x, center.y, center.z + cameraZ * 1.5)
    controls.target.copy(center)
    controls.update()
    
    console.log('模型添加到场景并调整相机完成')
  } catch (error) {
    console.error('模型加载失败:', error)
    console.error('错误详情:', error.message)
    console.error('错误堆栈:', error.stack)
    
    // 显示备用提示
    apiMessage.value = '模型加载失败，请检查控制台错误'
    
    // 尝试加载默认模型
    if (!url.includes('tietu.glb')) {
      console.log('尝试加载默认模型')
      try {
        await loadModel('/tietu.glb')
      } catch (backupError) {
        console.error('备用模型也加载失败:', backupError)
      }
    }
  } finally {
    loading.value = false
  }
}

// 动画循环
function animate() {
  animationFrameId = requestAnimationFrame(animate)
  controls.update()
  updateCameraParams() // 每帧更新参数
  renderer.render(scene, camera)
}

// 窗口大小变化处理
function handleResize() {
  camera.aspect = window.innerWidth / window.innerHeight
  camera.updateProjectionMatrix()
  renderer.setSize(window.innerWidth, window.innerHeight)
}

// 监听模型URL变化
watch(() => props.modelUrl, (newUrl) => {
  if (newUrl) {
    loadModel(newUrl)
  }
}, { immediate: true })

// 生命周期管理
onMounted(() => {
  initThree()
  if (props.modelUrl) {
    loadModel(props.modelUrl)
  } else {
    // 如果没有提供URL，加载默认模型
    loadModel('/tietu.glb')
  }
  animate()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  cancelAnimationFrame(animationFrameId)
  window.removeEventListener('resize', handleResize)
  if (controls) controls.dispose()
  if (renderer) renderer.dispose()
  clearCurrentModel()
  canvasContainer.value?.removeChild(renderer?.domElement)
})
</script>

<style scoped>
.canvas-container {
  width: 100vw;
  height: 100vh;
  position: relative;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 100;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 5px solid rgba(255,255,255,0.3);
  border-radius: 50%;
  border-top-color: #fff;
  animation: spin 1s ease-in-out infinite;
}

.loading-text {
  margin-top: 20px;
  color: white;
  font-size: 18px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.camera-panel {
  position: fixed;
  top: 20px;
  right: 20px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  padding: 15px;
  border-radius: 8px;
  font-family: Arial, sans-serif;
  min-width: 250px;
  backdrop-filter: blur(5px);
}

.camera-panel h3 {
  margin: 0 0 10px 0;
  font-size: 16px;
  color: #00ff88;
}

.param-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.param-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.label {
  font-weight: bold;
  margin-right: 15px;
  color: #aaa;
}

.value {
  color: #fff;
  font-family: 'Courier New', monospace;
}

.error-message {
  position: absolute;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  background-color: rgba(255, 0, 0, 0.7);
  color: white;
  padding: 10px 20px;
  border-radius: 4px;
  font-size: 16px;
  z-index: 100;
  backdrop-filter: blur(5px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
}
</style>