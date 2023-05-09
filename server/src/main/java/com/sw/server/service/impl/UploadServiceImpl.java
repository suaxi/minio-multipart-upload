package com.sw.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.server.pojo.FileUploadInfo;
import com.sw.server.service.UploadService;
import com.sw.server.utils.MinioUtils;
import com.sw.server.utils.RedisUtils;
import io.minio.ListPartsResponse;
import io.minio.messages.Part;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Wang Hao
 * @date 2023/5/3 17:23
 */
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
        String bucketName = minioUtils.createBucket(fileUploadInfo.getFileType());
        if (fileUploadInfo.getPartCount() == 1) {
            return minioUtils.getUploadObjectUrl(fileUploadInfo.getFileName(), bucketName);
        } else {
            return minioUtils.initMultiPartUpload(fileUploadInfo, fileUploadInfo.getFileName(), fileUploadInfo.getPartCount(), fileUploadInfo.getContentType(), bucketName);
        }
    }

    @Override
    public boolean mergeMultipartUpload(FileUploadInfo fileUploadInfo) {
        String bucketName = minioUtils.createBucket(fileUploadInfo.getFileType());
        return minioUtils.mergeMultipartUpload(fileUploadInfo.getFileName(), fileUploadInfo.getUploadId(), bucketName);
    }

    @Override
    public List<Integer> getByFileSha256(String sha256) throws JsonProcessingException {
        Object obj = redisUtils.get(sha256);
        FileUploadInfo fileUploadInfo = null;
        if (obj != null) {
            fileUploadInfo = objectMapper.readValue(objectMapper.writeValueAsString(obj), new TypeReference<FileUploadInfo>() {
            });
        }
        if (fileUploadInfo == null) {
            return Collections.emptyList();
        }
        String bucketName = minioUtils.createBucket(fileUploadInfo.getFileType());
        ListPartsResponse listPartsResponse = minioUtils.getByFileSha256(fileUploadInfo.getFileName(), fileUploadInfo.getUploadId(), bucketName);
        return listPartsResponse.result().partList().stream().map(Part::partNumber).collect(Collectors.toList());
    }

    @Override
    public String getFilePath(String bucketName, String fileName) {
        return minioUtils.getFilePath(bucketName, fileName);
    }

    @Override
    public String upload(MultipartFile file, String bucketName) {
        minioUtils.upload(file, bucketName);
        return this.getFilePath(bucketName, file.getName());
    }
}
