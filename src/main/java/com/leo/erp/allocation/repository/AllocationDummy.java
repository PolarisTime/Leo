package com.leo.erp.allocation.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JpaRepository 要求的实体占位符，不对应实际业务表。 */
@Entity
@Table(name = "sys_no_rule")
class AllocationDummy {
    @Id
    private Long id;
}
