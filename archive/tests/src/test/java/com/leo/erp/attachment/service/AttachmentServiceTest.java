package com.leo.erp.attachment.service;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.attachment.repository.UploadRuleRepository;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import com.leo.erp.attachment.service.storage.DirectUploadAttachmentStorage;
import com.leo.erp.attachment.service.storage.LocalAttachmentStorage;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.oss.service.OssSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
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
    void shouldNotDeclareNonPublicTransactionalMethods() {
        List<String> nonPublicTransactionalMethods = Arrays.stream(AttachmentService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .filter(method -> !Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .toList();

        assertThat(nonPublicTransactionalMethods).isEmpty();
    }

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
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
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
        assertThat(response.storageType()).isEqualTo("local");
        assertThat(response.storageLabel()).isEqualTo("本机存储");
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
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        AttachmentView response = service.upload(file, "PAGE_UPLOAD", "freight-statement");

        assertThat(response.previewUrl()).isEqualTo("/api/attachments/9002/preview?accessKey="
                + store.get(9002L).getAccessKey() + "&moduleKey=freight-statement");
        assertThat(response.downloadUrl()).isEqualTo("/api/attachments/9002/download?accessKey="
                + store.get(9002L).getAccessKey() + "&moduleKey=freight-statement");
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
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
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
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
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
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
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
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
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
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );

        assertThatThrownBy(() -> service.loadForDownload(9300L, "wrong-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void shouldPrepareDirectUploadWhenConfiguredStorageSupportsIt() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9400/direct.pdf");

        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(9400L),
                properties,
                filenameResolver,
                new StubUploadRuleService("direct.pdf"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );

        AttachmentService.DirectUploadPrepareResult result = service.prepareDirectUpload(
                "direct.pdf", "application/pdf", 128L, "PAGE_UPLOAD", "sales-order",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 11L);

        assertThat(result.attachmentId()).isEqualTo(9400L);
        assertThat(result.objectKey()).contains("/9400/direct.pdf");
        assertThat(result.storagePath()).isEqualTo("s3:test-bucket/attachments/2026/07/9400/direct.pdf");
        assertThat(result.uploadUrl()).isEqualTo(URI.create("https://upload.example.com/direct.pdf"));
        assertThat(result.method()).isEqualTo("PUT");
        assertThat(result.headers()).containsEntry("Content-Type", "application/pdf");
        assertThat(result.token()).isNotBlank();
        assertThat(storage.preparedKey).contains("/9400/direct.pdf");
    }

    @Test
    void shouldCompleteDirectUploadAndPersistMetadata() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9500/complete.pdf");

        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(9500L),
                properties,
                filenameResolver,
                new StubUploadRuleService("complete.pdf"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );

        AttachmentService.DirectUploadPrepareResult prepared = service.prepareDirectUpload(
                "complete.pdf", "application/pdf", 256L, "PAGE_UPLOAD", "sales-order",
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", 12L);
        AttachmentView view = service.completeDirectUpload(
                prepared.attachmentId(), prepared.token(), "sales-order", 12L);

        assertThat(storage.verifiedStoragePath).isEqualTo(prepared.storagePath());
        assertThat(storage.verifiedSha256Hex).isEqualTo("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        assertThat(view.id()).isEqualTo(9500L);
        assertThat(view.fileName()).isEqualTo("complete.pdf");
        assertThat(view.storageType()).isEqualTo("s3");
        assertThat(view.storageLabel()).isEqualTo("S3存储");
        assertThat(store.get(9500L).getStoragePath()).isEqualTo(prepared.storagePath());
        assertThat(store.get(9500L).getFileSize()).isEqualTo(256L);
    }

    @Test
    void shouldRejectDirectUploadCompletionWhenUserDoesNotMatchToken() {
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9550/complete.pdf");

        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(9550L),
                properties,
                filenameResolver,
                new StubUploadRuleService("complete.pdf"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );
        AttachmentService.DirectUploadPrepareResult prepared = service.prepareDirectUpload(
                "complete.pdf", "application/pdf", 256L, "PAGE_UPLOAD", "sales-order",
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", 12L);

        assertThatThrownBy(() -> service.completeDirectUpload(prepared.attachmentId(), prepared.token(), "sales-order", 13L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
        assertThat(storage.verifiedStoragePath).isNull();
    }

    @Test
    void shouldRejectDirectUploadCompletionWhenTokenDoesNotMatch() {
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9600/complete.pdf");

        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(9600L),
                properties,
                filenameResolver,
                new StubUploadRuleService("complete.pdf"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );
        AttachmentService.DirectUploadPrepareResult prepared = service.prepareDirectUpload(
                "complete.pdf", "application/pdf", 256L, "PAGE_UPLOAD", "sales-order",
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", 12L);

        assertThatThrownBy(() -> service.completeDirectUpload(prepared.attachmentId(), "bad-token", "sales-order", 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
    }

    @Test
    void shouldReturnPresignedPreviewUrlForS3AttachmentWhenWatermarkIsNotRequired() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9700/preview.pdf");
        AttachmentFile entity = attachmentEntity(9700L, "preview.pdf", "application/pdf", storage.storagePath);
        store.put(9700L, entity);

        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(9700L),
                properties,
                filenameResolver,
                new StubUploadRuleService("preview.pdf"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );

        AttachmentService.PresignedAttachmentUrl url = service.createPresignedAccessUrl(
                9700L, "access-key-9700", true, false, "sales-order");

        assertThat(url.url()).isEqualTo(URI.create("https://download.example.com/preview.pdf"));
        assertThat(url.inline()).isTrue();
    }

    @Test
    void shouldInstantiateConstructorWithExplicitMetadataAndTokenServices() {
        AttachmentProperties properties = localProperties();
        AttachmentFileRepository repository = attachmentRepository(new LinkedHashMap<>());
        AttachmentMetadataService metadataService = new AttachmentMetadataService(repository, filenameResolver);
        AttachmentDirectUploadTokenService tokenService = new AttachmentDirectUploadTokenService(
                "constructor-test-secret-must-be-long-enough"
        );

        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties),
                null,
                null,
                metadataService,
                tokenService
        );

        assertThat(service.getAttachments(List.of())).isEmpty();
    }

    @Test
    void shouldUseClipboardDefaultNameWhenOriginalFileNameIsBlank() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();

        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(9800L),
                properties,
                filenameResolver,
                new EchoUploadRuleService(),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );
        MockMultipartFile file = new MockMultipartFile("file", "", "image/png", "png".getBytes(StandardCharsets.UTF_8));

        AttachmentView response = service.upload(file, " clipboard_paste ");

        assertThat(response.fileName()).isEqualTo("clipboard.png");
        assertThat(response.originalFileName()).isEqualTo("clipboard.png");
        assertThat(response.sourceType()).isEqualTo("CLIPBOARD_PASTE");
        assertThat(store.get(9800L).getStoragePath()).contains("/9800/clipboard.png");
    }

    @Test
    void shouldUseUploadDefaultNameWhenOriginalFileNameIsBlank() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();

        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(9801L),
                properties,
                filenameResolver,
                new EchoUploadRuleService(),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );
        MockMultipartFile file = new MockMultipartFile("file", "", "text/plain", "text".getBytes(StandardCharsets.UTF_8));

        AttachmentView response = service.upload(file, null);

        assertThat(response.fileName()).isEqualTo("upload.txt");
        assertThat(response.sourceType()).isEqualTo("PAGE_UPLOAD");
    }

    @Test
    void shouldUseBaseNameWhenResolverReturnsBlankExtensionForMissingOriginalName() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        AttachmentFilenameResolver resolver = new AttachmentFilenameResolver() {
            @Override
            public FilenameParts parseFilenameParts(String originalFilename, String contentType) {
                return new FilenameParts("ignored", "");
            }
        };

        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(9802L),
                properties,
                resolver,
                new EchoUploadRuleService(),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );
        MockMultipartFile file = new MockMultipartFile("file", "", null, "text".getBytes(StandardCharsets.UTF_8));

        AttachmentView response = service.upload(file, "PAGE_UPLOAD");

        assertThat(response.fileName()).isEqualTo("upload");
    }

    @Test
    void shouldRejectUnsupportedSourceType() {
        AttachmentProperties properties = localProperties();
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );

        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
                "mobile"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的上传来源");
    }

    @Test
    void shouldRejectNullOrEmptyUploadFile() {
        AttachmentProperties properties = localProperties();
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );

        assertThatThrownBy(() -> service.upload(null, "PAGE_UPLOAD"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件不能为空");
        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]),
                "PAGE_UPLOAD"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件不能为空");
    }

    @Test
    void shouldRejectInvalidDirectUploadSha256AndOwner() {
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9810/direct.pdf");
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(9810L),
                properties,
                filenameResolver,
                new StubUploadRuleService("direct.pdf"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );

        assertThatThrownBy(() -> service.prepareDirectUpload(
                "direct.pdf", "application/pdf", 128L, "PAGE_UPLOAD", "sales-order", "bad", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件校验值无效");
        assertThatThrownBy(() -> service.prepareDirectUpload(
                "direct.pdf", "application/pdf", 128L, "PAGE_UPLOAD", "sales-order", null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件校验值无效");
        assertThatThrownBy(() -> service.prepareDirectUpload(
                "direct.pdf",
                "application/pdf",
                128L,
                "PAGE_UPLOAD",
                "sales-order",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                null
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传用户无效");
    }

    @Test
    void shouldPrepareDirectUploadWithDefaultNameAndNullModuleKey() {
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9812/upload.pdf");
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(9812L),
                properties,
                filenameResolver,
                new EchoUploadRuleService(),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );

        AttachmentService.DirectUploadPrepareResult result = service.prepareDirectUpload(
                null,
                "application/pdf",
                128L,
                "PAGE_UPLOAD",
                null,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                1L
        );

        assertThat(result.objectKey()).contains("/9812/upload.pdf");
        assertThat(storage.preparedKey).contains("/9812/upload.pdf");
    }

    @Test
    void shouldRejectDirectUploadMetadataWithNonPositiveSize() {
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(9811L),
                properties,
                filenameResolver,
                new StubUploadRuleService("direct.pdf"),
                new AttachmentStorageResolver(List.of(new RecordingDirectStorage("s3:test-bucket/direct.pdf")), properties),
                null,
                null
        );

        assertThatThrownBy(() -> service.prepareDirectUpload(
                "direct.pdf",
                "application/pdf",
                0L,
                "PAGE_UPLOAD",
                "sales-order",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                1L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件不能为空");
    }

    @Test
    void shouldCleanupDirectUploadObjectWhenMetadataSaveFails() {
        AttachmentFileRepository repository = (AttachmentFileRepository) Proxy.newProxyInstance(
                AttachmentFileRepository.class.getClassLoader(),
                new Class[]{AttachmentFileRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> throw new IllegalStateException("metadata failed");
                    case "toString" -> "AttachmentFileRepositoryFailureStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9820/direct.pdf");
        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(9820L),
                properties,
                filenameResolver,
                new StubUploadRuleService("direct.pdf"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );
        AttachmentService.DirectUploadPrepareResult prepared = service.prepareDirectUpload(
                "direct.pdf",
                "application/pdf",
                128L,
                "PAGE_UPLOAD",
                "sales-order",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                1L
        );

        assertThatThrownBy(() -> service.completeDirectUpload(prepared.attachmentId(), prepared.token(), "sales-order", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata failed");
        assertThat(storage.deletedStoragePath).isEqualTo(prepared.storagePath());
    }

    @Test
    void shouldSkipCleanupWhenStoredPathIsBlank() {
        AttachmentFileRepository repository = (AttachmentFileRepository) Proxy.newProxyInstance(
                AttachmentFileRepository.class.getClassLoader(),
                new Class[]{AttachmentFileRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> throw new IllegalStateException("metadata failed");
                    case "toString" -> "AttachmentFileRepositoryFailureStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        AttachmentProperties properties = localProperties();
        BlankPathStorage storage = new BlankPathStorage();
        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(9822L),
                properties,
                filenameResolver,
                new StubUploadRuleService("blank.txt"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );

        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "blank.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
                "PAGE_UPLOAD"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata failed");
        assertThat(storage.deleteCalled).isFalse();
    }

    @Test
    void shouldIgnoreCleanupFailureAndPreserveMetadataErrorOnDirectUploadCompletion() {
        AttachmentFileRepository repository = (AttachmentFileRepository) Proxy.newProxyInstance(
                AttachmentFileRepository.class.getClassLoader(),
                new Class[]{AttachmentFileRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> throw new IllegalStateException("metadata failed");
                    case "toString" -> "AttachmentFileRepositoryFailureStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9821/direct.pdf");
        storage.failDelete = true;
        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(9821L),
                properties,
                filenameResolver,
                new StubUploadRuleService("direct.pdf"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );
        AttachmentService.DirectUploadPrepareResult prepared = service.prepareDirectUpload(
                "direct.pdf",
                "application/pdf",
                128L,
                "PAGE_UPLOAD",
                "sales-order",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                1L
        );

        assertThatThrownBy(() -> service.completeDirectUpload(prepared.attachmentId(), prepared.token(), "sales-order", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata failed");
        assertThat(storage.deletedStoragePath).isEqualTo(prepared.storagePath());
    }

    @Test
    void shouldReturnEmptyMapWhenAttachmentIdsAreNull() {
        AttachmentProperties properties = localProperties();
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );

        assertThat(service.getAttachmentMap(null)).isEmpty();
    }

    @Test
    void shouldKeepFirstAttachmentWhenRepositoryReturnsDuplicateIds() {
        AttachmentFileRepository repository = (AttachmentFileRepository) Proxy.newProxyInstance(
                AttachmentFileRepository.class.getClassLoader(),
                new Class[]{AttachmentFileRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAllByIdInAndDeletedFlagFalse" -> {
                        AttachmentFile first = attachmentEntity(9825L, "first.pdf", "application/pdf", "local:first.pdf");
                        AttachmentFile second = attachmentEntity(9825L, "second.pdf", "application/pdf", "local:second.pdf");
                        yield List.of(first, second);
                    }
                    case "toString" -> "AttachmentFileRepositoryDuplicateStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        AttachmentProperties properties = localProperties();
        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );

        Map<Long, AttachmentView> result = service.getAttachmentMap(List.of(9825L));

        assertThat(result.get(9825L).fileName()).isEqualTo("first.pdf");
    }

    @Test
    void shouldValidateExistingAttachmentIds() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        store.put(9826L, attachmentEntity(9826L, "exists.pdf", "application/pdf", "local:exists.pdf"));
        AttachmentProperties properties = localProperties();
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );

        service.validateAttachmentIds(List.of(9826L));
    }

    @Test
    void shouldResolveLocalStorageLabelWhenStoragePathIsBlankOrUnknown() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        AttachmentFile nullStorage = attachmentEntity(9829L, "null.txt", "text/plain", null);
        AttachmentFile blankStorage = attachmentEntity(9830L, "blank.txt", "text/plain", "");
        AttachmentFile unknownStorage = attachmentEntity(9831L, "unknown.txt", "text/plain", "oss:bucket/file.txt");
        AttachmentFile noColonStorage = attachmentEntity(9832L, "nocolon.txt", "text/plain", "relative/file.txt");
        store.put(9829L, nullStorage);
        store.put(9830L, blankStorage);
        store.put(9831L, unknownStorage);
        store.put(9832L, noColonStorage);
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );

        List<AttachmentView> views = service.getAttachments(List.of(9829L, 9830L, 9831L, 9832L));

        assertThat(views).extracting(AttachmentView::storageType).containsExactly("local", "local", "local", "local");
        assertThat(views).extracting(AttachmentView::storageLabel).containsExactly("本机存储", "本机存储", "本机存储", "本机存储");
    }

    @Test
    void shouldBuildObjectKeyWithoutPrefixAndStripLeadingSlashPrefix() throws IOException {
        Map<Long, AttachmentFile> blankPrefixStore = new LinkedHashMap<>();
        AttachmentProperties blankPrefixProperties = localProperties();
        blankPrefixProperties.getStorage().setKeyPrefix(" ");
        AttachmentService blankPrefixService = new AttachmentService(
                attachmentRepository(blankPrefixStore),
                new FixedIdGenerator(9833L),
                blankPrefixProperties,
                filenameResolver,
                new StubUploadRuleService("plain.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(blankPrefixProperties)), blankPrefixProperties),
                null,
                null
        );

        AttachmentView blankPrefixView = blankPrefixService.upload(
                new MockMultipartFile("file", "plain.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
                "PAGE_UPLOAD"
        );
        assertThat(blankPrefixStore.get(blankPrefixView.id()).getStoragePath()).startsWith("local:2026/");

        Map<Long, AttachmentFile> slashPrefixStore = new LinkedHashMap<>();
        AttachmentProperties slashPrefixProperties = localProperties();
        slashPrefixProperties.getStorage().setKeyPrefix("/tenant/uploads");
        AttachmentService slashPrefixService = new AttachmentService(
                attachmentRepository(slashPrefixStore),
                new FixedIdGenerator(9834L),
                slashPrefixProperties,
                filenameResolver,
                new StubUploadRuleService("dir/slash.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(slashPrefixProperties)), slashPrefixProperties),
                null,
                null
        );

        AttachmentView slashPrefixView = slashPrefixService.upload(
                new MockMultipartFile("file", "slash.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
                "PAGE_UPLOAD"
        );
        assertThat(slashPrefixView.fileName()).isEqualTo("slash.txt");
        assertThat(slashPrefixStore.get(slashPrefixView.id()).getStoragePath()).startsWith("local:tenant/uploads/");
    }

    @Test
    void shouldKeepTrailingSlashCandidateFileNameWhenPreparingDirectUpload() {
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9837/dir/");
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(9837L),
                properties,
                filenameResolver,
                new StubUploadRuleService("dir/"),
                new AttachmentStorageResolver(List.of(storage), properties),
                null,
                null
        );

        AttachmentService.DirectUploadPrepareResult result = service.prepareDirectUpload(
                "source.txt",
                "text/plain",
                128L,
                "PAGE_UPLOAD",
                "sales-order",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                1L
        );

        assertThat(result.objectKey()).endsWith("/9837/dir/");
        assertThat(storage.preparedKey).endsWith("/9837/dir/");
    }

    @Test
    void shouldRejectBlockedExtensionWhenFilenameHasExecutableSuffix() {
        AttachmentProperties properties = localProperties();
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties),
                null,
                null
        );

        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "shell.JSP", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
                "PAGE_UPLOAD"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型: .jsp");
    }

    @Test
    void shouldAllowFilenameWithoutDotWhenValidatingUploadMetadata() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(9838L),
                properties,
                filenameResolver,
                new StubUploadRuleService("README"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties),
                null,
                null
        );

        AttachmentView view = service.upload(
                new MockMultipartFile("file", "README", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
                "PAGE_UPLOAD"
        );

        assertThat(view.fileName()).isEqualTo("README");
    }

    @Test
    void shouldBuildObjectKeyFromRuntimeOssPrefix() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        AttachmentFileRepository repository = attachmentRepository(store);
        OssSettingService ossSettingService = new FixedRuntimeOssSettingService(" /runtime/uploads/ ");
        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(9835L),
                properties,
                filenameResolver,
                new StubUploadRuleService("runtime.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties),
                null,
                null,
                new AttachmentMetadataService(repository, filenameResolver),
                new AttachmentDirectUploadTokenService("runtime-prefix-test-secret-must-be-long-enough"),
                ossSettingService
        );

        AttachmentView view = service.upload(
                new MockMultipartFile("file", "runtime.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
                "PAGE_UPLOAD"
        );

        assertThat(store.get(view.id()).getStoragePath()).startsWith("local:runtime/uploads/");
    }

    @Test
    void shouldBuildObjectKeyWithoutPrefixWhenRuntimeOssPrefixOnlyContainsSlash() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        AttachmentFileRepository repository = attachmentRepository(store);
        OssSettingService ossSettingService = new FixedRuntimeOssSettingService("/");
        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(9836L),
                properties,
                filenameResolver,
                new StubUploadRuleService("root.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties),
                null,
                null,
                new AttachmentMetadataService(repository, filenameResolver),
                new AttachmentDirectUploadTokenService("runtime-root-test-secret-must-be-long-enough"),
                ossSettingService
        );

        AttachmentView view = service.upload(
                new MockMultipartFile("file", "root.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
                "PAGE_UPLOAD"
        );

        assertThat(store.get(view.id()).getStoragePath()).startsWith("local:2026/");
    }

    @Test
    void shouldReturnNullWhenPresignedUrlRequiresWatermark() {
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                localProperties(),
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new RecordingDirectStorage("s3:test-bucket/file.txt")), localProperties()),
                null,
                null
        );

        assertThat(service.createPresignedAccessUrl(1L, "ignored", true, true, "sales-order")).isNull();
    }

    @Test
    void shouldRejectPresignedInlineUrlWhenPreviewIsNotSupported() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9840/data.txt");
        store.put(9840L, attachmentEntity(9840L, "data.txt", "text/plain", storage.storagePath));
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );

        assertThatThrownBy(() -> service.createPresignedAccessUrl(9840L, "access-key-9840", true, false, "sales-order"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前附件不支持预览");
    }

    @Test
    void shouldReturnNullWhenStorageCannotCreatePresignedUrl() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        store.put(9841L, attachmentEntity(9841L, "data.txt", "text/plain", "local:data.txt"));
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new MemoryAttachmentStorage("local:data.txt", "data".getBytes(StandardCharsets.UTF_8))), properties),
                null,
                null
        );

        assertThat(service.createPresignedAccessUrl(9841L, "access-key-9841", false, false, "sales-order")).isNull();
    }

    @Test
    void shouldUseDownloadContentTypeWhenCreatingNonInlinePresignedUrl() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        properties.getStorage().setType("s3");
        RecordingDirectStorage storage = new RecordingDirectStorage("s3:test-bucket/attachments/2026/07/9842/data.txt");
        store.put(9842L, attachmentEntity(9842L, "data.txt", "text/plain", storage.storagePath));
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties), null, null
        );

        AttachmentService.PresignedAttachmentUrl url = service.createPresignedAccessUrl(
                9842L, "access-key-9842", false, false, "sales-order");

        assertThat(url.inline()).isFalse();
        assertThat(storage.lastPresignedContentType).isEqualTo("text/plain");
    }

    @Test
    void shouldApplyWatermarkForImageDownloadResource() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        MemoryAttachmentStorage storage = new MemoryAttachmentStorage("local:photo.png", "image".getBytes(StandardCharsets.UTF_8));
        store.put(9850L, attachmentEntity(9850L, "photo.png", "image/png", storage.storagePath));
        ImageWatermarkService imageWatermarkService = new ImageWatermarkService() {
            @Override
            public byte[] apply(java.io.InputStream imageStream, String username) {
                return ("image-" + username).getBytes(StandardCharsets.UTF_8);
            }
        };
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties),
                imageWatermarkService,
                null
        );

        AttachmentDownloadResource resource = service.loadDownloadResource(9850L, "access-key-9850", true, true, "alice");

        assertThat(new String(resource.resource().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("image-alice");
        assertThat(resource.contentType().toString()).isEqualTo("image/png");
        assertThat(resource.contentDisposition()).contains("inline");
    }

    @Test
    void shouldApplyWatermarkForPdfDownloadResource() throws IOException {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        MemoryAttachmentStorage storage = new MemoryAttachmentStorage("local:file.pdf", "pdf".getBytes(StandardCharsets.UTF_8));
        store.put(9860L, attachmentEntity(9860L, "file.pdf", "application/octet-stream", storage.storagePath));
        PdfWatermarkService pdfWatermarkService = new PdfWatermarkService() {
            @Override
            public byte[] apply(java.io.InputStream pdfStream, String username) {
                return ("pdf-" + username).getBytes(StandardCharsets.UTF_8);
            }
        };
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties),
                null,
                pdfWatermarkService
        );

        AttachmentDownloadResource resource = service.loadDownloadResource(9860L, "access-key-9860", true, true, "bob");

        assertThat(new String(resource.resource().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("pdf-bob");
        assertThat(resource.contentType().toString()).isEqualTo("application/pdf");
    }

    @Test
    void shouldKeepOriginalResourceWhenWatermarkTypeIsUnsupported() throws Exception {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        MemoryAttachmentStorage storage = new MemoryAttachmentStorage("local:data.txt", "plain".getBytes(StandardCharsets.UTF_8));
        store.put(9870L, attachmentEntity(9870L, "data.txt", "text/plain", storage.storagePath));
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties),
                null,
                null
        );

        AttachmentDownloadResource resource = service.loadDownloadResource(9870L, "access-key-9870", false, true, "alice");

        assertThat(new String(resource.resource().getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("plain");
    }

    @Test
    void shouldSkipWatermarkWhenUsernameIsNull() throws Exception {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        MemoryAttachmentStorage storage = new MemoryAttachmentStorage("local:photo.png", "image".getBytes(StandardCharsets.UTF_8));
        store.put(9871L, attachmentEntity(9871L, "photo.png", "image/png", storage.storagePath));
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties),
                null,
                null
        );

        AttachmentDownloadResource resource = service.loadDownloadResource(9871L, "access-key-9871", true, true, null);

        assertThat(new String(resource.resource().getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("image");
        assertThat(resource.contentDisposition()).contains("inline");
    }

    @Test
    void shouldUseOctetStreamForBlankDownloadPayloadContentType() {
        AttachmentProperties properties = localProperties();
        MemoryAttachmentStorage storage = new MemoryAttachmentStorage("local:data.bin", "data".getBytes(StandardCharsets.UTF_8));
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties),
                null,
                null
        ) {
            @Override
            public AttachmentDownloadPayload loadForDownload(Long id, String accessKey) {
                return new AttachmentDownloadPayload(
                        "data.bin",
                        " ",
                        new ByteArrayResource("data".getBytes(StandardCharsets.UTF_8)),
                        false,
                        "none"
                );
            }
        };

        AttachmentDownloadResource resource = service.loadDownloadResource(9872L, "access-key-9872", false);

        assertThat(resource.contentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        assertThat(resource.contentDisposition()).contains("attachment");
    }

    @Test
    void shouldUseOctetStreamForNullDownloadPayloadContentType() {
        AttachmentProperties properties = localProperties();
        MemoryAttachmentStorage storage = new MemoryAttachmentStorage("local:data.bin", "data".getBytes(StandardCharsets.UTF_8));
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties),
                null,
                null
        ) {
            @Override
            public AttachmentDownloadPayload loadForDownload(Long id, String accessKey) {
                return new AttachmentDownloadPayload(
                        "data.bin",
                        null,
                        new ByteArrayResource("data".getBytes(StandardCharsets.UTF_8)),
                        false,
                        "none"
                );
            }
        };

        AttachmentDownloadResource resource = service.loadDownloadResource(9873L, "access-key-9873", false);

        assertThat(resource.contentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void shouldHandleAttachmentPrivateNullAndBlankBoundaries() throws Exception {
        AttachmentProperties properties = localProperties();
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties),
                null,
                null
        );

        Method normalizeSourceType = AttachmentService.class.getDeclaredMethod("normalizeSourceType", String.class);
        normalizeSourceType.setAccessible(true);
        assertThat(normalizeSourceType.invoke(service, new Object[]{null})).isEqualTo("PAGE_UPLOAD");
        assertThat(normalizeSourceType.invoke(service, " ")).isEqualTo("PAGE_UPLOAD");

        Method cleanupStoredFileQuietly = AttachmentService.class.getDeclaredMethod("cleanupStoredFileQuietly", String.class);
        cleanupStoredFileQuietly.setAccessible(true);
        cleanupStoredFileQuietly.invoke(service, new Object[]{null});
        cleanupStoredFileQuietly.invoke(service, " ");

        Method toModuleQuery = AttachmentService.class.getDeclaredMethod("toModuleQuery", String.class);
        toModuleQuery.setAccessible(true);
        assertThat(toModuleQuery.invoke(service, new Object[]{null})).isEqualTo("");
        assertThat(toModuleQuery.invoke(service, " ")).isEqualTo("");

        Method resolveStorageType = AttachmentService.class.getDeclaredMethod("resolveStorageType", String.class);
        resolveStorageType.setAccessible(true);
        assertThat(resolveStorageType.invoke(service, new Object[]{null})).isEqualTo("local");
        assertThat(resolveStorageType.invoke(service, " ")).isEqualTo("local");
    }

    @Test
    void shouldUseEmptyKeyPrefixWhenRuntimeKeyPrefixIsNull() throws Exception {
        AttachmentProperties properties = localProperties();
        AttachmentFileRepository repository = attachmentRepository(new LinkedHashMap<>());
        AttachmentService service = new AttachmentService(
                repository,
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties),
                null,
                null,
                new AttachmentMetadataService(repository, filenameResolver),
                new AttachmentDirectUploadTokenService("leo-direct-upload-test-secret-must-be-long-enough"),
                new FixedRuntimeOssSettingService(null)
        );
        Method normalizedKeyPrefix = AttachmentService.class.getDeclaredMethod("normalizedKeyPrefix");
        normalizedKeyPrefix.setAccessible(true);

        assertThat(normalizedKeyPrefix.invoke(service)).isEqualTo("");
    }

    @Test
    void shouldResolveImagePreviewContentTypes() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        List<String> names = List.of("photo.jpg", "anim.gif", "image.webp", "scan.bmp", "vector.svg");
        for (int i = 0; i < names.size(); i++) {
            String fileName = names.get(i);
            long id = 9890L + i;
            store.put(id, attachmentEntity(id, fileName, "application/octet-stream", "local:" + fileName));
        }
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(
                        new MemoryAttachmentStorage("local:photo.jpg", "data".getBytes(StandardCharsets.UTF_8))
                ), properties),
                null,
                null
        );

        assertThat(service.loadForDownload(9890L, "access-key-9890").contentType()).isEqualTo("image/jpeg");
        assertThat(service.loadForDownload(9891L, "access-key-9891").contentType()).isEqualTo("image/gif");
        assertThat(service.loadForDownload(9892L, "access-key-9892").contentType()).isEqualTo("image/webp");
        assertThat(service.loadForDownload(9893L, "access-key-9893").contentType()).isEqualTo("image/bmp");
        assertThat(service.loadForDownload(9894L, "access-key-9894").contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldUseOctetStreamWhenImagePreviewExtensionChangesBeforeContentTypeResolution() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        MemoryAttachmentStorage storage = new MemoryAttachmentStorage("local:photo.png", "data".getBytes(StandardCharsets.UTF_8));
        AttachmentFile entity = switchingExtensionAttachmentEntity(9895L, "photo.png", "image/png", storage.storagePath);
        store.put(9895L, entity);
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties),
                null,
                null
        );

        assertThat(service.loadForDownload(9895L, "access-key-9895").contentType())
                .isEqualTo("application/octet-stream");
    }

    @Test
    void shouldUseOctetStreamForUnknownImageExtensionWhenResolvingPreviewContentType() throws Exception {
        AttachmentProperties properties = localProperties();
        AttachmentService service = new AttachmentService(
                attachmentRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties),
                null,
                null
        );
        AttachmentFile entity = attachmentEntity(9896L, "vector.svg", "image/svg+xml", "local:vector.svg");
        Method method = AttachmentService.class.getDeclaredMethod(
                "resolveResponseContentType", AttachmentFile.class, String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, entity, "image")).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldRejectDownloadWhenAccessKeyIsBlankOrEntityAccessKeyMissing() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        AttachmentFile entity = attachmentEntity(9899L, "secure.pdf", "application/pdf", "local:secure.pdf");
        store.put(9899L, entity);
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(new LocalAttachmentStorage(properties)), properties), null, null
        );

        assertThatThrownBy(() -> service.loadForDownload(9899L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");

        assertThatThrownBy(() -> service.loadForDownload(9899L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");

        entity.setAccessKey(null);
        assertThatThrownBy(() -> service.loadForDownload(9899L, "access-key-9899"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");

        entity.setAccessKey(" ");
        assertThatThrownBy(() -> service.loadForDownload(9899L, "access-key-9899"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void shouldWrapWatermarkIoFailure() {
        Map<Long, AttachmentFile> store = new LinkedHashMap<>();
        AttachmentProperties properties = localProperties();
        MemoryAttachmentStorage storage = new MemoryAttachmentStorage("local:photo.png", "image".getBytes(StandardCharsets.UTF_8));
        store.put(9880L, attachmentEntity(9880L, "photo.png", "image/png", storage.storagePath));
        ImageWatermarkService imageWatermarkService = new ImageWatermarkService() {
            @Override
            public byte[] apply(java.io.InputStream imageStream, String username) throws IOException {
                throw new IOException("broken image");
            }
        };
        AttachmentService service = new AttachmentService(
                attachmentRepository(store),
                new FixedIdGenerator(1L),
                properties,
                filenameResolver,
                new StubUploadRuleService("ignored.txt"),
                new AttachmentStorageResolver(List.of(storage), properties),
                imageWatermarkService,
                null
        );

        assertThatThrownBy(() -> service.loadDownloadResource(9880L, "access-key-9880", true, true, "alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件水印处理失败");
    }

    private AttachmentProperties localProperties() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(5));
        properties.getStorage().setType("local");
        properties.getStorage().setKeyPrefix("attachments");
        properties.getStorage().getLocal().setPath(tempDir.toString());
        return properties;
    }

    private AttachmentFile attachmentEntity(Long id, String fileName, String contentType, String storagePath) {
        AttachmentFile entity = new AttachmentFile();
        entity.setId(id);
        entity.setFileName(fileName);
        entity.setOriginalFileName(fileName);
        entity.setFileExtension(filenameResolver.parseFilenameParts(fileName, contentType).extension());
        entity.setContentType(contentType);
        entity.setFileSize(7L);
        entity.setStoragePath(storagePath);
        entity.setAccessKey("access-key-" + id);
        entity.setSourceType("PAGE_UPLOAD");
        return entity;
    }

    private AttachmentFile switchingExtensionAttachmentEntity(Long id, String fileName, String contentType, String storagePath) {
        AttachmentFile entity = new AttachmentFile() {
            private int extensionCalls;

            @Override
            public String getFileExtension() {
                extensionCalls++;
                return extensionCalls == 1 ? "png" : "svg";
            }
        };
        entity.setId(id);
        entity.setFileName(fileName);
        entity.setOriginalFileName(fileName);
        entity.setFileExtension("png");
        entity.setContentType(contentType);
        entity.setFileSize(7L);
        entity.setStoragePath(storagePath);
        entity.setAccessKey("access-key-" + id);
        entity.setSourceType("PAGE_UPLOAD");
        return entity;
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
                            if (entity != null && !entity.isDeletedFlag()) {
                                results.add(entity);
                            }
                        }
                        yield results;
                    }
                    case "findByIdAndDeletedFlagFalse" -> {
                        AttachmentFile entity = store.get((Long) args[0]);
                        yield entity != null && !entity.isDeletedFlag() ? Optional.of(entity) : Optional.empty();
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
            super(emptyUploadRuleRepository(), new FixedIdGenerator(1L), new AttachmentFilenameResolver(), new ModuleCatalog(), null);
            this.fileName = fileName;
        }

        @Override
        public String buildPageUploadFileName(String moduleKey, String originalFilename, String contentType) {
            return fileName;
        }

        @Override
        public boolean isPageUploadEnabled(String moduleKey) {
            return true;
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

    private static final class EchoUploadRuleService extends UploadRuleService {

        private EchoUploadRuleService() {
            super(StubUploadRuleService.emptyUploadRuleRepository(), new FixedIdGenerator(1L),
                    new AttachmentFilenameResolver(), new ModuleCatalog(), null);
        }

        @Override
        public String buildPageUploadFileName(String moduleKey, String originalFilename, String contentType) {
            return originalFilename;
        }

        @Override
        public boolean isPageUploadEnabled(String moduleKey) {
            return true;
        }
    }

    private static final class FixedRuntimeOssSettingService extends OssSettingService {

        private final String keyPrefix;

        private FixedRuntimeOssSettingService(String keyPrefix) {
            super(null, null, new AttachmentProperties(), null, null);
            this.keyPrefix = keyPrefix;
        }

        @Override
        public ResolvedOssSetting resolveRuntimeSetting() {
            return new ResolvedOssSetting(
                    "local",
                    keyPrefix,
                    "/tmp/uploads",
                    new AttachmentProperties.S3(),
                    false,
                    false
            );
        }
    }

    private static final class RecordingDirectStorage implements DirectUploadAttachmentStorage {

        private final String storagePath;
        private String preparedKey;
        private String verifiedStoragePath;
        private String verifiedSha256Hex;
        private String deletedStoragePath;
        private String lastPresignedContentType;
        private boolean failDelete;

        private RecordingDirectStorage(String storagePath) {
            this.storagePath = storagePath;
        }

        @Override
        public String type() {
            return "s3";
        }

        @Override
        public String store(String objectKey, MultipartFile file) {
            throw new UnsupportedOperationException("direct upload test storage");
        }

        @Override
        public Resource load(String storagePath) {
            throw new UnsupportedOperationException("direct upload test storage");
        }

        @Override
        public void delete(String storagePath) {
            this.deletedStoragePath = storagePath;
            if (failDelete) {
                throw new IllegalStateException("delete failed");
            }
        }

        @Override
        public PresignedUpload prepareDirectUpload(String objectKey, String contentType, long fileSize, String sha256Hex) {
            this.preparedKey = objectKey;
            return new PresignedUpload(
                    URI.create("https://upload.example.com/direct.pdf"),
                    "PUT",
                    Map.of("Content-Type", contentType, "x-amz-checksum-sha256", "ASNFZ4mrze8BI0VniavN7wEjRWeJq83vASNFZ4mrze8="),
                    storagePath,
                    java.time.Instant.now().plusSeconds(600)
            );
        }

        @Override
        public void verifyDirectUpload(String storagePath, long expectedFileSize, String expectedSha256Hex) {
            this.verifiedStoragePath = storagePath;
            this.verifiedSha256Hex = expectedSha256Hex;
        }

        @Override
        public URI createPresignedAccessUrl(String storagePath, String fileName, String contentType, boolean inline) {
            this.lastPresignedContentType = contentType;
            return URI.create("https://download.example.com/" + fileName);
        }
    }

    private static final class BlankPathStorage implements com.leo.erp.attachment.service.storage.AttachmentStorage {

        private boolean deleteCalled;

        @Override
        public String type() {
            return "local";
        }

        @Override
        public String store(String objectKey, MultipartFile file) {
            return "";
        }

        @Override
        public Resource load(String storagePath) {
            return new ByteArrayResource(new byte[0]);
        }

        @Override
        public void delete(String storagePath) {
            deleteCalled = true;
        }
    }

    private static final class MemoryAttachmentStorage implements com.leo.erp.attachment.service.storage.AttachmentStorage {

        private final String storagePath;
        private final byte[] content;

        private MemoryAttachmentStorage(String storagePath, byte[] content) {
            this.storagePath = storagePath;
            this.content = content;
        }

        @Override
        public String type() {
            return "local";
        }

        @Override
        public String store(String objectKey, MultipartFile file) {
            return storagePath;
        }

        @Override
        public Resource load(String storagePath) {
            return new ByteArrayResource(content);
        }

        @Override
        public void delete(String storagePath) {
        }
    }
}
