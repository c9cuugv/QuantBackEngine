package com.quantbackengine.backend;

import com.quantbackengine.backend.config.PythonBridgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PythonBridgeProperties.class)
public class QuantBackEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantBackEngineApplication.class, args);
    }
}
