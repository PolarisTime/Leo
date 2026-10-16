package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemIssuedDoneResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单明细行「已开完」<b>手动标记</b>(人工裁定"该明细行不用再开了")。
 *
 * <p><b>与自动吨位无关</b>: 该标记不是根据已开货吨位算出来的, 也不参与比价的额度扣减;
 * 它只是一个人工结论, 供比价选单默认隐藏已开完的行。恢复本能力时曾存在"不保留任何自动吨位
 * 软提示/超量警告"的定稿, 后来业务改为保留吨位列; 两者并存 —— 吨位列给事实(已开货多少),
 * 本标记给人工作出的最终裁定。</p>
 *
 * <p>语义(幂等):</p>
 * <ul>
 *   <li>标记 {@code true}: 未标记时写入留痕({@code issued_done_by/name/at}); <b>已标记时不覆盖留痕</b>
 *       (谁先标就是谁), 重复调用结果一致;</li>
 *   <li>取消 {@code false}: 状态与留痕一并清空; 未标记时是 no-op;</li>
 *   <li>明细行不存在或所属采购订单已删除 → 404({@link ErrorCode#NOT_FOUND})。</li>
 * </ul>
 *
 * <p>本类只改这一个标记, 不触碰吨位/金额, 也不参与任何自动扣减或告警。</p>
 */
@Service
public class PurchaseOrderItemIssuedDoneService {

    private final PurchaseOrderItemRepository itemRepository;

    public PurchaseOrderItemIssuedDoneService(PurchaseOrderItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /** 设置/取消标记(幂等); 返回标记后的留痕状态。 */
    @Transactional
    public PurchaseOrderItemIssuedDoneResponse mark(Long itemId, boolean issuedDone,
                                                    Long operatorId, String operatorName) {
        PurchaseOrderItem item = requireActiveItem(itemId);
        if (issuedDone) {
            if (!item.isIssuedDone()) {
                item.setIssuedDone(true);
                item.setIssuedDoneBy(operatorId == null ? 0L : operatorId);
                item.setIssuedDoneName(normalizeOperatorName(operatorName));
                item.setIssuedDoneAt(LocalDateTime.now());
            }
        } else if (item.isIssuedDone()) {
            item.setIssuedDone(false);
            item.setIssuedDoneBy(null);
            item.setIssuedDoneName(null);
            item.setIssuedDoneAt(null);
        }
        return toResponse(itemRepository.saveAndFlush(item));
    }

    private PurchaseOrderItem requireActiveItem(Long itemId) {
        if (itemId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购订单明细行不能为空");
        }
        return itemRepository.findActiveByIdIn(List.of(itemId)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购订单明细行不存在"));
    }

    private static String normalizeOperatorName(String operatorName) {
        return operatorName == null || operatorName.isBlank() ? "system" : operatorName;
    }

    private static PurchaseOrderItemIssuedDoneResponse toResponse(PurchaseOrderItem item) {
        return new PurchaseOrderItemIssuedDoneResponse(item.getId(), item.isIssuedDone(),
                item.getIssuedDoneBy(), item.getIssuedDoneName(), item.getIssuedDoneAt());
    }
}
