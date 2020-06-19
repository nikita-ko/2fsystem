package home.work.system;

import home.work.system.FileSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.IOException;

@Configuration
@PropertySource("classpath:application.properties")
public class ContextConfig {
    @Value("${file.system.size}")
    private int fileSystemSize;

    @Bean
    public FileSystem fileSystem() throws IOException {
        return new FileSystem(fileSystemSize);
    }
}
