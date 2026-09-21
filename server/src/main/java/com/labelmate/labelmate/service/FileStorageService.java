package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.FileUploadResponse;
import com.labelmate.labelmate.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded dataset files on the local filesystem (development
 * default; replaceable with object storage later without touching callers).
 *
 * <p>Uploads are untrusted: the extension is restricted to dataset text
 * formats, the size is bounded, the stored name is sanitized and
 * collision-proofed, and the resolved path is verified to stay inside the
 * upload directory so a crafted filename can never escape it.
 */
@Service
public class FileStorageService {

    /** Mirrors the 50 MB client-side cap so oversized files fail fast. */
    static final long MAX_BYTES = 50L * 1024 * 1024;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Map<String, String> IMAGE_MEDIA_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif");

    private final Path uploadDir;
    private final Path imagesDir;
    private final long maxImageBytes;

    public FileStorageService(
            @Value("${app.upload.dir:uploads}") String uploadDir,
            @Value("${app.upload.max-image-mb:5}") long maxImageMb) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        // Images live in their own subdirectory so the public /uploads/images/**
        // handler below never exposes raw dataset files stored next to them.
        this.imagesDir = this.uploadDir.resolve("images").normalize();
        this.maxImageBytes = maxImageMb * 1024 * 1024;
    }

    /**
     * Stores one dataset file and returns the metadata the dataset form needs.
     */
    public FileUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file too large — max 50 MB");
        }
        String original = file.getOriginalFilename();
        String base = original == null ? "" : Paths.get(original).getFileName().toString();
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
        String lower = cleaned.toLowerCase();
        if (cleaned.isBlank() || !(lower.endsWith(".csv") || lower.endsWith(".txt"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "only .csv or .txt files are accepted");
        }
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(cleaned.length() - 200);
        }
        String stored = System.currentTimeMillis() + "_" + cleaned;
        try {
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(stored).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file name");
            }
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), sha256)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new FileUploadResponse(
                    stored,
                    "uploads/" + stored,
                    Files.size(target),
                    HexFormat.of().formatHex(sha256.digest()));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "could not store file");
        }
    }

    public record StoredImage(String imageUrl, String mediaType, String originalName) {
    }

    /**
     * Locates an already-stored dataset file by its recorded path. The
     * recorded path carries an {@code uploads/} prefix while the file itself
     * sits directly under the upload directory, so both the verbatim path
     * and its bare filename are tried — always confined to the upload
     * directory. Returns empty for blank paths, traversal attempts, or files
     * that were never uploaded; callers treat that as a metadata-only record
     * rather than an error.
     */
    public Optional<Path> findStoredFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return Optional.empty();
        }
        if (filePath.contains("..") || filePath.contains("\0")) {
            return Optional.empty();
        }
        Path direct = uploadDir.resolve(filePath).normalize();
        if (direct.startsWith(uploadDir) && Files.isRegularFile(direct)) {
            return Optional.of(direct);
        }
        String base = Paths.get(filePath).getFileName().toString();
        if (!base.isBlank()) {
            Path flat = uploadDir.resolve(base).normalize();
            if (flat.startsWith(uploadDir) && Files.isRegularFile(flat)) {
                return Optional.of(flat);
            }
        }
        return Optional.empty();
    }

    /**
     * Stores one labeling image and returns its public URL. Images are
     * untrusted: the extension is whitelisted, the size is bounded, the
     * stored name is a UUID (never the client filename), and the bytes must
     * decode as a real image — except WebP, which stock-JDK ImageIO cannot
     * decode and is verified via its RIFF....WEBP magic header instead.
     */
    public StoredImage storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "image file is empty");
        }
        if (file.getSize() > maxImageBytes) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "image too large — max " + (maxImageBytes / 1024 / 1024) + " MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String extension = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "only .jpg, .jpeg, .png, .webp or .gif images are accepted");
        }
        String stored = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(imagesDir);
            Path target = imagesDir.resolve(stored).normalize();
            if (!target.startsWith(imagesDir)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file name");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            byte[] bytes = Files.readAllBytes(target);
            boolean valid = "webp".equals(extension) ? isWebP(bytes) : ImageIO.read(new ByteArrayInputStream(bytes)) != null;
            if (!valid) {
                Files.deleteIfExists(target);
                throw new ApiException(HttpStatus.BAD_REQUEST, "file is not a valid image");
            }
            return new StoredImage("/uploads/images/" + stored, IMAGE_MEDIA_TYPES.get(extension), original);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "could not store image");
        }
    }

    /**
     * Resolves a stored {@code /uploads/images/...} URL back to a file inside
     * the images directory. Anything else yields 400/404 so a crafted
     * reference can never escape the directory (path traversal) or probe for
     * files.
     */
    public Path resolveImage(String imageUrl) {
        final String prefix = "/uploads/images/";
        if (imageUrl == null || !imageUrl.startsWith(prefix)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unknown image reference");
        }
        String name = imageUrl.substring(prefix.length());
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unknown image reference");
        }
        Path path = imagesDir.resolve(name).normalize();
        if (!path.startsWith(imagesDir) || !Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "image not found");
        }
        return path;
    }

    private static boolean isWebP(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        return bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
}
