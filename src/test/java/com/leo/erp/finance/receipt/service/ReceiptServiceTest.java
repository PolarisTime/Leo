package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.CrudRuntimeSettings;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.finance.receipt.mapper.ReceiptMapper;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationResponse;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.service.CustomerStatementQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptServiceTest {

    @Test
    void shouldRejectDuplicateReceiptNo() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-DUP")).thenReturn(true);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(new ReceiptRequest(
                "SK-DUP", null, "客户A", null, "项目A", 21L,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款单号已存在");
    }

    @Test
    void shouldRejectCustomerNameMismatchBetweenReceiptAndStatement() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户B");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                resourceRecordAccessGuard,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单客户与收款单客户不一致");
    }

    @Test
    void shouldRejectProjectNameMismatchBetweenReceiptAndStatement() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目B");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                resourceRecordAccessGuard,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单项目与收款单项目不一致");
    }

    @Test
    void shouldRejectDuplicateAllocationToSameStatement() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                resourceRecordAccessGuard,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(
                        new ReceiptAllocationRequest(null, 21L, new BigDecimal("50.00")),
                        new ReceiptAllocationRequest(null, 21L, new BigDecimal("50.00"))
                )
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复核销同一客户对账单");
    }

    @Test
    void shouldRejectZeroAllocationAmount() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, BigDecimal.ZERO))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额必须大于0");
    }

    @Test
    void shouldRejectReceiptAmountNotMatchingAllocations() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "已收款",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("80.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款金额必须等于核销金额合计");
    }

    @Test
    void shouldRejectReceivedReceiptWithUnconfirmedCustomerStatement() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.PENDING_CONFIRM);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "已收款",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单未确认，不能收款");
    }

    @Test
    void shouldRejectReceivedReceiptWithoutAllocations() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                null,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "已收款",
                List.of()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已收款状态必须填写核销明细");
    }

    @Test
    void shouldAllowDraftReceiptWithPartialAllocation() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerCode("C-001");
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> {
            Receipt receipt = inv.getArgument(0);
            receipt.setId(1L);
            return receipt;
        });
        when(receiptMapper.toResponse(any(Receipt.class))).thenAnswer(inv -> {
            Receipt receipt = inv.getArgument(0);
            return new ReceiptResponse(
                    receipt.getId(), receipt.getReceiptNo(), receipt.getCustomerCode(), receipt.getCustomerName(),
                    receipt.getProjectId(), receipt.getProjectName(), receipt.getSourceStatementId(),
                    receipt.getReceiptDate(), receipt.getPayType(), receipt.getAmount(), receipt.getStatus(),
                    receipt.getOperatorName(), receipt.getRemark(), List.of()
            );
        });

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                receiptMapper,
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        ReceiptResponse result = service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("80.00")))
        ));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(StatusConstants.DRAFT);
        assertThat(result.customerCode()).isEqualTo("C-001");
    }

    @Test
    void shouldRejectCustomerCodeMismatchBetweenReceiptAndStatement() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerCode("C-001");
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "C-OTHER",
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单客户编码与收款单客户编码不一致");
    }

    @Test
    void shouldRejectOverReceiptAgainstCustomerStatement() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptAllocationRepository allocationRepository = mock(ReceiptAllocationRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);
        when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                eq(21L),
                eq(StatusConstants.RECEIVED),
                anyLong()
        )).thenReturn(new BigDecimal("950.00"));

        ReceiptService service = service(
                receiptRepository,
                allocationRepository,
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "已收款",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计收款金额不能超过销售金额");
    }

    @Test
    void shouldRejectOverReceiptAgainstCustomerStatementWhenStatusContainsWhitespace() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptAllocationRepository allocationRepository = mock(ReceiptAllocationRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);
        when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                eq(21L),
                eq(StatusConstants.RECEIVED),
                anyLong()
        )).thenReturn(new BigDecimal("950.00"));

        ReceiptService service = service(
                receiptRepository,
                allocationRepository,
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                " 已收款 ",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计收款金额不能超过销售金额");
    }

    @Test
    void shouldRejectAllocationAmountExceedingReceiptAmount() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("120.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额合计不能超过收款金额");
    }

    @Test
    void shouldRejectCustomerStatementOutsideDataScope() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无数据权限"))
                .when(resourceRecordAccessGuard)
                .assertCurrentUserCanAccess("customer-statement", "read", statement);

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                customerStatementQueryService,
                mock(ApplicationEventPublisher.class),
                resourceRecordAccessGuard,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L,
                "客户A",
                "项目A",
                new BigDecimal("100.00"),
                "已收款",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldCreateSuccessfullyWithMatchingAllocations() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptAllocationRepository allocationRepository = mock(ReceiptAllocationRepository.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(0L);

        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        statement.setStatementNo("ST-001");
        statement.setClosingAmount(new BigDecimal("500.00"));

        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(customerStatementQueryService.findActiveById(21L)).thenReturn(Optional.of(statement));
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(receiptMapper.toResponse(any(Receipt.class))).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            return new ReceiptResponse(
                    r.getId(), r.getReceiptNo(), r.getCustomerCode(), r.getCustomerName(),
                    r.getProjectId(), r.getProjectName(), r.getSourceStatementId(),
                    r.getReceiptDate(), r.getPayType(), r.getAmount(), r.getStatus(),
                    r.getOperatorName(), r.getRemark(), List.of()
            );
        });

        ReceiptService service = service(
                receiptRepository, allocationRepository, idGenerator, receiptMapper,
                customerStatementQueryService, eventPublisher, resourceRecordAccessGuard, workflowTransitionGuard
        );

        ReceiptResponse result = service.create(buildRequest(
                21L, "客户A", "项目A", new BigDecimal("100.00"), "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        ));

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        verify(receiptRepository).save(any(Receipt.class));
    }

    @Test
    void shouldCreateSuccessfullyWithoutItemsAndWithSourceStatementId() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);

        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));

        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-002")).thenReturn(false);
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            r.setId(2L);
            return r;
        });
        when(receiptMapper.toResponse(any(Receipt.class))).thenReturn(
                new ReceiptResponse(2L, "SK-002", null, "客户A", null, "项目A", 21L,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                customerStatementQueryService, eventPublisher, resourceRecordAccessGuard,
                mock(WorkflowTransitionGuard.class)
        );

        ReceiptRequest request = new ReceiptRequest(
                "SK-002", null, "客户A", null, "项目A", 21L,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        );

        ReceiptResponse result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(2L);
    }

    @Test
    void shouldNotCheckDuplicateReceiptNoWhenUpdatingWithSameNo() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setDeletedFlag(false);
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(true);

        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
        when(receiptMapper.toResponse(any(Receipt.class))).thenReturn(
                new ReceiptResponse(1L, "SK-001", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptRequest request = new ReceiptRequest(
                "SK-001", null, "客户A", null, "项目A", null,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        );

        ReceiptResponse result = service.update(1L, request);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectUpdateWithChangedDuplicateReceiptNo() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setDeletedFlag(false);
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-DUP")).thenReturn(true);

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptRequest request = new ReceiptRequest(
                "SK-DUP", null, "客户A", null, "项目A", null,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        );

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(RuntimeException.class)
                ;
    }

    @Test
    void shouldValidateChangedUniqueReceiptNoOnUpdate() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setDeletedFlag(false);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-NEW")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReflectionTestUtils.invokeMethod(service, "validateUpdate", existing, new ReceiptRequest(
                "SK-NEW", null, "客户A", null, "项目A", null,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        ));
    }

    @Test
    void shouldReturnDetailForExistingReceipt() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setDeletedFlag(false);
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(existing));

        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
        when(receiptMapper.toResponse(existing)).thenReturn(
                new ReceiptResponse(1L, "SK-001", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptResponse result = service.detail(1L);

        assertThat(result).isNotNull();
        assertThat(result.receiptNo()).isEqualTo("SK-001");
    }

    @Test
    void shouldReturnDeletedReceiptDetailForAdminWhenRuntimeAllows() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
        Receipt deleted = buildReceiptEntity(1L, "SK-DELETED");
        deleted.setDeletedFlag(true);
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(receiptMapper.toResponse(deleted)).thenReturn(
                new ReceiptResponse(1L, "SK-DELETED", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "已删除", "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );
        CrudRuntimeSettings runtimeSettings = mock(CrudRuntimeSettings.class);
        when(runtimeSettings.shouldAdminSeeDeletedRecords()).thenReturn(true);
        ReflectionTestUtils.invokeMethod(service, "setCrudRuntimeSettings", runtimeSettings);

        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            ));

            ReceiptResponse result = service.detail(1L);

            assertThat(result.receiptNo()).isEqualTo("SK-DELETED");
            verify(receiptRepository).findById(1L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldReturnPageResults() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);

        Receipt receipt = buildReceiptEntity(1L, "SK-001");
        Page<Receipt> page = new PageImpl<>(List.of(receipt));
        when(receiptRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(receiptMapper.toResponse(receipt)).thenReturn(
                new ReceiptResponse(1L, "SK-001", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        Page<ReceiptResponse> result = service.page(
                new com.leo.erp.common.api.PageQuery(0, 10, "id", "desc"),
                new com.leo.erp.common.api.PageFilter(null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null)
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldReturnSearchResults() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);

        Receipt receipt = buildReceiptEntity(1L, "SK-001");
        Page<Receipt> page = new PageImpl<>(List.of(receipt));
        when(receiptRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(receiptMapper.toResponse(receipt)).thenReturn(
                new ReceiptResponse(1L, "SK-001", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        List<ReceiptResponse> results = service.search("SK", 10);

        assertThat(results).isNotNull();
    }

    @Test
    void shouldAllowValidStatusTransitionDraftToReceived() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptAllocationRepository allocationRepository = mock(ReceiptAllocationRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);

        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");
        existing.setAmount(new BigDecimal("100.00"));

        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        statement.setStatementNo("ST-001");
        statement.setClosingAmount(new BigDecimal("500.00"));

        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.setId(100L);
        allocation.setLineNo(1);
        allocation.setSourceStatementId(21L);
        allocation.setAllocatedAmount(new BigDecimal("100.00"));
        existing.setItems(new ArrayList<>(List.of(allocation)));

        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(customerStatementQueryService.findActiveById(21L)).thenReturn(Optional.of(statement));
        when(receiptMapper.toResponse(any(Receipt.class))).thenReturn(
                new ReceiptResponse(1L, "SK-001", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        StatusConstants.RECEIVED, "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, allocationRepository,
                new SnowflakeIdGenerator(0L), receiptMapper,
                customerStatementQueryService, eventPublisher,
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptResponse result = service.updateStatus(1L, StatusConstants.RECEIVED);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(StatusConstants.RECEIVED);
    }

    @Test
    void shouldRejectStatusTransitionToReceivedWithoutAllocations() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        existing.setAmount(new BigDecimal("100.00"));
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        ReceiptService service = service(
                receiptRepository,
                mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.updateStatus(1L, StatusConstants.RECEIVED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已收款状态必须填写核销明细");
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.updateStatus(1L, "已审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从");
    }

    @Test
    void shouldReturnSameResponseWhenStatusUnchanged() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(receiptMapper.toResponse(existing)).thenReturn(
                new ReceiptResponse(1L, "SK-001", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptResponse result = service.updateStatus(1L, StatusConstants.DRAFT);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldPublishEventsForAffectedStatementsOnSave() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);

        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));

        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-003")).thenReturn(false);
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            r.setId(3L);
            return r;
        });
        when(receiptMapper.toResponse(any(Receipt.class))).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            return new com.leo.erp.finance.receipt.web.dto.ReceiptResponse(
                    r.getId(), r.getReceiptNo(), r.getCustomerCode(), r.getCustomerName(),
                    r.getProjectId(), r.getProjectName(), r.getSourceStatementId(),
                    r.getReceiptDate(), r.getPayType(), r.getAmount(), r.getStatus(),
                    r.getOperatorName(), r.getRemark(), List.of()
            );
        });

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                customerStatementQueryService, eventPublisher, resourceRecordAccessGuard,
                mock(WorkflowTransitionGuard.class)
        );

        service.create(buildRequest(
                21L, "客户A", "项目A", new BigDecimal("100.00"), "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        ));

        verify(eventPublisher).publishEvent(any(ReceiptSettledEvent.class));
    }

    @Test
    void shouldRejectNegativeAllocationAmount() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setProjectName("项目A");
        statement.setSalesAmount(new BigDecimal("1000.00"));
        when(customerStatementQueryService.requireActiveById(21L)).thenReturn(statement);
        when(receiptRepository.existsByReceiptNoAndDeletedFlagFalse("SK-001")).thenReturn(false);

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                customerStatementQueryService, mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                21L, "客户A", "项目A", new BigDecimal("100.00"), "草稿",
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("-10.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额必须大于0");
    }

    @Test
    void shouldToAllocationResponseWhenStatementFound() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);

        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setDeletedFlag(false);
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");

        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.setId(100L);
        allocation.setLineNo(1);
        allocation.setSourceStatementId(21L);
        allocation.setAllocatedAmount(new BigDecimal("100.00"));
        existing.setItems(new ArrayList<>(List.of(allocation)));

        CustomerStatement statement = new CustomerStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(21L);
        statement.setStatementNo("ST-001");
        statement.setProjectName("项目A");
        statement.setClosingAmount(new BigDecimal("500.00"));

        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(customerStatementQueryService.findActiveById(21L)).thenReturn(Optional.of(statement));
        when(receiptMapper.toResponse(existing)).thenReturn(
                new ReceiptResponse(1L, "SK-001", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                customerStatementQueryService, mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptResponse result = service.detail(1L);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldToAllocationResponseWhenStatementNotFound() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        CustomerStatementQueryService customerStatementQueryService = mock(CustomerStatementQueryService.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);

        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setDeletedFlag(false);
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");

        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.setId(100L);
        allocation.setLineNo(1);
        allocation.setSourceStatementId(null);
        allocation.setAllocatedAmount(new BigDecimal("100.00"));
        existing.setItems(new ArrayList<>(List.of(allocation)));

        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(receiptMapper.toResponse(existing)).thenReturn(
                new ReceiptResponse(1L, "SK-001", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                customerStatementQueryService, mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptResponse result = service.detail(1L);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldHandleBeforeStatusUpdateForNonSettledStatus() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setStatus(StatusConstants.RECEIVED);
        existing.setDeletedFlag(false);
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptMapper.toResponse(any(Receipt.class))).thenReturn(
                new ReceiptResponse(1L, "SK-001", null, "客户A", null, "项目A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        StatusConstants.DRAFT, "财务A", null, null)
        );

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), receiptMapper,
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptResponse result = service.updateStatus(1L, StatusConstants.DRAFT);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void shouldDeleteSuccessfully() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        service.delete(1L);

        verify(receiptRepository).save(any(Receipt.class));
    }

    @Test
    void shouldRejectDeleteWhenStatusIsProtected() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setStatus(StatusConstants.RECEIVED);
        existing.setDeletedFlag(false);
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除");
    }

    @Test
    void shouldRejectEditWhenStatusIsProtected() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        Receipt existing = buildReceiptEntity(1L, "SK-001");
        existing.setStatus(StatusConstants.RECEIVED);
        existing.setDeletedFlag(false);
        when(receiptRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptRequest request = new ReceiptRequest(
                "SK-001", null, "客户A", null, "项目A", null,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("200.00"), "草稿", "财务A", null, List.of()
        );

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能编辑");
    }

    @Test
    void shouldRejectWhenEntityNotFound() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        when(receiptRepository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.detail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款单不存在");
    }

    @Test
    void shouldRejectWhenUpdateEntityNotFound() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        when(receiptRepository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        ReceiptRequest request = new ReceiptRequest(
                "SK-001", null, "客户A", null, "项目A", null,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        );

        assertThatThrownBy(() -> service.update(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款单不存在");
    }

    @Test
    void shouldRejectWhenStatusTransitionEntityNotFound() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        when(receiptRepository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.updateStatus(999L, "草稿"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款单不存在");
    }

    @Test
    void shouldRejectWhenDeleteEntityNotFound() {
        ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
        when(receiptRepository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        ReceiptService service = service(
                receiptRepository, mock(ReceiptAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(ReceiptMapper.class),
                mock(CustomerStatementQueryService.class), mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class), mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款单不存在");
    }

    private ReceiptService service(ReceiptRepository receiptRepository,
                                   ReceiptAllocationRepository receiptAllocationRepository,
                                   SnowflakeIdGenerator snowflakeIdGenerator,
                                   ReceiptMapper receiptMapper,
                                   CustomerStatementQueryService customerStatementQueryService,
                                   ApplicationEventPublisher eventPublisher,
                                   ResourceRecordAccessGuard resourceRecordAccessGuard,
                                   WorkflowTransitionGuard workflowTransitionGuard) {
        ReceiptStatementAllocationValidator statementAllocationValidator = new ReceiptStatementAllocationValidator(
                receiptAllocationRepository,
                customerStatementQueryService,
                resourceRecordAccessGuard
        );
        ReceiptAllocationService allocationService = new ReceiptAllocationService(statementAllocationValidator);
        ReceiptSettlementSyncService settlementSyncService = new ReceiptSettlementSyncService(eventPublisher);
        return new ReceiptService(
                receiptRepository,
                snowflakeIdGenerator,
                receiptMapper,
                new ReceiptApplyService(workflowTransitionGuard, allocationService, settlementSyncService),
                allocationService,
                new ReceiptAllocationResponseAssembler(customerStatementQueryService),
                settlementSyncService
        );
    }

    private Receipt buildReceiptEntity(Long id, String receiptNo) {
        Receipt receipt = new Receipt();
        receipt.setId(id);
        receipt.setReceiptNo(receiptNo);
        receipt.setCustomerName("客户A");
        receipt.setProjectName("项目A");
        receipt.setReceiptDate(LocalDate.of(2026, 4, 26));
        receipt.setPayType("银行转账");
        receipt.setAmount(new BigDecimal("100.00"));
        receipt.setStatus(StatusConstants.DRAFT);
        receipt.setOperatorName("财务A");
        receipt.setItems(new ArrayList<>());
        receipt.setOriginalAllocationStatementIds(new java.util.LinkedHashSet<>());
        return receipt;
    }

    private ReceiptRequest buildRequest(Long sourceStatementId,
                                        String customerName,
                                        String projectName,
                                        BigDecimal amount,
                                        String status,
                                        List<ReceiptAllocationRequest> items) {
        return buildRequest(null, sourceStatementId, customerName, projectName, amount, status, items);
    }

    private ReceiptRequest buildRequest(String customerCode,
                                        Long sourceStatementId,
                                        String customerName,
                                        String projectName,
                                        BigDecimal amount,
                                        String status,
                                        List<ReceiptAllocationRequest> items) {
        return new ReceiptRequest(
                "SK-001",
                customerCode,
                customerName,
                null,
                projectName,
                sourceStatementId,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                amount,
                status,
                "财务A",
                null,
                items
        );
    }
}
