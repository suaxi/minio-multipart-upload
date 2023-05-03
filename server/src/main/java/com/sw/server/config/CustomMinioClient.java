package com.sw.server.config;

import com.google.common.collect.Multimap;
import io.minio.CreateMultipartUploadResponse;
import io.minio.ListPartsResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.errors.*;
import io.minio.messages.Part;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * @author Wang Hao
 * @date 2023/5/3 16:34
 */
public class CustomMinioClient extends MinioClient {

    public CustomMinioClient(MinioClient client) {
        super(client);
    }

    /**
     * 初始化分片上传、获取uploadId
     *
     * @param bucket           桶
     * @param region           区域
     * @param object           上传对象
     * @param headers          headers
     * @param extraQueryParams extraQueryParams
     * @return
     * @throws ServerException
     * @throws InsufficientDataException
     * @throws ErrorResponseException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws InvalidKeyException
     * @throws XmlParserException
     * @throws InvalidResponseException
     * @throws InternalException
     */
    public String initMultiPartUpload(String bucket, String region, String object, Multimap<String, String> headers, Multimap<String, String> extraQueryParams) throws ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, IOException, InvalidKeyException, XmlParserException, InvalidResponseException, InternalException {
        CreateMultipartUploadResponse response = this.createMultipartUpload(bucket, region, object, headers, extraQueryParams);
        return response.result().uploadId();
    }

    /**
     * 合并分片
     *
     * @param bucketName       桶
     * @param region           区域
     * @param objectName       上传对象
     * @param uploadId         上传id
     * @param parts            块
     * @param extraHeaders     extraHeaders
     * @param extraQueryParams extraQueryParams
     * @return
     * @throws ServerException
     * @throws InsufficientDataException
     * @throws ErrorResponseException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws InvalidKeyException
     * @throws XmlParserException
     * @throws InvalidResponseException
     * @throws InternalException
     */
    public ObjectWriteResponse mergeMultipartUpload(String bucketName, String region, String objectName, String uploadId, Part[] parts, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, IOException, InvalidKeyException, XmlParserException, InvalidResponseException, InternalException {
        return this.completeMultipartUpload(bucketName, region, objectName, uploadId, parts, extraHeaders, extraQueryParams);
    }

    /**
     * 查询当前上传后的分片信息
     *
     * @param bucketName       桶
     * @param region           区域
     * @param objectName       文件名称
     * @param maxParts         分片数量
     * @param partNumberMarker 分片起始值
     * @param uploadId         上传的 uploadId
     * @param extraHeaders     extraHeaders
     * @param extraQueryParams extraQueryParams
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InsufficientDataException
     * @throws IOException
     * @throws InvalidKeyException
     * @throws ServerException
     * @throws XmlParserException
     * @throws ErrorResponseException
     * @throws InternalException
     * @throws InvalidResponseException
     */
    public ListPartsResponse listMultipart(String bucketName, String region, String objectName, Integer maxParts, Integer partNumberMarker, String uploadId, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws NoSuchAlgorithmException, InsufficientDataException, IOException, InvalidKeyException, ServerException, XmlParserException, ErrorResponseException, InternalException, InvalidResponseException {
        return this.listParts(bucketName, region, objectName, maxParts, partNumberMarker, uploadId, extraHeaders, extraQueryParams);
    }
}
