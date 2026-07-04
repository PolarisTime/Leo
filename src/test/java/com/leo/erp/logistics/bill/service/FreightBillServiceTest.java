package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.service.CrudRuntimeSettings;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.mapper.FreightBillMapper;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreightBillServiceTest {

    @BeforeEach
    void setUpIdGenerator() {
        SecurityContextHolder.clearContext();
    }

    private FreightBillService createService(FreightBillRepository repository) {
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenReturn(new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of()));
        return createService(repository, sourceService);
    }

    private FreightBillService createService(FreightBillRepository repository,
                                             FreightBillSourceService sourceService) {
        return createService(repository, mock(SalesOutboundRepository.class), sourceService);
    }

    private FreightBillService createService(FreightBillRepository repository,
                                             SalesOutboundRepository salesOutboundRepository,
                                             FreightBillSourceService sourceService) {
        return createService(repository, salesOutboundRepository, sourceService, null, null);
    }

    private FreightBillService createService(FreightBillRepository repository,
                                             SalesOutboundRepository salesOutboundRepository,
                                             FreightBillSourceService sourceService,
                                             CarrierRepository carrierRepository,
                                             CompanySettingService companySettingService) {
        return new FreightBillService(
                repository,
                salesOutboundRepository,
                new SnowflakeIdGenerator(),
                Mappers.getMapper(FreightBillMapper.class),
                sourceService,
                new FreightBillApplyService(),
                mock(WorkflowTransitionGuard.class),
                carrierRepository,
                companySettingService
        );
    }

    private FreightBillItemRequest buildItemRequest(String sourceNo) {
        return new FreightBillItemRequest(
                sourceNo,
                "客户甲",
                "项目甲",
                "M001",
                null,
                "宝钢",
                "钢材",
                "HRB400",
                "18",
                "12m",
                2,
                "件",
                new BigDecimal("1.250"),
                0,
                "B001",
                null,
                "一号库"
        );
    }

    private FreightBillRequest buildRequest(String billNo, FreightBillItemRequest... items) {
        return new FreightBillRequest(
                billNo,
                "物流甲",
                null,
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 5, 4),
                new BigDecimal("20.00"),
                null,
                null,
                List.of(items)
        );
    }

    @Test
    void shouldReturnSalesOutboundImportCandidates() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(101L);
        outbound.setOutboundNo("OB-001");
        outbound.setSalesOrderNo("SO-001");
        outbound.setCustomerName("客户甲");
        outbound.setProjectName("项目甲");
        outbound.setWarehouseName("一号库");
        outbound.setOutboundDate(LocalDate.of(2026, 6, 1));
        outbound.setTotalWeight(new BigDecimal("12.500"));
        outbound.setTotalAmount(new BigDecimal("3000.00"));
        outbound.setStatus(StatusConstants.AUDITED);

        when(salesOutboundRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(outbound)));

        FreightBillService service = createService(repository, salesOutboundRepository, sourceService);

        PageFilter filter = new PageFilter(
                "OB",
                StatusConstants.AUDITED,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                "客户甲",
                "项目甲",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        var page = service.importCandidates(PageQuery.of(0, 20, null, null), filter);

        assertThat(page.getContent()).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo(101L);
            assertThat(candidate.outboundNo()).isEqualTo("OB-001");
            assertThat(candidate.salesOrderNo()).isEqualTo("SO-001");
            assertThat(candidate.customerName()).isEqualTo("客户甲");
            assertThat(candidate.projectName()).isEqualTo("项目甲");
            assertThat(candidate.warehouseName()).isEqualTo("一号库");
            assertThat(candidate.outboundDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(candidate.totalWeight()).isEqualByComparingTo("12.500");
            assertThat(candidate.totalAmount()).isEqualByComparingTo("3000.00");
            assertThat(candidate.status()).isEqualTo(StatusConstants.AUDITED);
        });
        verify(salesOutboundRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void pageShouldDelegateToRepositoryAndMapResponses() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill bill = new FreightBill();
        bill.setId(9L);
        bill.setBillNo("FB-PAGE");
        bill.setCarrierName("物流甲");
        bill.setSettlementCompanyId(7L);
        bill.setSettlementCompanyName("结算主体A");
        bill.setCustomerName("客户甲");
        bill.setProjectName("项目甲");
        bill.setBillTime(LocalDate.of(2026, 6, 1));
        bill.setUnitPrice(new BigDecimal("20.00"));
        bill.setTotalWeight(new BigDecimal("2.500"));
        bill.setTotalFreight(new BigDecimal("50.00"));
        bill.setStatus(StatusConstants.UNAUDITED);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bill)));
        FreightBillService service = createService(repository);

        var page = service.page(
                PageQuery.of(0, 20, "billTime", "desc"),
                PageFilter.of(
                        "FB",
                        "物流甲",
                        7L,
                        StatusConstants.UNAUDITED,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30)
                )
        );

        assertThat(page.getContent()).singleElement()
                .satisfies(response -> assertThat(response.billNo()).isEqualTo("FB-PAGE"));
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ======================== create tests ========================

    @Test
    void constructorsWithoutOptionalResolversShouldStillCreateService() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenReturn(new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of()));
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-CTOR1")).thenReturn(false);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-CTOR2")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FreightBillService withoutCarrierRepository = new FreightBillService(
                repository,
                mock(SalesOutboundRepository.class),
                new SnowflakeIdGenerator(),
                Mappers.getMapper(FreightBillMapper.class),
                sourceService,
                new FreightBillApplyService(),
                mock(WorkflowTransitionGuard.class)
        );
        FreightBillService withoutCompanySettingService = new FreightBillService(
                repository,
                mock(SalesOutboundRepository.class),
                new SnowflakeIdGenerator(),
                Mappers.getMapper(FreightBillMapper.class),
                sourceService,
                new FreightBillApplyService(),
                mock(WorkflowTransitionGuard.class),
                mock(CarrierRepository.class)
        );

        assertThat(withoutCarrierRepository.create(buildRequest("FB-CTOR1", buildItemRequest("OB-001"))).billNo())
                .isEqualTo("FB-CTOR1");
        assertThat(withoutCompanySettingService.create(buildRequest("FB-CTOR2", buildItemRequest("OB-002"))).billNo())
                .isEqualTo("FB-CTOR2");
    }

    @Test
    void shouldSaveVehiclePlateAndExposeItInResponse() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-001")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillResponse response = service.create(new FreightBillRequest(
                "FB-001",
                "物流甲",
                "苏A12345",
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 5, 4),
                new BigDecimal("20.00"),
                null,
                null,
                List.of(new FreightBillItemRequest(
                        "OB-001",
                        "客户甲",
                        "项目甲",
                        "M001",
                        null,
                        "宝钢",
                        "钢材",
                        "HRB400",
                        "18",
                        "12m",
                        2,
                        "件",
                        new BigDecimal("1.250"),
                        0,
                        "B001",
                        null,
                        "一号库"
                ))
        ));

        assertThat(response.vehiclePlate()).isEqualTo("苏A12345");
        assertThat(response.totalWeight()).isEqualByComparingTo("2.500");
        assertThat(response.totalFreight()).isEqualByComparingTo("50.00");

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getItems())
                .singleElement()
                .satisfies(item -> assertThat(item.getMaterialName()).isEqualTo("宝钢"));
    }

    @Test
    void shouldThrowWhenBillNoAlreadyExists() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-DUP")).thenReturn(true);

        FreightBillService service = createService(repository);

        assertThatThrownBy(() -> service.create(buildRequest("FB-DUP", buildItemRequest("OB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单号已存在");
    }

    @Test
    void shouldCalculateTotalWeightAndFreightFromMultipleItems() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-002")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item1 = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );
        FreightBillItemRequest item2 = new FreightBillItemRequest(
                "OB-002", "客户甲", "项目甲", "M002", null, "宝钢", "钢材", "HRB400", "20", "12m",
                3, "件", new BigDecimal("2.000"), 0, "B002", null, "一号库"
        );

        FreightBillRequest request = new FreightBillRequest(
                "FB-002", "物流甲", null, "客户甲", "项目甲",
                LocalDate.of(2026, 5, 4), new BigDecimal("15.00"),
                null, null, List.of(item1, item2)
        );

        FreightBillResponse response = service.create(request);

        assertThat(response.totalWeight()).isEqualByComparingTo("8.500");
        assertThat(response.totalFreight()).isEqualByComparingTo("127.50");
    }

    @Test
    void shouldResolveMaterialNameFromBrandWhenMaterialNameIsNull() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-003")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        service.create(buildRequest("FB-003", item));

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getItems().get(0).getMaterialName()).isEqualTo("宝钢");
    }

    @Test
    void shouldUseExplicitMaterialNameWhenProvided() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-004")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", "显式名称", "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        service.create(buildRequest("FB-004", item));

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getItems().get(0).getMaterialName()).isEqualTo("显式名称");
    }

    @Test
    void shouldConvertEmptyVehiclePlateToNull() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-005")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillRequest request = new FreightBillRequest(
                "FB-005", "物流甲", "   ", "客户甲", "项目甲",
                LocalDate.of(2026, 5, 4), new BigDecimal("20.00"),
                null, null, List.of(buildItemRequest("OB-001"))
        );

        FreightBillResponse response = service.create(request);

        assertThat(response.vehiclePlate()).isNull();
    }

    @Test
    void shouldUseExplicitSettlementCompanyInsteadOfCarrierDefault() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        CarrierRepository carrierRepository = mock(CarrierRepository.class);
        CompanySettingService companySettingService = mock(CompanySettingService.class);
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenReturn(new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of()));
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-SETTLEMENT")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Carrier carrier = new Carrier();
        carrier.setDefaultSettlementCompanyId(7L);
        carrier.setDefaultSettlementCompanyName("嘉兴颖捷建材有限公司");
        when(carrierRepository.findFirstByCarrierNameAndDeletedFlagFalseOrderByCarrierCodeAsc("物流甲"))
                .thenReturn(Optional.of(carrier));
        when(companySettingService.requireActiveSettlementCompany(9L))
                .thenReturn(companySetting(9L, "TEST9"));
        FreightBillService service = createService(
                repository,
                mock(SalesOutboundRepository.class),
                sourceService,
                carrierRepository,
                companySettingService
        );

        FreightBillResponse response = service.create(new FreightBillRequest(
                "FB-SETTLEMENT",
                "物流甲",
                9L,
                null,
                null,
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 5, 4),
                new BigDecimal("20.00"),
                null,
                null,
                List.of(buildItemRequest("OB-001"))
        ));

        assertThat(response.settlementCompanyId()).isEqualTo(9L);
        assertThat(response.settlementCompanyName()).isEqualTo("TEST9");
    }

    @Test
    void shouldUseRequestedSettlementCompanyNameWhenCompanyServiceIsMissing() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenReturn(new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of()));
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-REQ-SETTLEMENT")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FreightBillService service = createService(
                repository,
                mock(SalesOutboundRepository.class),
                sourceService,
                mock(CarrierRepository.class),
                null
        );

        FreightBillResponse response = service.create(new FreightBillRequest(
                "FB-REQ-SETTLEMENT",
                "物流甲",
                12L,
                "  结算主体B  ",
                null,
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 5, 4),
                new BigDecimal("20.00"),
                null,
                null,
                List.of(buildItemRequest("OB-001"))
        ));

        assertThat(response.settlementCompanyId()).isEqualTo(12L);
        assertThat(response.settlementCompanyName()).isEqualTo("结算主体B");
    }

    @Test
    void shouldClearSettlementCompanyWhenCarrierNameIsBlank() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenReturn(new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of()));
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-BLANK-CARRIER")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FreightBillService service = createService(
                repository,
                mock(SalesOutboundRepository.class),
                sourceService,
                mock(CarrierRepository.class),
                null
        );

        FreightBillResponse response = service.create(new FreightBillRequest(
                "FB-BLANK-CARRIER",
                "   ",
                null,
                null,
                null,
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 5, 4),
                new BigDecimal("20.00"),
                null,
                null,
                List.of(buildItemRequest("OB-001"))
        ));

        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
    }

    @Test
    void shouldClearSettlementCompanyWhenCarrierHasNoDefault() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        CarrierRepository carrierRepository = mock(CarrierRepository.class);
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenReturn(new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of()));
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-NO-CARRIER")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(carrierRepository.findFirstByCarrierNameAndDeletedFlagFalseOrderByCarrierCodeAsc("物流甲"))
                .thenReturn(Optional.empty());
        FreightBillService service = createService(
                repository,
                mock(SalesOutboundRepository.class),
                sourceService,
                carrierRepository,
                null
        );

        FreightBillResponse response = service.create(buildRequest("FB-NO-CARRIER", buildItemRequest("OB-001")));

        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
    }

    @Test
    void shouldUseCarrierDefaultSettlementCompanyWhenPresent() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        CarrierRepository carrierRepository = mock(CarrierRepository.class);
        Carrier carrier = new Carrier();
        carrier.setDefaultSettlementCompanyId(7L);
        carrier.setDefaultSettlementCompanyName("默认结算主体");
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenReturn(new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of()));
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-CARRIER-DEFAULT")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(carrierRepository.findFirstByCarrierNameAndDeletedFlagFalseOrderByCarrierCodeAsc("物流甲"))
                .thenReturn(Optional.of(carrier));
        FreightBillService service = createService(
                repository,
                mock(SalesOutboundRepository.class),
                sourceService,
                carrierRepository,
                null
        );

        FreightBillResponse response = service.create(buildRequest("FB-CARRIER-DEFAULT", buildItemRequest("OB-001")));

        assertThat(response.settlementCompanyId()).isEqualTo(7L);
        assertThat(response.settlementCompanyName()).isEqualTo("默认结算主体");
    }

    @Test
    void shouldSetDefaultStatusToUnaudited() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-006")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillRequest request = buildRequest("FB-006", buildItemRequest("OB-001"));

        service.create(request);

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getStatus()).isEqualTo(StatusConstants.UNAUDITED);
    }

    @Test
    void shouldNormalizeQuantityUnitToPieceWhenBlank() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-008")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        service.create(buildRequest("FB-008", item));

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getItems().get(0).getQuantityUnit()).isEqualTo("件");
    }

    @Test
    void shouldAssignLineNumbersSequentially() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-009")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item1 = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );
        FreightBillItemRequest item2 = new FreightBillItemRequest(
                "OB-002", "客户乙", "项目乙", "M002", null, "沙钢", "钢材", "HRB400", "20", "12m",
                3, "件", new BigDecimal("2.000"), 0, "B002", null, "二号库"
        );

        service.create(new FreightBillRequest(
                "FB-009", "物流甲", null, "客户甲", "项目甲",
                LocalDate.of(2026, 5, 4), new BigDecimal("20.00"),
                null, null, List.of(item1, item2)
        ));

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        List<FreightBillItem> savedItems = billCaptor.getValue().getItems();
        assertThat(savedItems.get(0).getLineNo()).isEqualTo(1);
        assertThat(savedItems.get(1).getLineNo()).isEqualTo(2);
    }

    @Test
    void shouldResolveMultiCustomerLabel() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-010")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item1 = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );
        FreightBillItemRequest item2 = new FreightBillItemRequest(
                "OB-002", "客户乙", "项目乙", "M002", null, "沙钢", "钢材", "HRB400", "20", "12m",
                3, "件", new BigDecimal("2.000"), 0, "B002", null, "二号库"
        );

        service.create(new FreightBillRequest(
                "FB-010", "物流甲", null, "客户甲", "项目甲",
                LocalDate.of(2026, 5, 4), new BigDecimal("20.00"),
                null, null, List.of(item1, item2)
        ));

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getCustomerName()).isEqualTo("多客户");
        assertThat(billCaptor.getValue().getProjectName()).isEqualTo("多项目");
    }

    @Test
    void shouldResolveSingleCustomerLabel() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-011")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item1 = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );
        FreightBillItemRequest item2 = new FreightBillItemRequest(
                "OB-002", "客户甲", "项目甲", "M002", null, "沙钢", "钢材", "HRB400", "20", "12m",
                3, "件", new BigDecimal("2.000"), 0, "B002", null, "一号库"
        );

        service.create(new FreightBillRequest(
                "FB-011", "物流甲", null, "客户甲", "项目甲",
                LocalDate.of(2026, 5, 4), new BigDecimal("20.00"),
                null, null, List.of(item1, item2)
        ));

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getCustomerName()).isEqualTo("客户甲");
        assertThat(billCaptor.getValue().getProjectName()).isEqualTo("项目甲");
    }

    @Test
    void shouldSetFieldsFromRequestOnCreate() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-012")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillRequest request = new FreightBillRequest(
                "FB-012", "物流乙", "苏B99999", "客户乙", "项目乙",
                LocalDate.of(2026, 6, 1), new BigDecimal("25.50"),
                null, "备注信息", List.of(buildItemRequest("OB-001"))
        );

        FreightBillResponse response = service.create(request);

        assertThat(response.billNo()).isEqualTo("FB-012");
        assertThat(response.carrierName()).isEqualTo("物流乙");
        assertThat(response.billTime()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.unitPrice()).isEqualByComparingTo("25.50");
        assertThat(response.remark()).isEqualTo("备注信息");
    }

    // ======================== update tests ========================

    @Test
    void shouldUpdateExistingFreightBill() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setBillNo("FB-OLD");
        existing.setStatus(StatusConstants.UNAUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-NEW")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillRequest request = new FreightBillRequest(
                "FB-NEW", "物流丙", "苏C11111", "客户丙", "项目丙",
                LocalDate.of(2026, 7, 1), new BigDecimal("30.00"),
                null, null, List.of(buildItemRequest("OB-010"))
        );

        service.update(1L, request);

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getBillNo()).isEqualTo("FB-OLD");
    }

    @Test
    void shouldPreserveBillNoOnUpdateEvenWhenRequestHasDifferentBillNo() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setBillNo("FB-OLD");
        existing.setStatus(StatusConstants.UNAUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillRequest request = buildRequest("FB-DUP", buildItemRequest("OB-001"));

        service.update(1L, request);

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getBillNo()).isEqualTo("FB-OLD");
    }

    @Test
    void shouldNotThrowWhenUpdateRequestHasDifferentBillNo() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setBillNo("FB-OLD");
        existing.setStatus(StatusConstants.UNAUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillRequest request = buildRequest("FB-DUP", buildItemRequest("OB-001"));

        FreightBillResponse response = service.update(1L, request);

        assertThat(response).isNotNull();
        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getBillNo()).isEqualTo("FB-OLD");
    }

    @Test
    void shouldThrowWhenUpdateNonExistentEntity() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        FreightBillService service = createService(repository);

        assertThatThrownBy(() -> service.update(999L, buildRequest("FB-001", buildItemRequest("OB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单不存在");
    }

    @Test
    void shouldThrowWhenUpdateRequestBillNoConflictsWithExistingBill() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setBillNo("FB-OLD");
        existing.setStatus(StatusConstants.UNAUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-DUP")).thenReturn(true);

        FreightBillService service = new FreightBillService(
                repository,
                mock(SalesOutboundRepository.class),
                new SnowflakeIdGenerator(),
                Mappers.getMapper(FreightBillMapper.class),
                mock(FreightBillSourceService.class),
                new FreightBillApplyService(),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        ) {
            @Override
            protected FreightBillRequest normalizeUpdateRequest(FreightBill entity, FreightBillRequest request) {
                return request;
            }
        };

        assertThatThrownBy(() -> service.update(1L, buildRequest("FB-DUP", buildItemRequest("OB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单号已存在");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldAllowUpdateRequestBillNoChangeWhenNewBillNoDoesNotExist() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setBillNo("FB-OLD");
        existing.setStatus(StatusConstants.UNAUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-FREE")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenReturn(new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of()));

        FreightBillService service = new FreightBillService(
                repository,
                mock(SalesOutboundRepository.class),
                new SnowflakeIdGenerator(),
                Mappers.getMapper(FreightBillMapper.class),
                sourceService,
                new FreightBillApplyService(),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        ) {
            @Override
            protected FreightBillRequest normalizeUpdateRequest(FreightBill entity, FreightBillRequest request) {
                return request;
            }
        };

        FreightBillResponse response = service.update(1L, buildRequest("FB-FREE", buildItemRequest("OB-001")));

        assertThat(response.billNo()).isEqualTo("FB-FREE");
        verify(repository).existsByBillNoAndDeletedFlagFalse("FB-FREE");
    }

    // ======================== detail tests ========================

    @Test
    void shouldReturnDetailWithItems() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill entity = new FreightBill();
        entity.setId(1L);
        entity.setBillNo("FB-001");
        entity.setCarrierName("物流甲");
        entity.setCustomerName("客户甲");
        entity.setProjectName("项目甲");
        entity.setBillTime(LocalDate.of(2026, 5, 4));
        entity.setUnitPrice(new BigDecimal("20.00"));
        entity.setTotalWeight(new BigDecimal("2.500"));
        entity.setTotalFreight(new BigDecimal("50.00"));
        entity.setStatus(StatusConstants.UNAUDITED);

        FreightBillItem item = new FreightBillItem();
        item.setId(100L);
        item.setLineNo(1);
        item.setSourceNo("OB-001");
        item.setCustomerName("客户甲");
        item.setProjectName("项目甲");
        item.setMaterialCode("M001");
        item.setMaterialName("宝钢");
        item.setBrand("宝钢");
        item.setCategory("钢材");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setQuantity(2);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.250"));
        item.setPiecesPerBundle(0);
        item.setBatchNo("B001");
        item.setWeightTon(new BigDecimal("2.500"));
        item.setWarehouseName("一号库");

        entity.setItems(List.of(item));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(entity));

        FreightBillService service = createService(repository);

        FreightBillResponse response = service.detail(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.billNo()).isEqualTo("FB-001");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceNo()).isEqualTo("OB-001");
        assertThat(response.items().get(0).materialName()).isEqualTo("宝钢");
    }

    @Test
    void shouldReturnMapperResponseWhenSavedEntityHasNoItems() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill entity = new FreightBill();
        entity.setId(1L);
        entity.setBillNo("FB-NO-ITEM");
        entity.setCarrierName("物流甲");
        entity.setCustomerName("客户甲");
        entity.setProjectName("项目甲");
        entity.setBillTime(LocalDate.of(2026, 5, 4));
        entity.setUnitPrice(new BigDecimal("20.00"));
        entity.setTotalWeight(BigDecimal.ZERO);
        entity.setTotalFreight(BigDecimal.ZERO);
        entity.setStatus(StatusConstants.UNAUDITED);
        entity.setItems(new ArrayList<>());
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(entity));
        FreightBillService service = createService(repository);

        FreightBillResponse response = service.detail(1L);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void shouldUseBrandAsMaterialNameWhenMaterialNameIsNullOnDetail() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill entity = new FreightBill();
        entity.setId(1L);
        entity.setBillNo("FB-001");
        entity.setCarrierName("物流甲");
        entity.setCustomerName("客户甲");
        entity.setProjectName("项目甲");
        entity.setBillTime(LocalDate.of(2026, 5, 4));
        entity.setUnitPrice(new BigDecimal("20.00"));
        entity.setTotalWeight(new BigDecimal("2.500"));
        entity.setTotalFreight(new BigDecimal("50.00"));
        entity.setStatus(StatusConstants.UNAUDITED);

        FreightBillItem item = new FreightBillItem();
        item.setId(100L);
        item.setLineNo(1);
        item.setSourceNo("OB-001");
        item.setCustomerName("客户甲");
        item.setProjectName("项目甲");
        item.setMaterialCode("M001");
        item.setMaterialName(null);
        item.setBrand("沙钢");
        item.setCategory("钢材");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setQuantity(2);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.250"));
        item.setPiecesPerBundle(0);
        item.setWeightTon(new BigDecimal("2.500"));

        entity.setItems(List.of(item));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(entity));

        FreightBillService service = createService(repository);

        FreightBillResponse response = service.detail(1L);

        assertThat(response.items().get(0).materialName()).isEqualTo("沙钢");
    }

    @Test
    void shouldThrowWhenDetailEntityNotFound() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        FreightBillService service = createService(repository);

        assertThatThrownBy(() -> service.detail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单不存在");
    }

    @Test
    void shouldFindVisibleEntityByIdWhenAdminViewsDeletedRecords() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill deleted = new FreightBill();
        deleted.setId(1L);
        deleted.setBillNo("FB-DELETED");
        deleted.setCarrierName("物流甲");
        deleted.setCustomerName("客户甲");
        deleted.setProjectName("项目甲");
        deleted.setBillTime(LocalDate.of(2026, 5, 4));
        deleted.setUnitPrice(new BigDecimal("20.00"));
        deleted.setTotalWeight(BigDecimal.ZERO);
        deleted.setTotalFreight(BigDecimal.ZERO);
        deleted.setStatus(StatusConstants.DELETED);
        deleted.setDeletedFlag(true);
        deleted.setItems(new ArrayList<>());
        when(repository.findById(1L)).thenReturn(Optional.of(deleted));
        FreightBillService service = createService(repository);
        CrudRuntimeSettings runtimeSettings = mock(CrudRuntimeSettings.class);
        when(runtimeSettings.shouldAdminSeeDeletedRecords()).thenReturn(true);
        ReflectionTestUtils.invokeMethod(service, "setCrudRuntimeSettings", runtimeSettings);
        setupAdminPrincipal();

        FreightBillResponse response = service.detail(1L);

        assertThat(response.billNo()).isEqualTo("FB-DELETED");
        verify(repository).findById(1L);
        verify(repository, never()).findByIdAndDeletedFlagFalse(1L);
    }

    // ======================== delete tests ========================

    @Test
    void shouldSoftDeleteEntity() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setStatus(StatusConstants.UNAUDITED);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        service.delete(1L);

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().isDeletedFlag()).isTrue();
    }

    @Test
    void shouldThrowWhenDeleteNonExistentEntity() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        FreightBillService service = createService(repository);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单不存在");
    }

    // ======================== updateStatus tests ========================

    @Test
    void shouldUpdateStatus() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setStatus(StatusConstants.UNAUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        service.updateStatus(1L, StatusConstants.AUDITED);

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getStatus()).isEqualTo(StatusConstants.AUDITED);
    }

    @Test
    void shouldReturnSameResponseWhenStatusNotChanged() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setBillNo("FB-001");
        existing.setStatus(StatusConstants.AUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        FreightBillService service = createService(repository);

        service.updateStatus(1L, StatusConstants.AUDITED);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenStatusTransitionNotAllowed() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setStatus(StatusConstants.AUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        FreightBillService service = createService(repository);

        assertThatThrownBy(() -> service.updateStatus(1L, "完成销售"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowWhenUpdateStatusForNonExistentEntity() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        FreightBillService service = createService(repository);

        assertThatThrownBy(() -> service.updateStatus(999L, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单不存在");
    }

    // ======================== assertSourceOutboundsNotOccupied tests ========================

    @Test
    void shouldThrowWhenSourceOutboundAlreadyOccupied() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-OCC")).thenReturn(false);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenThrow(new BusinessException(
                        com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                        "销售出库单OB-DUP已归集到物流单FB-OTHER"
                ));
        FreightBillService service = createService(repository, sourceService);

        FreightBillItemRequest item = buildItemRequest("OB-DUP");

        assertThatThrownBy(() -> service.create(buildRequest("FB-OCC", item)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售出库单")
                .hasMessageContaining("OB-DUP")
                .hasMessageContaining("FB-OTHER");
    }

    @Test
    void shouldNotCheckOccupiedWhenSourceNoIsEmpty() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-EMPTY")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item = new FreightBillItemRequest(
                "", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        service.create(buildRequest("FB-EMPTY", item));

        verify(repository, never()).findAllBySourceNosExcludingCurrentBill(anyCollection(), any());
    }

    @Test
    void shouldIncludeCarrierNameInOccupiedErrorMessage() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-OCC2")).thenReturn(false);
        FreightBillSourceService sourceService = mock(FreightBillSourceService.class);
        when(sourceService.validateSources(any(FreightBillRequest.class), any()))
                .thenThrow(new BusinessException(
                        com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                        "销售出库单SRC-001已归集到物流单FB-EXIST（物流商：快速物流）"
                ));
        FreightBillService service = createService(repository, sourceService);

        FreightBillItemRequest item = buildItemRequest("SRC-001");

        assertThatThrownBy(() -> service.create(buildRequest("FB-OCC2", item)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快速物流");
    }

    // ======================== search tests ========================

    @SuppressWarnings("unchecked")
    @Test
    void searchDelegatesToRepository() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        FreightBillService service = createService(repository);

        List<FreightBillResponse> results = service.search("test", 100);

        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void importCandidatesShouldRejectNonAuditedRequestedStatusInSpecification() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillService service = createService(repository);
        Specification<SalesOutbound> spec = ReflectionTestUtils.invokeMethod(
                service,
                "importableSalesOutboundStatus",
                StatusConstants.UNAUDITED
        );
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        Root<SalesOutbound> root = mockRoot();
        Predicate disjunction = mock(Predicate.class);
        when(cb.disjunction()).thenReturn(disjunction);

        Predicate predicate = spec.toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(disjunction);
        verify(cb).disjunction();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void importCandidatesShouldFilterAuditedStatusInSpecification() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillService service = createService(repository);
        Specification<SalesOutbound> spec = ReflectionTestUtils.invokeMethod(
                service,
                "importableSalesOutboundStatus",
                StatusConstants.AUDITED
        );
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        Root<SalesOutbound> root = mockRoot();
        Path<Object> statusPath = root.get("status");
        Predicate audited = mock(Predicate.class);

        when(cb.equal(statusPath, StatusConstants.AUDITED)).thenReturn(audited);

        Predicate predicate = spec.toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(audited);
        verify(cb).equal(statusPath, StatusConstants.AUDITED);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void importCandidatesShouldFilterAuditedStatusWhenRequestedStatusIsBlank() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillService service = createService(repository);
        Specification<SalesOutbound> spec = ReflectionTestUtils.invokeMethod(
                service,
                "importableSalesOutboundStatus",
                "   "
        );
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        Root<SalesOutbound> root = mockRoot();
        Path<Object> statusPath = root.get("status");
        Predicate audited = mock(Predicate.class);

        when(cb.equal(statusPath, StatusConstants.AUDITED)).thenReturn(audited);

        Predicate predicate = spec.toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(audited);
        verify(cb).equal(statusPath, StatusConstants.AUDITED);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void importCandidatesShouldBuildUnoccupiedSourceSubquerySpecification() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillService service = createService(repository);
        Specification<SalesOutbound> spec = ReflectionTestUtils.invokeMethod(service, "sourceOutboundNotOccupied");
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        Subquery<Long> subquery = mock(Subquery.class);
        Root<FreightBill> billRoot = mockRoot();
        Join<FreightBill, FreightBillItem> itemJoin = mock(Join.class, Answers.RETURNS_DEEP_STUBS);
        Root<SalesOutbound> root = mockRoot();
        Predicate billNotDeleted = mock(Predicate.class);
        Predicate sourceNoMatches = mock(Predicate.class);
        Predicate exists = mock(Predicate.class);
        Predicate notExists = mock(Predicate.class);
        Expression<Long> literal = mock(Expression.class);
        Expression<String> trimmedSourceNo = mock(Expression.class);

        when(cb.isFalse(any(Expression.class))).thenReturn(billNotDeleted);
        when(cb.equal(any(Expression.class), any(Expression.class))).thenReturn(sourceNoMatches);
        when(cb.literal(1L)).thenReturn(literal);
        when(cb.trim(any(Expression.class))).thenReturn(trimmedSourceNo);
        when(cb.exists(subquery)).thenReturn(exists);
        when(cb.not(exists)).thenReturn(notExists);
        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(FreightBill.class)).thenReturn(billRoot);
        org.mockito.Mockito.doReturn(itemJoin).when(billRoot).<FreightBill, FreightBillItem>join("items");
        when(subquery.select(literal)).thenReturn(subquery);
        when(subquery.where(any(Predicate[].class))).thenReturn(subquery);

        Predicate predicate = spec.toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(notExists);
        verify(query).subquery(Long.class);
        verify(billRoot).<FreightBill, FreightBillItem>join("items");
        verify(cb).isFalse(billRoot.get("deletedFlag"));
        verify(cb).trim(itemJoin.get("sourceNo"));
        verify(cb).equal(trimmedSourceNo, root.get("outboundNo"));
        verify(cb).exists(subquery);
        verify(cb).not(exists);
    }

    // ======================== apply status normalization tests ========================

    @Test
    void shouldPreserveExplicitStatusOnCreate() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-STS")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillRequest request = new FreightBillRequest(
                "FB-STS", "物流甲", null, "客户甲", "项目甲",
                LocalDate.of(2026, 5, 4), new BigDecimal("20.00"),
                StatusConstants.AUDITED, null, List.of(buildItemRequest("OB-001"))
        );

        service.create(request);

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getStatus()).isEqualTo(StatusConstants.AUDITED);
    }

    // ======================== precision / rounding tests ========================

    @Test
    void shouldScaleTotalFreightToTwoDecimals() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-PRC")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                3, "件", new BigDecimal("1.333"), 0, "B001", null, "一号库"
        );

        FreightBillRequest request = new FreightBillRequest(
                "FB-PRC", "物流甲", null, "客户甲", "项目甲",
                LocalDate.of(2026, 5, 4), new BigDecimal("17.50"),
                null, null, List.of(item)
        );

        FreightBillResponse response = service.create(request);

        assertThat(response.totalWeight()).isEqualByComparingTo("3.999");
        assertThat(response.totalFreight()).isEqualByComparingTo("69.98");
    }

    // ======================== update with items sync tests ========================

    @Test
    void shouldSyncItemsOnUpdate() {
        FreightBillRepository repository = mock(FreightBillRepository.class);

        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setBillNo("FB-SYNC");
        existing.setStatus(StatusConstants.UNAUDITED);

        FreightBillItem existingItem = new FreightBillItem();
        existingItem.setId(500L);
        existingItem.setLineNo(1);
        existingItem.setSourceNo("OB-OLD");
        existingItem.setCustomerName("客户甲");
        existingItem.setProjectName("项目甲");
        existingItem.setMaterialCode("M001");
        existingItem.setBrand("宝钢");
        existingItem.setCategory("钢材");
        existingItem.setMaterial("HRB400");
        existingItem.setSpec("18");
        existingItem.setQuantity(2);
        existingItem.setQuantityUnit("件");
        existingItem.setPieceWeightTon(new BigDecimal("1.250"));
        existingItem.setPiecesPerBundle(0);
        existingItem.setWeightTon(new BigDecimal("2.500"));
        existingItem.setFreightBill(existing);
        existing.setItems(new ArrayList<>(List.of(existingItem)));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest newItem = new FreightBillItemRequest(
                500L, "OB-NEW", "客户乙", "项目乙", "M002", "新名称", "沙钢", "钢材", "HRB400", "20", "12m",
                3, "件", new BigDecimal("2.000"), 0, "B002", null, "二号库"
        );

        FreightBillRequest request = new FreightBillRequest(
                "FB-SYNC", "物流甲", null, "客户甲", "项目甲",
                LocalDate.of(2026, 5, 4), new BigDecimal("20.00"),
                null, null, List.of(newItem)
        );

        service.update(1L, request);

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        FreightBill saved = billCaptor.getValue();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getId()).isEqualTo(500L);
        assertThat(saved.getItems().get(0).getSourceNo()).isEqualTo("OB-NEW");
    }

    @Test
    void shouldAddNewItemsOnUpdate() {
        FreightBillRepository repository = mock(FreightBillRepository.class);

        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setBillNo("FB-ADD");
        existing.setStatus(StatusConstants.UNAUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillRequest request = buildRequest("FB-ADD", buildItemRequest("OB-NEW1"), buildItemRequest("OB-NEW2"));

        service.update(1L, request);

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getItems()).hasSize(2);
    }

    // ======================== emptyToNull tests ========================

    @Test
    void shouldConvertWhitespaceBatchNoToNull() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-BN")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "   ", null, "一号库"
        );

        service.create(buildRequest("FB-BN", item));

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getItems().get(0).getBatchNo()).isEqualTo("   ");
    }

    // ======================== coverage for allowAdminViewDeletedRecords ========================

    // ======================== coverage for notFoundMessage ========================

    @Test
    void notFoundMessageShouldReturnChineseMessage() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        FreightBillService service = createService(repository);

        assertThatThrownBy(() -> service.detail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单不存在");
    }

    // ======================== edge case: zero weight ========================

    @Test
    void shouldHandleZeroQuantityGracefully() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-ZERO")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                0, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        FreightBillResponse response = service.create(buildRequest("FB-ZERO", item));

        assertThat(response.totalWeight()).isEqualByComparingTo("0.000");
        assertThat(response.totalFreight()).isEqualByComparingTo("0.00");
    }

    // ======================== edge case: null pieceWeightTon ========================

    @Test
    void shouldHandleNullPieceWeightTon() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-NULL")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillItemRequest item = new FreightBillItemRequest(
                "OB-001", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                5, "件", null, 0, "B001", null, "一号库"
        );

        FreightBillResponse response = service.create(buildRequest("FB-NULL", item));

        assertThat(response.totalWeight()).isEqualByComparingTo("0.000");
    }

    // ======================== validateUpdate same billNo ========================

    @Test
    void shouldNotCheckDuplicateWhenUpdateWithSameBillNo() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBill existing = new FreightBill();
        existing.setId(1L);
        existing.setBillNo("FB-KEEP");
        existing.setStatus(StatusConstants.UNAUDITED);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = createService(repository);

        FreightBillRequest request = buildRequest("FB-KEEP", buildItemRequest("OB-001"));

        service.update(1L, request);

        verify(repository, never()).existsByBillNoAndDeletedFlagFalse("FB-KEEP");
    }

    private CompanySetting companySetting(Long id, String companyName) {
        CompanySetting companySetting = new CompanySetting();
        companySetting.setId(id);
        companySetting.setCompanyName(companyName);
        return companySetting;
    }

    private void setupAdminPrincipal() {
        SecurityPrincipal principal = new SecurityPrincipal(
                1L,
                "admin",
                "",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @SuppressWarnings("unchecked")
    private <T> Root<T> mockRoot() {
        Root<T> root = mock(Root.class, Answers.RETURNS_DEEP_STUBS);
        Map<String, Path<?>> expressions = new java.util.HashMap<>();
        when(root.get(any(String.class))).thenAnswer(invocation ->
                expressions.computeIfAbsent(invocation.getArgument(0), ignored -> mock(Path.class))
        );
        return root;
    }
}
