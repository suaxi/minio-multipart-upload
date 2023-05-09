const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8088',
        // 路径重写
        pathRewrite: { '^/api': '' },
        ws: true,
        // 用于空值请求头中的host值（默认值为true）
        changeOrigin: true
      }
    }
  }
})
