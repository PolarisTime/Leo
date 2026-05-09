package com.leo.erp.common.search;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.contract.purchase.service.PurchaseContractService;
import com.leo.erp.contract.sales.service.SalesContractService;
import com.leo.erp.finance.invoiceissue.service.InvoiceIssueService;
import com.leo.erp.finance.invoicereceipt.service.InvoiceReceiptService;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.finance.receipt.service.ReceiptService;
import com.leo.erp.logistics.bill.service.FreightBillService;
import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.outbound.service.SalesOutboundService;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.statement.customer.service.CustomerStatementService;
import com.leo.erp.statement.freight.service.FreightStatementService;
import com.leo.erp.statement.supplier.service.SupplierStatementService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalSearchService {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchService.class);

    private static final int MAX_TOTAL_LIMIT = 50;
    private static final int MAX_PER_MODULE_LIMIT = 6;
    private static final String[] SUMMARY_FIELDS = {
            "customerName",
            "supplierName",
            "projectName",
            "carrierName",
            "counterpartyName",
            "warehouseName",
            "salesName",
            "buyerName",
            "status",
            "businessType"
    };

    private final PermissionService permissionService;
    private final ModulePermissionGuard modulePermissionGuard;
    private final List<ModuleSearcher<?>> moduleSearchers;

    public GlobalSearchService(PermissionService permissionService,
                               ModulePermissionGuard modulePermissionGuard,
                               PurchaseOrderService purchaseOrderService,
                               PurchaseInboundService purchaseInboundService,
                               SalesOrderService salesOrderService,
                               SalesOutboundService salesOutboundService,
                               FreightBillService freightBillService,
                               PurchaseContractService purchaseContractService,
                               SalesContractService salesContractService,
                               SupplierStatementService supplierStatementService,
                               CustomerStatementService customerStatementService,
                               FreightStatementService freightStatementService,
                               ReceiptService receiptService,
                               PaymentService paymentService,
                               InvoiceReceiptService invoiceReceiptService,
                               InvoiceIssueService invoiceIssueService) {
        this.permissionService = permissionService;
        this.modulePermissionGuard = modulePermissionGuard;
        this.moduleSearchers = List.of(
                module("purchase-order", "orderNo", purchaseOrderService::search, purchaseOrderService::detail),
                module("purchase-inbound", "inboundNo", purchaseInboundService::search, purchaseInboundService::detail),
                module("sales-order", "orderNo", salesOrderService::search, salesOrderService::detail),
                module("sales-outbound", "outboundNo", salesOutboundService::search, salesOutboundService::detail),
                module("freight-bill", "billNo", freightBillService::search, freightBillService::detail),
                module("purchase-contract", "contractNo", purchaseContractService::search, purchaseContractService::detail),
                module("sales-contract", "contractNo", salesContractService::search, salesContractService::detail),
                module("supplier-statement", "statementNo", supplierStatementService::search, supplierStatementService::detail),
                module("customer-statement", "statementNo", customerStatementService::search, customerStatementService::detail),
                module("freight-statement", "statementNo", freightStatementService::search, freightStatementService::detail),
                module("receipt", "receiptNo", receiptService::search, receiptService::detail),
                module("payment", "paymentNo", paymentService::search, paymentService::detail),
                module("invoice-receipt", "receiveNo", invoiceReceiptService::search, invoiceReceiptService::detail),
                module("invoice-issue", "issueNo", invoiceIssueService::search, invoiceIssueService::detail)
        );
    }

    @Transactional(readOnly = true)
    public List<GlobalSearchResponse> search(String keyword, int limit) {
        return search(keyword, limit, null);
    }

    @Transactional(readOnly = true)
    public List<GlobalSearchResponse> search(String keyword, int limit, List<String> moduleKeys) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        SecurityPrincipal principal = currentPrincipal();
        boolean trackIdSearch = isLikelyTrackId(normalizedKeyword);
        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_TOTAL_LIMIT);
        int perModuleLimit = Math.min(normalizedLimit, MAX_PER_MODULE_LIMIT);
        Set<String> requestedModuleKeys = normalizeModuleKeys(moduleKeys);
        List<GlobalSearchResponse> results = new ArrayList<>();

        for (ModuleSearcher<?> moduleSearcher : resolveModuleSearchers(requestedModuleKeys)) {
            results.addAll(searchModule(moduleSearcher, principal, normalizedKeyword, trackIdSearch, perModuleLimit));
            if (trackIdSearch && !results.isEmpty()) {
                break;
            }
        }

        return results.stream()
                .sorted((left, right) -> {
                    if (left.matchedByTrackId() != right.matchedByTrackId()) {
                        return left.matchedByTrackId() ? -1 : 1;
                    }
                    return left.primaryNo().compareToIgnoreCase(right.primaryNo());
                })
                .limit(normalizedLimit)
                .toList();
    }

    private List<ModuleSearcher<?>> resolveModuleSearchers(Set<String> requestedModuleKeys) {
        if (requestedModuleKeys == null || requestedModuleKeys.isEmpty()) {
            return moduleSearchers;
        }
        return moduleSearchers.stream()
                .filter(moduleSearcher -> requestedModuleKeys.contains(moduleSearcher.moduleKey()))
                .toList();
    }

    private Set<String> normalizeModuleKeys(List<String> moduleKeys) {
        if (moduleKeys == null || moduleKeys.isEmpty()) {
            return Set.of();
        }
        return moduleKeys.stream()
                .filter(Objects::nonNull)
                .flatMap(item -> List.of(item.split(",")).stream())
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return principal;
    }

    private <T> List<GlobalSearchResponse> searchModule(ModuleSearcher<T> moduleSearcher,
                                                        SecurityPrincipal principal,
                                                        String keyword,
                                                        boolean trackIdSearch,
                                                        int perModuleLimit) {
        ModulePermissionGuard.PermissionCheck permissionCheck;
        try {
            permissionCheck = modulePermissionGuard.requireResourcePermission(
                    principal,
                    moduleSearcher.moduleKey(),
                    ResourcePermissionCatalog.READ
            );
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.FORBIDDEN) {
                return List.of();
            }
            throw ex;
        }

        DataScopeContext.Context previous = DataScopeContext.current();
        String dataScope = ResourcePermissionCatalog.isBusinessResource(permissionCheck.resource())
                ? permissionService.getUserDataScope(principal.id(), permissionCheck.resource(), permissionCheck.action())
                : ResourcePermissionCatalog.SCOPE_ALL;
        DataScopeContext.set(
                principal.id(),
                permissionCheck.resource(),
                dataScope,
                permissionService.getDataScopeOwnerUserIds(principal.id(), dataScope)
        );
        try {
            List<T> records = trackIdSearch
                    ? lookupByTrackId(moduleSearcher, keyword)
                    : safeSearch(moduleSearcher, keyword, perModuleLimit);
            return records.stream()
                    .map(record -> toResponse(moduleSearcher, record, keyword))
                    .filter(Objects::nonNull)
                    .toList();
        } finally {
            restore(previous);
        }
    }

    private <T> List<T> safeSearch(ModuleSearcher<T> moduleSearcher, String keyword, int perModuleLimit) {
        try {
            List<T> rows = moduleSearcher.searchInvoker().search(keyword, perModuleLimit);
            return rows == null ? List.of() : rows;
        } catch (RuntimeException ex) {
            log.warn("Global search skipped module {} due to search failure", moduleSearcher.moduleKey(), ex);
            return List.of();
        }
    }

    private <T> List<T> lookupByTrackId(ModuleSearcher<T> moduleSearcher, String keyword) {
        Long id;
        try {
            id = Long.parseLong(keyword);
        } catch (NumberFormatException ex) {
            return List.of();
        }

        try {
            T record = moduleSearcher.detailInvoker().detail(id);
            return record == null ? List.of() : List.of(record);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.NOT_FOUND || ex.getErrorCode() == ErrorCode.FORBIDDEN) {
                return List.of();
            }
            log.warn("Global search skipped module {} due to detail lookup failure", moduleSearcher.moduleKey(), ex);
            return List.of();
        } catch (RuntimeException ex) {
            log.warn("Global search skipped module {} due to detail lookup failure", moduleSearcher.moduleKey(), ex);
            return List.of();
        }
    }

    private <T> GlobalSearchResponse toResponse(ModuleSearcher<T> moduleSearcher, T record, String keyword) {
        String trackId = readString(record, "id");
        String primaryNo = Optional.ofNullable(readString(record, moduleSearcher.primaryNoField()))
                .filter(value -> !value.isBlank())
                .orElse(trackId);
        if (trackId.isBlank() || primaryNo.isBlank()) {
            return null;
        }
        return new GlobalSearchResponse(
                moduleSearcher.moduleKey(),
                moduleTitle(moduleSearcher.moduleKey()),
                trackId,
                primaryNo,
                buildSummary(record),
                trackId.equals(keyword)
        );
    }

    private String moduleTitle(String moduleKey) {
        return ResourcePermissionCatalog.resolveResourceByMenuCode(moduleKey)
                .map(ResourcePermissionCatalog::resourceTitle)
                .orElse(moduleKey);
    }

    private String buildSummary(Object record) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String field : SUMMARY_FIELDS) {
            String value = readString(record, field);
            if (!value.isBlank()) {
                values.add(value);
            }
            if (values.size() >= 3) {
                break;
            }
        }
        return String.join(" / ", values);
    }

    private String readString(Object target, String property) {
        if (target == null || property == null || property.isBlank()) {
            return "";
        }
        Object value = readProperty(target, property);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Object readProperty(Object target, String property) {
        try {
            Method method = target.getClass().getMethod(property);
            return method.invoke(target);
        } catch (NoSuchMethodException ignored) {
            return invokeGetter(target, property);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    private Object invokeGetter(Object target, String property) {
        if (property.isBlank()) {
            return null;
        }
        String getterName = "get" + property.substring(0, 1).toUpperCase(Locale.ROOT) + property.substring(1);
        try {
            Method method = target.getClass().getMethod(getterName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    private boolean isLikelyTrackId(String keyword) {
        return keyword != null && keyword.matches("^\\d{12,}$");
    }

    private void restore(DataScopeContext.Context previous) {
        if (previous == null) {
            DataScopeContext.clear();
            return;
        }
        DataScopeContext.set(previous.userId(), previous.resource(), previous.scope(), previous.ownerUserIds());
    }

    private static <T> ModuleSearcher<T> module(String moduleKey,
                                                String primaryNoField,
                                                SearchInvoker<T> searchInvoker,
                                                DetailInvoker<T> detailInvoker) {
        return new ModuleSearcher<>(moduleKey, primaryNoField, searchInvoker, detailInvoker);
    }

    @FunctionalInterface
    private interface SearchInvoker<T> {
        List<T> search(String keyword, int maxSize);
    }

    @FunctionalInterface
    private interface DetailInvoker<T> {
        T detail(Long id);
    }

    private record ModuleSearcher<T>(
            String moduleKey,
            String primaryNoField,
            SearchInvoker<T> searchInvoker,
            DetailInvoker<T> detailInvoker
    ) {
    }
}
