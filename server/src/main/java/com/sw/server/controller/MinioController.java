package com.sw.server.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sw.server.pojo.FileUploadInfo;
import com.sw.server.service.UploadService;
import com.sw.server.utils.MinioUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Wang Hao
 * @date 2023/5/3 17:37
 */
@RestController
@RequestMapping("/file")
@Api(tags = "文件上传")
public class MinioController {

    @Autowired
    private UploadService uploadService;

    @Autowired
    private MinioUtils minioUtils;


    @GetMapping("/check")
    @ApiOperation("根据md5查询文件")
    public ResponseEntity<?> checkFileUploadedByMd5(@RequestParam("md5") String md5) throws JsonProcessingException {
        if (StringUtils.isEmpty(md5)) {
            throw new IllegalArgumentException("md5不能为空！");
        }
        return new ResponseEntity<>(uploadService.getByFileSha256(md5), HttpStatus.OK);
    }

    @PostMapping("/init")
    @ApiOperation("分片初始化")
    public ResponseEntity<Map<String, Object>> initMultiPartUpload(@RequestBody FileUploadInfo fileUploadInfo) {
        return new ResponseEntity<>(uploadService.initMultiPartUpload(fileUploadInfo), HttpStatus.OK);
    }

    @PostMapping("/merge")
    @ApiOperation("分片数据合并")
    public ResponseEntity<String> completeMultiPartUpload(@RequestBody FileUploadInfo fileUploadInfo) {
        //合并文件
        boolean result = uploadService.mergeMultipartUpload(fileUploadInfo);
        //获取上传文件地址
        if (result) {
            String filePath = uploadService.getFilePath(fileUploadInfo.getFileType().toLowerCase(), fileUploadInfo.getFileName());
            return new ResponseEntity<>(filePath, HttpStatus.OK);
        }
        return new ResponseEntity<>(null, HttpStatus.OK);
    }


    @PostMapping("/upload")
    @ApiOperation("上传")
    public ResponseEntity<List<String>> uploadScreenshot(@RequestPart("files") MultipartFile[] files, @RequestParam("buckName") String buckName) {
        List<String> uploadUrlList = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                uploadUrlList.add(uploadService.upload(file, buckName));
            }
        }
        return new ResponseEntity<>(uploadUrlList, HttpStatus.OK);
    }


    @PostMapping("/create/{bucketName}")
    @ApiOperation("创建桶")
    public ResponseEntity<String> createBucket(@PathVariable("bucketName") String bucketName) {
        return new ResponseEntity<>(minioUtils.createBucket(bucketName), HttpStatus.OK);
    }
}
