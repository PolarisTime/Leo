package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentBindingServiceTest {

    @Test
    void shouldReplaceBindingsAndPreserveAttachmentOrder() {
        AtomicReference<List<AttachmentBinding>> savedBindings = new AtomicReference<>(List.of());
        AtomicReference<List<AttachmentBinding>> deletedBindings = new AtomicReference<>(List.of());
        AtomicReference<Boolean> flushCalled = new AtomicReference<>(false);
        List<AttachmentBinding> existingBindings = List.of(binding(90L, "sales-orders", 9L, 10L, 1));
        Map<Long, AttachmentView> attachments = new LinkedHashMap<>();
        attachments.put(11L, attachment(11L, "A.pdf"));
        attachments.put(12L, attachment(12L, "B.pdf"));

        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(existingBindings, savedBindings, deletedBindings, flushCalled),
                attachmentService(attachments),
                new FixedIdGenerator(101L, 102L),
                new ModuleCatalog()
        );

        List<AttachmentView> result = service.replace("sales-orders", 9L, List.of(12L, 11L));

        assertThat(savedBindings.get())
                .extracting(AttachmentBinding::getId, AttachmentBinding::getModuleKey, AttachmentBinding::getRecordId, AttachmentBinding::getAttachmentId, AttachmentBinding::getSortOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(101L, "sales-orders", 9L, 12L, 1),
                        org.assertj.core.groups.Tuple.tuple(102L, "sales-orders", 9L, 11L, 2)
                );
        assertThat(deletedBindings.get()).containsExactlyElementsOf(existingBindings);
        assertThat(flushCalled.get()).isTrue();
        assertThat(result).extracting(AttachmentView::id).containsExactly(12L, 11L);
    }

    @Test
    void shouldRejectDuplicateAttachmentIds() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                new FixedIdGenerator(101L),
                new ModuleCatalog()
        );

        assertThatThrownBy(() -> service.replace("sales-orders", 9L, List.of(12L, 12L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件列表存在重复项");
    }

    @SuppressWarnings("unchecked")
    private AttachmentBindingRepository bindingRepository(List<AttachmentBinding> existingBindings,
                                                          AtomicReference<List<AttachmentBinding>> savedBindings,
                                                          AtomicReference<List<AttachmentBinding>> deletedBindings,
                                                          AtomicReference<Boolean> flushCalled) {
        return (AttachmentBindingRepository) Proxy.newProxyInstance(
                AttachmentBindingRepository.class.getClassLoader(),
                new Class[]{AttachmentBindingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc" -> existingBindings;
                    case "findByModuleKeyAndRecordIdInAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc" -> existingBindings;
                    case "deleteAllInBatch" -> {
                        deletedBindings.set(new ArrayList<>((Collection<AttachmentBinding>) args[0]));
                        yield null;
                    }
                    case "flush" -> {
                        flushCalled.set(true);
                        yield null;
                    }
                    case "saveAll" -> {
                        savedBindings.set(new ArrayList<>((Collection<AttachmentBinding>) args[0]));
                        yield args[0];
                    }
                    case "toString" -> "AttachmentBindingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private AttachmentBinding binding(Long id, String moduleKey, Long recordId, Long attachmentId, Integer sortOrder) {
        AttachmentBinding binding = new AttachmentBinding();
        binding.setId(id);
        binding.setModuleKey(moduleKey);
        binding.setRecordId(recordId);
        binding.setAttachmentId(attachmentId);
        binding.setSortOrder(sortOrder);
        return binding;
    }

    private AttachmentService attachmentService(Map<Long, AttachmentView> attachments) {
        return new AttachmentService(null, null, null, null, null, null) {
            @Override
            public void validateAttachmentIds(List<Long> ids) {
            }

            @Override
            public List<AttachmentView> getAttachments(List<Long> ids, String moduleKey) {
                return ids.stream().map(attachments::get).toList();
            }

            @Override
            public Map<Long, AttachmentView> getAttachmentMap(List<Long> ids, String moduleKey) {
                Map<Long, AttachmentView> result = new LinkedHashMap<>();
                for (Long id : ids) {
                    AttachmentView attachment = attachments.get(id);
                    if (attachment != null) {
                        result.put(id, attachment);
                    }
                }
                return result;
            }
        };
    }

    private AttachmentView attachment(Long id, String name) {
        return new AttachmentView(
                id,
                name,
                name,
                name,
                "application/pdf",
                1L,
                "PAGE_UPLOAD",
                "tester",
                LocalDateTime.now(),
                true,
                "pdf",
                "/api/attachments/" + id + "/preview",
                "/api/attachments/" + id + "/download"
        );
    }

    private static final class FixedIdGenerator extends SnowflakeIdGenerator {

        private final long[] ids;
        private int index;

        private FixedIdGenerator(long... ids) {
            this.ids = ids;
        }

        @Override
        public synchronized long nextId() {
            return ids[index++];
        }
    }
}
