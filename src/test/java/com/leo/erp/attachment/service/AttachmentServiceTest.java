package com.leo.erp.attachment.service;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.attachment.repository.UploadRuleRepository;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import com.leo.erp.attachment.service.storage.LocalAttachmentStorage;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentServiceTest {

    @TempDir
    Path tempDir;

    private final AttachmentFilenameResolver filenameResolver = new AttachmentFilenameResolver();

    @Test
    void shouldUploadAttachmentToConfiguredLocalDirectory() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentFileRepository repository = attachmentRepository(store);
        AttachmentProperties properties = localProperties();

        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(9001L),
                properties,
                filenameResolver,
                new StubUploadRuleService("renamed-contract.pdf"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties)
        );
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        AttachmentView response = service.upload(file, "PAGE_UPLOAD");

        Path expectedFile = tempDir.resolve("attachments")
                .resolve(String.valueOf(LocalDate.now().getYear()))
                .resolve(String.format("%02d", LocalDate.now().getMonthValue()))
                .resolve("9001")
                .resolve("renamed-contract.pdf");
        assertThat(Files.exists(expectedFile)).isTrue();
        assertThat(Files.readString(expectedFile)).isEqualTo("hello");
        assertThat(response.id()).isEqualTo(9001L);
        assertThat(response.fileName()).isEqualTo("renamed-contract.pdf");
        assertThat(store.get(9001L).getAccessKey()).isNotBlank();
        assertThat(response.previewUrl()).isEqualTo("/api/attachments/9001/preview?accessKey=" + store.get(9001L).getAccessKey());
        assertThat(response.downloadUrl()).isEqualTo("/api/attachments/9001/download?accessKey=" + store.get(9001L).getAccessKey());
        assertThat(store.get(9001L).getOriginalFileName()).isEqualTo("contract.pdf");
        assertThat(store.get(9001L).getStoragePath()).isEqualTo(
                "local:attachments/" + LocalDate.now().getYear() + "/" + String.format("%02d", LocalDate.now().getMonthValue()) + "/9001/renamed-contract.pdf"
        );
    }

    @Test
    void shouldIncludeModuleKeyInAttachmentUrlsWhenProvided() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentFileRepository repository = attachmentRepository(store);
        AttachmentProperties properties = localProperties();

        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(9002L),
                properties,
                filenameResolver,
                new StubUploadRuleService("renamed-proof.pdf"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties)
        );
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        AttachmentView response = service.upload(file, "PAGE_UPLOAD", "freight-statements");

        assertThat(response.previewUrl()).isEqualTo("/api/attachments/9002/preview?accessKey="
                + store.get(9002L).getAccessKey() + "&moduleKey=freight-statements");
        assertThat(response.downloadUrl()).isEqualTo("/api/attachments/9002/download?accessKey="
                + store.get(9002L).getAccessKey() + "&moduleKey=freight-statements");
    }

    @Test
    void shouldRejectMissingAttachmentIds() {
        AttachmentProperties properties = localProperties();

        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties)
        );

        assertThatThrownBy(() -> service.validateAttachmentIds(List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void shouldRejectFileTooLarge() {
        AttachmentProperties properties = localProperties();
        properties.setMaxFileSize(DataSize.ofBytes(2));

        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("big.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties)
        );
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", "toolarge".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(file, "PAGE_UPLOAD"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过大小限制");
    }

    @Test
    void shouldCleanupStoredFileWhenRepositorySaveFails() {
        AttachmentProperties properties = localProperties();
        AttachmentFileRepository repository = (AttachmentFileRepository) Proxy.newProxyInstance(
                AttachmentFileRepository.class.getClassLoader(),
                new Class[]{AttachmentFileRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> throw new IllegalStateException("db failed");
                    case "toString" -> "AttachmentFileRepositoryFailureStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(9100L),
                properties,
                filenameResolver,
                new StubUploadRuleService("rollback.pdf"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties)
        );

        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "rollback.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8)),
                "PAGE_UPLOAD"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db failed");

        Path expectedFile = tempDir.resolve("attachments")
                .resolve(String.valueOf(LocalDate.now().getYear()))
                .resolve(String.format("%02d", LocalDate.now().getMonthValue()))
                .resolve("9100")
                .resolve("rollback.pdf");
        assertThat(Files.exists(expectedFile)).isFalse();
    }

    @Test
    void shouldLoadDownloadForStandaloneAttachment() throws Exception {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        Path targetFile = tempDir.resolve("attachments")
                .resolve(String.valueOf(LocalDate.now().getYear()))
                .resolve(String.format("%02d", LocalDate.now().getMonthValue()))
                .resolve("9200")
                .resolve("preview.pdf");
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, "preview");

        AttachmentFile entity = new AttachmentFile();
        entity.setId(9200L);
        entity.setFileName("preview.pdf");
        entity.setOriginalFileName("preview.pdf");
        entity.setFileExtension("pdf");
        entity.setContentType("text/html");
        entity.setFileSize(7L);
        entity.setStoragePath("local:attachments/" + LocalDate.now().getYear() + "/" + String.format("%02d", LocalDate.now().getMonthValue()) + "/9200/preview.pdf");
        entity.setAccessKey("access-key-9200");
        entity.setSourceType("PAGE_UPLOAD");
        store.put(9200L, entity);

        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(9200L),
                properties,
                filenameResolver,
                new StubUploadRuleService("preview.pdf"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties)
        );

        AttachmentService.AttachmentDownloadPayload payload = service.loadForDownload(9200L, "access-key-9200");
        Resource resource = payload.resource();

        assertThat(payload.fileName()).isEqualTo("preview.pdf");
        assertThat(payload.previewSupported()).isTrue();
        assertThat(payload.previewType()).isEqualTo("pdf");
        assertThat(payload.contentType()).isEqualTo("application/pdf");
        assertThat(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("preview");
    }

    @Test
    void shouldRejectDownloadWhenAccessKeyDoesNotMatch() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();

        AttachmentFile entity = new AttachmentFile();
        entity.setId(9300L);
        entity.setFileName("proof.pdf");
        entity.setOriginalFileName("proof.pdf");
        entity.setFileExtension("pdf");
        entity.setContentType("application/pdf");
        entity.setFileSize(7L);
        entity.setStoragePath("local:attachments/" + LocalDate.now().getYear() + "/" + String.format("%02d", LocalDate.now().getMonthValue()) + "/9300/proof.pdf");
        entity.setAccessKey("expected-access-key");
        entity.setSourceType("PAGE_UPLOAD");
        store.put(9300L, entity);

        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(9300L),
                properties,
                filenameResolver,
                new StubUploadRuleService("proof.pdf"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties)
        );

        assertThatThrownBy(() -> service.loadForDownload(9300L, "wrong-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");
    }

    private AttachmentProperties localProperties() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(5));
        properties.getStorage().setType("local");
        properties.getStorage().setKeyPrefix("attachments");
        properties.getStorage().getLocal().setPath(tempDir.toString());
        return properties;
    }

    private AttachmentFileRepository attachmentRepository(Map<Long, AttachmentFile> store) {
        return (AttachmentFileRepository) Proxy.newProxyInstance(
                AttachmentFileRepository.class.getClassLoader(),
                new Class[]{AttachmentFileRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        AttachmentFile entity = (AttachmentFile) args[0];
                        store.put(entity.getId(), entity);
                        yield entity;
                    }
                    case "findAllByIdInAndDeletedFlagFalse" -> {
                        Collection<Long> ids = (Collection<Long>) args[0];
                        List<AttachmentFile> results = new ArrayList<>();
                        for (Long id : ids) {
                            AttachmentFile entity = store.get(id);
                            if (entity != null && !Boolean.TRUE.equals(entity.getDeletedFlag())) {
                                results.add(entity);
                            }
                        }
                        yield results;
                    }
                    case "findByIdAndDeletedFlagFalse" -> {
                        AttachmentFile entity = store.get((Long) args[0]);
                        yield entity != null && !Boolean.TRUE.equals(entity.getDeletedFlag()) ? Optional.of(entity) : Optional.empty();
                    }
                    case "findById" -> Optional.ofNullable(store.get((Long) args[0]));
                    case "toString" -> "AttachmentFileRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class FixedIdGenerator extends SnowflakeIdGenerator {

        private final long id;

        private FixedIdGenerator(long id) {
            this.id = id;
        }

        @Override
        public synchronized long nextId() {
            return id;
        }
    }

    private static final class StubUploadRuleService extends UploadRuleService {

        private final String fileName;

        private StubUploadRuleService(String fileName) {
            super(emptyUploadRuleRepository(), new FixedIdGenerator(1L), new AttachmentFilenameResolver(), new ModuleCatalog());
            this.fileName = fileName;
        }

        @Override
        public String buildPageUploadFileName(String moduleKey, String originalFilename, String contentType) {
            return fileName;
        }

        private static UploadRuleRepository emptyUploadRuleRepository() {
            return (UploadRuleRepository) Proxy.newProxyInstance(
                    UploadRuleRepository.class.getClassLoader(),
                    new Class[]{UploadRuleRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "toString" -> "UploadRuleRepositoryStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
