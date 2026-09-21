package com.labelmate.labelmate.config;

import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves stored dataset images read-only under {@code /uploads/images/**} so
 * the annotation workspace can display them with plain {@code <img>} tags.
 * Only the images subdirectory is exposed — raw dataset files stored next to
 * it stay private. Files are reachable solely by their exact stored (UUID)
 * name: no directory listing, no write access.
 */
@Configuration
public class UploadWebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public UploadWebConfig(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).resolve("images").toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/images/**").addResourceLocations(location);
    }
}
