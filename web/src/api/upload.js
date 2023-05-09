import request from '@/utils/request'

// 上传
export function upload(data) {
  return request({
    url: '/file/upload',
    method: 'post',
    data
  })
}

// 上传校验
export function check(md5) {
  return request({
    url: `file/check?md5=${md5}`,
    method: 'get'
  })
}

// 初始化分片
export function init(data) {
  return request({
    url: '/file/init',
    method: 'post',
    data
  })
}

// 合并分片数据
export function merge(data) {
  return request({
    url: '/file/merge',
    method: 'post',
    data
  })
}
