package com.lqr.papermind;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 论文 RAG 服务启动类。
 */
@MapperScan("com.lqr.papermind.**.mapper")
@EnableScheduling
@SpringBootApplication
public class PaperMindApplication {
    /**
     * 应用程序入口方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PaperMindApplication.class, args);
    }

}