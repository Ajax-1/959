import axios from 'axios'

// 创建axios实例
const service = axios.create({
  baseURL: 'http://localhost:8080', // 显式指定后端API地址
  timeout: 10000 // 请求超时时间
})

// 请求拦截器
service.interceptors.request.use(config => {
  // 在这里可以统一处理请求头，例如添加token
  // config.headers.Authorization = 'Bearer your_token'
  console.log('正在发送请求：', config.url, config.params || config.data)
  return config
})

// 响应拦截器
service.interceptors.response.use(
  response => {
    // 处理响应数据
    console.log('接收到响应：', response.data)
    return response.data
  },
  error => {
    // 统一处理错误
    console.error('请求错误:', error)
    
    // 添加详细日志以便调试
    if (error.response) {
      // 请求成功发出且服务器也响应了状态码，但状态码超出了2xx的范围
      console.error('错误状态:', error.response.status)
      console.error('错误数据:', error.response.data)
      console.error('错误详情:', error.response.statusText)
      console.error('请求方法:', error.config.method)
      console.error('请求URL:', error.config.url)
      console.error('请求数据:', error.config.data)
      console.error('请求头:', error.config.headers)
    } else if (error.request) {
      // 请求已经成功发起，但没有收到响应
      console.error('未收到响应，请求详情:', error.request)
    } else {
      // 发送请求时出了点问题
      console.error('请求配置错误:', error.message)
    }
    
    return Promise.reject(error)
  }
)

// 封装GET/POST方法
export const get = (url, params, config = {}) => {
  return service.get(url, { ...config, params })
}

export const post = (url, data, config = {}) => {
  return service.post(url, data, config)
}

// 同时导出命名函数和默认对象
export default {
  get,
  post,
  service
}