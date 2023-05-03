package com.sw.server.controller;

import io.swagger.annotations.Api;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Wang Hao
 * @date 2023/5/3 16:14
 */
@RestController
@RequestMapping("/test")
@Api(tags = "测试")
public class IndexController {

    @GetMapping
    public ResponseEntity<String> test() {
        return new ResponseEntity<>("孙笑川", HttpStatus.OK);
    }

}
