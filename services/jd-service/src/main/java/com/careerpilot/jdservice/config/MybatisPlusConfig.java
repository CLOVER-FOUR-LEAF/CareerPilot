package com.careerpilot.jdservice.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 分页插件。
 *
 * <p>没有这个 Bean 时 {@code selectPage} 不会生成 LIMIT，
 * 接口会一次性把全表拉出来——简历/JD 多起来之后直接把内存打爆。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限，防止前端传 size=999999 拖库
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
