import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 3000
})

request.interceptors.response.use(
  response => {
    const res = response
    if (res.status === 200) {
      return res.data
    }
  },
  error => {
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
