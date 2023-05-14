package com.sw.server.service;

import com.sw.server.pojo.FileUploadInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * @author Wang Hao
 * @date 2023/5/3 17:21
 */
public interface UploadService {


    /**
     * 分片上传初始化
     *
     * @param fileUploadInfo 上传文件信息
     * @return
     */
    Map<String, Object> initMultiPartUpload(FileUploadInfo fileUploadInfo);

    /**
     * 完成分片上传
     *
     * @param fileUploadInfo 上传文件信息
     * @return boolean
     */
    boolean mergeMultipartUpload(FileUploadInfo fileUploadInfo);

    /**
     * 通过 sha256 获取已上传的数据
     *
     * @param sha256 String
     * @return
     */
    Map<String, Object> getByFileSha256(String sha256);

    /**
     * 获取文件地址
     *
     * @param fileName   文件名
     * @return
     */
    String getFilePath(String fileName);


    /**
     * 单文件上传
     *
     * @param file       文件
     * @param bucketName 桶名称
     * @return
     */
    String upload(MultipartFile file, String bucketName);
}
