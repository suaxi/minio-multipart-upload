<template>
  <div class="app">
    <h2>分片上传示例</h2>

    <el-upload
      ref="upload"
      class="upload-demo"
      action="http://127.0.0.1:8088/file/upload"
      :on-remove="handleRemove"
      :on-change="handleFileChange"
      :file-list="uploadFileList"
      :show-file-list="false"
      :auto-upload="false"
      multiple
    >
      <template #trigger>
        <el-button type="primary" plain>选择文件</el-button>
      </template>
      <el-button style="margin-left: 10px;" type="success" plain @click="handler">上传</el-button>
      <el-button type="danger" plain @click="clearFileHandler">清空</el-button>
    </el-upload>
    <!-- 文件列表 -->
    <div class="file-list-wrapper">
      <el-collapse>
        <el-collapse-item v-for="(item, index) in uploadFileList" :key="index">
          <template #title>
            <div class="upload-file-item">
              <div class="file-info-item file-name" :title="item.name">{{ item.name }}</div>
              <div class="file-info-item file-size">{{ transformByte(item.size) }}</div>
              <div class="file-info-item file-progress">
                <span class="file-progress-label" />
                <el-progress :percentage="item.uploadProgress" class="file-progress-value" />
              </div>
              <div class="file-info-item file-size"><span />
                <el-tag v-if="item.status === '等待上传'" size="small" type="info">等待上传</el-tag>
                <el-tag v-else-if="item.status === '校验md5'" size="small" type="warning">校验MD5</el-tag>
                <el-tag v-else-if="item.status === '正在上传'" size="small">正在上传</el-tag>
                <el-tag v-else-if="item.status === '上传成功'" size="small" type="success">上传完成</el-tag>
                <el-tag v-else size="small" type="danger">上传错误</el-tag>
              </div>
            </div>
          </template>
          <div class="file-chunk-list-wrapper">
            <!-- 分片列表 -->
            <el-table :data="item.chunkList" max-height="400" style="width: 100%">
              <el-table-column prop="chunkNumber" label="分片序号" width="180" />
              <el-table-column prop="progress" label="上传进度">
                <template #default="{ row }">
                  <el-progress
                    v-if="!row.status || row.progressStatus === 'normal'"
                    :percentage="row.progress"
                  />
                  <el-progress
                    v-else
                    :percentage="row.progress"
                    :status="row.progressStatus"
                    :text-inside="true"
                    :stroke-width="16"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="180" />
            </el-table>
          </div>

        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script>
import SparkMD5 from 'spark-md5'
import { check, init, merge } from '@/api/upload'
import axios from 'axios'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

export default {
  name: 'MultipartUpload',
  setup() {
    // file_upload_id
    const FILE_UPLOAD_ID_KEY = 'file_upload_id'

    // 分片大小
    const chunkSize = 10 * 1024 * 1024

    // 当前文件下标索引
    let currentFileIndex = 0

    // 状态
    const FileStatus = {
      wait: '等待上传',
      getMd5: '校验md5',
      chip: '正在创建序列',
      uploading: '正在上传',
      success: '上传成功',
      error: '上传错误'
    }

    // 上传并发数
    const simultaneousUploads = ref(3)
    const uploadIdInfo = ref(null)
    const uploadFileList = ref([])
    const imgDataUrl = ref('')

    const transformByte = (size) => {
      if (!size) {
        return '0B'
      }
      const unitSize = 1024
      if (size < unitSize) {
        return size + ' B'
      }
      // KB
      if (size < Math.pow(unitSize, 2)) {
        return (size / unitSize).toFixed(2) + ' K'
      }
      // MB
      if (size < Math.pow(unitSize, 3)) {
        return (size / Math.pow(unitSize, 2)).toFixed(2) + ' MB'
      }
      // GB
      if (size < Math.pow(unitSize, 4)) {
        return (size / Math.pow(unitSize, 3)).toFixed(2) + ' GB'
      }
      // TB
      return (size / Math.pow(unitSize, 4)).toFixed(2) + ' TB'
    }

    /**
     * 开始上传文件
     */
    const handler = () => {
      // 判断文件列表是否为空
      if (uploadFileList.value.length === 0) {
        ElMessage.error('请先选择文件')
        return
      }
      // 当前操作文件
      const currentFile = uploadFileList.value[currentFileIndex]

      // 更新上传标签
      if (currentFile) {
        currentFile.status = FileStatus.getMd5
      }

      // 截取封面图片
      // ScreenshotVideo(currentFile.raw);

      // 计算文件MD5
      if (!currentFile) return
      getFileMd5(currentFile.raw, async(md5, totalChunks) => {
        // 检查是否已上传
        const checkResult = await checkFileUploadedByMd5(md5)
        // 确认上传状态
        if (checkResult && checkResult.code === 1) {
          currentFile.status = FileStatus.success
          currentFile.uploadProgress = 100
          ElMessage.success(`上传成功，文件地址：${checkResult.data}`)
          console.log('文件访问地址：' + checkResult.data)
          return
        } else if (checkResult && checkResult.code === 2) { // "上传中" 状态
          // 获取已上传分片列表
          currentFile.chunkUploadedList = checkResult.data
        } else { // 未上传
          console.log('未上传')
        }

        // 创建分片
        currentFile.status = FileStatus.chip

        // 创建分片
        const fileChunks = createFileChunk(currentFile.raw, chunkSize)

        // 重命名文件
        const fileName = currentFile.name

        // 获取文件类型
        const type = currentFile.name.substring(currentFile.name.lastIndexOf('.') + 1)
        // let type = fileSuffixTypeUtil(currentFile.name)

        const param = {
          fileName: fileName,
          fileSize: currentFile.size,
          chunkSize: chunkSize,
          partCount: totalChunks,
          fileMd5: md5,
          contentType: 'application/octet-stream',
          fileType: type
        }
        // 获取上传url
        uploadIdInfo.value = await getFileUploadUrls(param)
        saveFileUploadId(uploadIdInfo.value.uploadId)

        const uploadUrls = uploadIdInfo.value.urlList
        currentFile.chunkList = []
        // $set(currentFile, 'chunkList', [])

        if (uploadUrls !== undefined) {
          if (fileChunks.length !== uploadUrls.length) {
            ElMessage.error('文件分片上传地址获取错误')
            return
          }
        }
        // else if (uploadUrls.length === 1) {
        // 	currentFileIndex++;
        // 	//文件上传成功
        // 	//saveFileInfoToDB(currentFile, fileName, uploadIdInfoResult.data.data, md5);

        // 	currentFile.uploadProgress = 100

        // 	currentFile.status = FileStatus.success

        // 	//return;
        // }

        fileChunks.map((chunkItem, index) => {
          currentFile.chunkList.push({
            chunkNumber: index + 1,
            chunk: chunkItem,
            uploadUrl: uploadUrls[index],
            progress: 0,
            status: '—'
          })
        })
        let tempFileChunks = []

        currentFile.chunkList.forEach((item) => {
          tempFileChunks.push(item)
        })

        // 更新状态
        currentFile.status = FileStatus.uploading

        // 上传
        await uploadChunkBase(tempFileChunks)

        // let imgParam = {
        // 	fileName: screenImg.name,
        // 	fileSize: screenImg.size,
        // 	partCount: 1,
        // 	contentType: 'application/octet-stream',
        // 	fileType: 'image',
        // }

        // //上传封面图
        // let screenImgUrl = await getFileUploadUrls(imgParam)

        // 处理分片列表，删除已上传的分片
        tempFileChunks = processUploadChunkList(tempFileChunks)

        console.log('上传完成')

        // 判断是否单文件上传或者分片上传
        if (uploadIdInfo.value.uploadId === 'SingleFileUpload') {
          console.log('单文件上传')
          // 更新状态
          currentFile.status = FileStatus.success
        } else {
          // 合并文件
          const mergeResult = await mergeFile({
            uploadId: uploadIdInfo.value.uploadId,
            fileName: fileName,
            fileMd5: md5,
            fileType: type
          })

          // 合并文件状态
          if (!mergeResult) {
            currentFile.status = FileStatus.error
            ElMessage.error(mergeResult.error)
          } else {
            currentFile.status = FileStatus.success
            console.log('文件访问地址：' + mergeResult)
            ElMessage.success(`上传成功，文件地址：${mergeResult}`)
            // 文件下标偏移
            currentFileIndex++
            // 递归上传下一个文件
            handler()
          }
        }
        // TODO 持久化保存上传文件信息
      })
    }

    /**
     * 清空列表
     */
    const clearFileHandler = () => {
      uploadFileList.value = []
      uploadIdInfo.value = null
      currentFileIndex = 0
    }

    /**
     * 上传文件列表
     * @param {*} file
     * @param {*} fileList
     */
    const handleFileChange = (file, fileList) => {
      // if (!beforeUploadVideo(file)) return

      uploadFileList.value = fileList

      uploadFileList.value.forEach((item) => {
        // 初始化自定义属性
        initFileProperties(item)
      })
    }

    /**
     * 初始化文件属性
     * @param file
     */
    const initFileProperties = (file) => {
      file.chunkList = []
      file.status = FileStatus.wait
      file.progressStatus = 'warning'
      file.uploadProgress = 0
    }

    /**
     * 移除文件列表
     * @param {*} file
     * @param {*} fileList
     */
    const handleRemove = (file, fileList) => {
      uploadFileList.value = fileList
    }

    /**
     * 检查上传文件格式
     * @param {*} file
     */
    const beforeUploadVideo = (file) => {
      const type = file.name.substring(file.name.lastIndexOf('.') + 1)
      if (
        [
          'mp4',
          'ogg',
          'flv',
          'avi',
          'wmv',
          'rmvb'
        ].indexOf(type) === -1
      ) {
        ElMessage.error('请上传正确的视频格式')
        return false
      }
    }

    /**
     * 获取文件新名称
     * @param file
     * @param md5
     * @returns {*}
     */
    const getNewFileName = (file, md5) => {
      return new Date().getTime() + file.name
      // return md5 + '-' + file.name
    }

    /**
     * 分片读取文件 MD5
     */
    const getFileMd5 = (file, callback) => {
      const blobSlice = File.prototype.slice || File.prototype.mozSlice || File.prototype.webkitSlice
      const fileReader = new FileReader()
      // 计算分片数
      const totalChunks = Math.ceil(file.size / chunkSize)
      console.log('总分片数：' + totalChunks)
      let currentChunk = 0
      const spark = new SparkMD5.ArrayBuffer()
      loadNext()
      fileReader.onload = function(e) {
        try {
          spark.append(e.target.result)
        } catch (error) {
          console.log('获取Md5错误：' + currentChunk)
        }
        if (currentChunk < totalChunks) {
          currentChunk++
          loadNext()
        } else {
          callback(spark.end(), totalChunks)
        }
      }
      fileReader.onerror = function() {
        console.warn('读取Md5失败，文件读取错误')
      }

      function loadNext() {
        const start = currentChunk * chunkSize
        const end = ((start + chunkSize) >= file.size) ? file.size : start + chunkSize
        // 注意这里的 fileRaw
        fileReader.readAsArrayBuffer(blobSlice.call(file, start, end))
      }
    }

    /**
     * 文件分片
     * @param file
     * @param size
     * @returns {*[]}
     */
    const createFileChunk = (file, size = chunkSize) => {
      const fileChunkList = []
      let count = 0
      while (count < file.size) {
        fileChunkList.push({
          file: file.slice(count, count + size)
        })
        count += size
      }
      return fileChunkList
    }

    /**
     * 处理即将上传的分片列表，判断是否有已上传的分片，有则从列表中删除
     * @param chunkList
     * @returns {*}
     */
    const processUploadChunkList = (chunkList) => {
      const currentFile = uploadFileList.value[currentFileIndex]
      const chunkUploadedList = currentFile.chunkUploadedList
      if (chunkUploadedList === undefined || chunkUploadedList === null || chunkUploadedList.length === 0) {
        return chunkList
      }
      for (let i = chunkList.length - 1; i >= 0; i--) {
        const chunkItem = chunkList[currentFileIndex]
        for (let j = 0; j < chunkUploadedList.length; j++) {
          if (chunkItem.chunkNumber === chunkUploadedList[j]) {
            chunkList.splice(i, 1)
            break
          }
        }
      }
      return chunkList
    }

    /**
     * 上传分片文件
     * @param chunkList
     * @returns {Promise<unknown>}
     */
    const uploadChunkBase = (chunkList) => {
      let successCount = 0
      const totalChunks = chunkList.length
      return new Promise((resolve, reject) => {
        const handler = () => {
          if (chunkList.length) {
            const chunkItem = chunkList.shift()
            // 直接上传二进制，不需要构造 FormData，否则上传后文件损坏
            axios.put(chunkItem.uploadUrl, chunkItem.chunk.file, {
              // 上传进度处理
              onUploadProgress: checkChunkUploadProgress(chunkItem),
              headers: {
                'Content-Type': 'application/octet-stream'
              }
            }).then(response => {
              if (response.status === 200) {
                console.log('分片：' + chunkItem.chunkNumber + ' 上传成功')
                // 如果长度为1，说明是单文件，直接退出
                // if (chunkList.length === 1) {
                // 	return;
                // }
                successCount++
                // 继续上传下一个分片
                handler()
              } else {
                console.log('上传失败：' + response.status + '，' + response.statusText)
              }
            }).catch(error => {
              // 更新状态
              console.log('分片：' + chunkItem.chunkNumber + ' 上传失败，' + error)
              // 重新添加到队列中
              chunkList.push(chunkItem)
              handler()
            })
          }
          if (successCount >= totalChunks) {
            resolve()
          }
        }
        // 并发
        for (let i = 0; i < simultaneousUploads.value; i++) {
          handler()
        }
      })
    }

    /**
     * 获取文件上传地址
     * @param fileParam
     * @returns {Promise<axios.AxiosResponse<any>>}
     */
    const getFileUploadUrls = (fileParam) => {
      return init(fileParam)
    }

    /**
     * 保存文件上传id
     * @param data
     */
    const saveFileUploadId = (data) => {
      localStorage.setItem(FILE_UPLOAD_ID_KEY, data)
    }

    /**
     * 上传校验
     * @param md5
     * @returns {Promise<unknown>}
     */
    const checkFileUploadedByMd5 = (md5) => {
      return new Promise((resolve, reject) => {
        check(md5).then(response => {
          console.log(response)
          resolve(response)
        }).catch(error => {
          reject(error)
        })
      })
    }

    /**
     * 合并分片数据
     * @param fileParam
     * @returns {Promise<unknown>}
     */
    const mergeFile = (fileParam) => {
      return new Promise((resolve, reject) => {
        merge(fileParam).then(response => {
          console.log(response)
          const data = response
          if (!data) {
            // data.msg = FileStatus.error
            resolve(data)
          } else {
            // data.msg = FileStatus.success
            resolve(data)
          }
        })
        // .catch(error => {
        //     $message.error('合并文件失败：' + error)
        //     file.status = FileStatus.error
        //     reject()
        // })
      })
    }

    /**
     * 检查分片上传进度
     * @param item
     * @returns {(function(*): void)|*}
     */
    const checkChunkUploadProgress = (item) => {
      return p => {
        item.progress = parseInt(String((p.loaded / p.total) * 100))
        updateChunkUploadStatus(item)
      }
    }

    /**
     * 更新上传状态
     * @param item
     */
    const updateChunkUploadStatus = (item) => {
      let status = FileStatus.uploading
      let progressStatus = 'normal'
      if (item.progress >= 100) {
        status = FileStatus.success
        progressStatus = 'success'
      }
      const chunkIndex = item.chunkNumber - 1
      const currentChunk = uploadFileList.value[currentFileIndex].chunkList[chunkIndex]
      // 修改状态
      currentChunk.status = status
      currentChunk.progressStatus = progressStatus
      // 更新状态
      // $set(uploadFileList[currentFileIndex].chunkList, chunkIndex, currentChunk)
      uploadFileList.value[currentFileIndex].chunkList.splice(chunkIndex, 1, currentChunk)
      // 获取文件上传进度
      getCurrentFileProgress()
    }

    /**
     * 获取文件上传进度
     */
    const getCurrentFileProgress = () => {
      const currentFile = uploadFileList.value[currentFileIndex]
      if (!currentFile || !currentFile.chunkList) {
        return
      }
      const chunkList = currentFile.chunkList
      const uploadedSize = chunkList.map((item) => item.chunk.file.size * item.progress).reduce((acc, cur) => acc + cur)
      // 计算方式：已上传大小 / 文件总大小
      currentFile.uploadProgress = parseInt((uploadedSize / currentFile.size).toFixed(2))
      // $set(uploadFileList.value, currentFile)
    }

    return {
      uploadIdInfo,
      uploadFileList,
      imgDataUrl,
      transformByte,
      handler,
      clearFileHandler,
      handleFileChange,
      initFileProperties,
      handleRemove,
      beforeUploadVideo,
      getNewFileName,
      getFileMd5,
      createFileChunk,
      processUploadChunkList,
      uploadChunkBase,
      getFileUploadUrls,
      saveFileUploadId,
      checkFileUploadedByMd5,
      mergeFile,
      checkChunkUploadProgress,
      updateChunkUploadStatus,
      getCurrentFileProgress
    }
  }
}
</script>

<style scoped lang="less">
.container {
  width: 600px;
  margin: 0 auto;
}

.file-list-wrapper {
  margin-top: 20px;
}

h2 {
  text-align: center;
}

.file-info-item {
  margin: 0 10px;
}

.upload-file-item {
  display: flex;
}

.file-progress {
  display: flex;
  align-items: center;
}

.file-progress-value {
  width: 150px;
}

.file-name {
  width: 190px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-size {
  width: 100px;
}

.uploader-example {
  width: 880px;
  padding: 15px;
  margin: 40px auto 0;
  font-size: 12px;
  box-shadow: 0 0 10px rgba(0, 0, 0, .4);
}

.uploader-example .uploader-btn {
  margin-right: 4px;
}

.uploader-example .uploader-list {
  max-height: 440px;
  overflow: auto;
  overflow-x: hidden;
  overflow-y: auto;
}
</style>
