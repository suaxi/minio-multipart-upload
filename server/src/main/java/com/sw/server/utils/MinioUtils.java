package com.sw.server.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultimap;
import com.sw.server.config.CustomMinioClient;
import com.sw.server.pojo.FileUploadInfo;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Wang Hao
 * @date 2023/5/3 16:46
 */
@Slf4j
@Component
public class MinioUtils {

    @Value(value = "${oss.minio.endpoint}")
    private String endpoint;

    @Value(value = "${oss.minio.accessKey}")
    private String accessKey;

    @Value(value = "${oss.minio.secretKey}")
    private String secretKey;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ObjectMapper objectMapper;

    private CustomMinioClient customMinioClient;

    @PostConstruct
    public void init() {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        customMinioClient = new CustomMinioClient(minioClient);
    }


    /**
     * 创建桶
     *
     * @return
     */
    public String createBucket(String bucketName) {
        try {
            BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(bucketName).build();
            //如果桶存在
            if (customMinioClient.bucketExists(bucketExistsArgs)) {
                return bucketName;
            }
            MakeBucketArgs makeBucketArgs = MakeBucketArgs.builder().bucket(bucketName).build();
            customMinioClient.makeBucket(makeBucketArgs);
            return bucketName;
        } catch (Exception e) {
            log.error("创建桶失败：{}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 单文件签名上传
     *
     * @param objectName 文件全路径名称
     * @param bucketName 桶名称
     * @return /
     */
    public Map<String, Object> getUploadObjectUrl(String objectName, String bucketName) {
        try {
            log.info("开始单文件上传: [{}] - [{}]", objectName, bucketName);
            Map<String, Object> result = new HashMap<>(2);
            List<String> partList = new ArrayList<>();
            String url = customMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(1, TimeUnit.DAYS)
                            .build());
            log.info("单文件上传成功");
            partList.add(url);
            result.put("uploadId", "SingleFileUpload");
            result.put("urlList", partList);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败:", e);
        }
    }

    /**
     * 文件上传
     *
     * @param file       文件
     * @param bucketName 桶名称
     * @return
     */
    public String upload(MultipartFile file, String bucketName) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw new RuntimeException("上传失败，文件不能为空！");
        }
        String objectName = file.getName();
        try {
            PutObjectArgs objectArgs = PutObjectArgs.builder().bucket(bucketName).object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1).contentType(file.getContentType()).build();
            //文件名称相同会覆盖
            customMinioClient.putObject(objectArgs);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        //查看文件地址
        GetPresignedObjectUrlArgs build = GetPresignedObjectUrlArgs.builder().bucket(bucketName).object(objectName).method(Method.GET).build();
        String url = null;
        try {
            url = customMinioClient.getPresignedObjectUrl(build);
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException |
                 ServerException e) {
            e.printStackTrace();
        }
        return url;
    }


    /**
     * 初始化分片上传
     *
     * @param fileUploadInfo
     * @param objectName     文件全路径名称
     * @param partCount      分片数量
     * @param contentType    类型，如果类型使用默认流会导致无法预览
     * @param bucketName     桶名称
     * @return Map <String, Object>
     */
    public Map<String, Object> initMultiPartUpload(FileUploadInfo fileUploadInfo, String objectName, int partCount, String contentType, String bucketName) {
        log.info("开始初始化分片上传数据");
        Map<String, Object> result = new HashMap<>(2);
        try {
            if (StringUtils.isBlank(contentType)) {
                contentType = "application/octet-stream";
            }
            HashMultimap<String, String> headers = HashMultimap.create();

            headers.put("Content-Type", contentType);

            //获取uploadId
            String uploadId = customMinioClient.initMultiPartUpload(bucketName, null, objectName, headers, null);

            result.put("uploadId", uploadId);

            fileUploadInfo.setUploadId(uploadId);

            //redis保存文件信息
            redisUtils.set(fileUploadInfo.getMd5(), objectMapper.writeValueAsString(fileUploadInfo), 30, TimeUnit.MINUTES);

            List<String> partList = new ArrayList<>();
            Map<String, String> reqParams = new HashMap<>(2);
            reqParams.put("uploadId", uploadId);
            for (int i = 1; i <= partCount; i++) {
                reqParams.put("partNumber", String.valueOf(i));
                String uploadUrl = customMinioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.PUT)
                                .bucket(bucketName)
                                .object(objectName)
                                .expiry(1, TimeUnit.DAYS)
                                .extraQueryParams(reqParams)
                                .build());
                partList.add(uploadUrl);
            }
            log.info("文件初始化分片上传成功");
            result.put("urlList", partList);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("初始化分片上传失败：", e);
        }
    }


    /**
     * 分片上传完后合并
     *
     * @param objectName 文件全路径名称
     * @param uploadId   返回的uploadId
     * @param bucketName 桶名称
     * @return boolean
     */
    public boolean mergeMultipartUpload(String objectName, String uploadId, String bucketName) {
        try {
            log.info("合并分片上传数据");
            Part[] parts = new Part[1000];
            //查询上传后的分片数据
            ListPartsResponse partResult = customMinioClient.listMultipart(bucketName, null, objectName, 1000, 0, uploadId, null, null);
            int partNumber = 1;
            for (Part part : partResult.result().partList()) {
                parts[partNumber - 1] = new Part(partNumber, part.etag());
                partNumber++;
            }
            //合并分片
            customMinioClient.mergeMultipartUpload(bucketName, null, objectName, uploadId, parts, null, null);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("合并分片数据失败：", e);
        }
    }

    /**
     * 通过 sha256 获取上传中的分片信息
     *
     * @param objectName 文件全路径名称
     * @param uploadId   返回的uploadId
     * @param bucketName 桶名称
     * @return
     */
    public ListPartsResponse getByFileSha256(String objectName, String uploadId, String bucketName) {
        try {
            log.info("查询minio上传分片数据");
            return customMinioClient.listMultipart(bucketName, null, objectName, 1000, 0, uploadId, null, null);
        } catch (Exception e) {
            throw new RuntimeException("查询minio上传分片数据失败：" + e);
        }
    }


    /**
     * 获取文件下载地址
     *
     * @param bucketName 桶名称
     * @param fileName   文件名
     * @return
     */
    public String getFilePath(String bucketName, String fileName) {
        return endpoint + "/" + bucketName + "/" + fileName;
    }

}
