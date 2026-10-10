package com.flowerfarm.config;

import com.flowerfarm.model.FarmProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds the customer-owned farm identity without coupling core layers to Spring config. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
public class FarmProfileConfig {

    @Bean
    @ConfigurationProperties(prefix = "flowerfarm.profile")
    FarmProfile farmProfile() {
        return new FarmProfile();
    }
}
