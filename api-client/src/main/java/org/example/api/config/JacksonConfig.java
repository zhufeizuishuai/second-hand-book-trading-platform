package org.example.api.config;

import cn.hutool.json.JSONNull;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            // 把 Hutool 的 JSONNull 序列化为 null
            module.addSerializer(JSONNull.class, ToStringSerializer.instance);
            builder.modules(module);
            // 关键：忽略空对象序列化异常
            builder.failOnEmptyBeans(false);
        };
    }
}