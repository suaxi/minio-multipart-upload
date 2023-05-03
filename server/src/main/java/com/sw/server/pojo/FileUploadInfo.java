package com.sw.server.pojo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Wang Hao
 * @date 2023/5/3 16:10
 */
@Data
@ApiModel("上传文件信息")
public class FileUploadInfo implements Serializable {

    @ApiModelProperty("文件名")
    private String fileName;

    @ApiModelProperty("文件大小")
    private Double fileSize;

    @ApiModelProperty("contentType")
    private String contentType;

    @ApiModelProperty("分片数量")
    private Integer partCount;

    @ApiModelProperty("上传id")
    private String uploadId;

    @ApiModelProperty("md5")
    private String md5;

    @ApiModelProperty("文件类型")
    private String fileType;
}
