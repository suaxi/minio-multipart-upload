import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 3000
})

request.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    ElMessage.error(error.response.data.message)
    return Promise.reject(error)
  }
)

request.interceptors.request.use(
  config => {
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

export default request
