package platform

import (
	"context"
	"sort"
)

type PermissionEntryService struct{}

func NewPermissionEntryService() PermissionEntryService {
	return PermissionEntryService{}
}

func (PermissionEntryService) Page(_ context.Context, query PageQuery, keyword string) (PageResponse[PermissionEntryResponse], error) {
	rows := permissionEntryRows()
	if keyword != "" {
		filtered := rows[:0]
		for _, row := range rows {
			if containsLower(row.PermissionCode, keyword) ||
				containsLower(row.PermissionName, keyword) ||
				containsLower(row.ModuleName, keyword) ||
				containsLower(row.PermissionType, keyword) ||
				containsLower(row.ActionName, keyword) ||
				containsLower(row.ResourceKey, keyword) {
				filtered = append(filtered, row)
			}
		}
		rows = filtered
	}
	sortPermissionEntries(rows, query)
	total := int64(len(rows))
	start := query.Page * query.Size
	if start > len(rows) {
		start = len(rows)
	}
	end := start + query.Size
	if end > len(rows) {
		end = len(rows)
	}
	return NewPageResponse(rows[start:end], total, query), nil
}

func (PermissionEntryService) Detail(_ context.Context, id int64) (PermissionEntryResponse, error) {
	for _, row := range permissionEntryRows() {
		if row.ID == id {
			return row, nil
		}
	}
	return PermissionEntryResponse{}, NewAuthError(AuthErrorNotFound, "权限不存在")
}

func permissionEntryRows() []PermissionEntryResponse {
	entries := PermissionCatalog()
	rows := make([]PermissionEntryResponse, 0)
	for _, entry := range entries {
		for _, action := range entry.Actions {
			permissionType := "操作权限"
			if action.Code == "read" {
				permissionType = "资源权限"
			}
			rows = append(rows, PermissionEntryResponse{
				ID:             hashPermissionID(entry.Code, action.Code),
				PermissionCode: entry.Code + ":" + action.Code,
				PermissionName: entry.Title + action.Title,
				ModuleName:     entry.Group,
				PermissionType: permissionType,
				ActionName:     action.Title,
				ScopeName:      "全部",
				ResourceKey:    entry.Code,
				Status:         "正常",
				Remark:         "系统资源动作定义",
			})
		}
	}
	return rows
}

func sortPermissionEntries(rows []PermissionEntryResponse, query PageQuery) {
	less := func(i, j int) bool { return rows[i].ID < rows[j].ID }
	switch query.SortBy {
	case "permissionCode":
		less = func(i, j int) bool { return rows[i].PermissionCode < rows[j].PermissionCode }
	case "permissionName":
		less = func(i, j int) bool { return rows[i].PermissionName < rows[j].PermissionName }
	case "moduleName":
		less = func(i, j int) bool { return rows[i].ModuleName < rows[j].ModuleName }
	case "permissionType":
		less = func(i, j int) bool { return rows[i].PermissionType < rows[j].PermissionType }
	case "actionName":
		less = func(i, j int) bool { return rows[i].ActionName < rows[j].ActionName }
	case "status":
		less = func(i, j int) bool { return rows[i].Status < rows[j].Status }
	}
	sort.Slice(rows, func(i, j int) bool {
		if query.Direction == "asc" {
			return less(i, j)
		}
		return less(j, i)
	})
}
