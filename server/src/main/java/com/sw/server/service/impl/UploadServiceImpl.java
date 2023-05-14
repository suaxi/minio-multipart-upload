package com.sw.server.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.server.pojo.FileUploadInfo;
import com.sw.server.service.UploadService;
import com.sw.server.utils.MinioUtils;
import com.sw.server.utils.RedisUtils;
import io.minio.ListPartsResponse;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Wang Hao
 * @date 2023/5/3 17:23
 */
@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    @Autowired
    private MinioUtils minioUtils;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Map<String, Object> initMultiPartUpload(FileUploadInfo fileUploadInfo) {
        String bucketName = minioUtils.createBucket();
        if (fileUploadInfo.getPartCount() == 1) {
            return minioUtils.getUploadObjectUrl(fileUploadInfo.getFileName(), bucketName);
        } else {
            return minioUtils.initMultiPartUpload(fileUploadInfo, fileUploadInfo.getFileName(), fileUploadInfo.getPartCount(), fileUploadInfo.getContentType(), bucketName);
        }
    }

    @Override
    public boolean mergeMultipartUpload(FileUploadInfo fileUploadInfo) {
        String bucketName = minioUtils.createBucket();
        return minioUtils.mergeMultipartUpload(fileUploadInfo.getFileName(), fileUploadInfo.getUploadId(), bucketName);
    }

    @Override
    public Map<String, Object> getByFileSha256(String sha256) {
        Map<String, Object> result = new HashMap<>(2);
        Object obj = redisUtils.get(sha256);
        FileUploadInfo fileUploadInfo = null;
        if (obj != null) {
            fileUploadInfo = objectMapper.convertValue(obj, new TypeReference<FileUploadInfo>() {
            });
        }
        if (fileUploadInfo == null) {
            return null;
        }
        //上传文件信息持久化存储到数据库之后，此处应该使用在附件表查询是否已有md5对应的文件的方式
        try {
            String bucketName = minioUtils.createBucket();
            ListPartsResponse listPartsResponse = minioUtils.getByFileSha256(fileUploadInfo.getFileName(), fileUploadInfo.getUploadId(), bucketName);
            result.put("code", 2);
            result.put("data", listPartsResponse.result().partList().stream().map(Part::partNumber).collect(Collectors.toList()));
            return result;
        } catch (Exception e) {
            log.info("md5：[{}] 对应的文件已上传完成", sha256);
            result.put("code", 1);
            result.put("data", minioUtils.getFilePath(fileUploadInfo.getFileName()));
            return result;
        }
    }

    @Override
    public String getFilePath(String fileName) {
        return minioUtils.getFilePath(fileName);
    }

    @Override
    public String upload(MultipartFile file, String bucketName) {
        minioUtils.upload(file, bucketName);
        return this.getFilePath(file.getName());
    }
}
