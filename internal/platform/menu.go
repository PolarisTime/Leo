package platform

import (
	"context"
	"sort"

	"github.com/jackc/pgx/v5/pgxpool"
)

type MenuService struct {
	db *pgxpool.Pool
}

type MenuNode struct {
	MenuCode     string     `json:"menuCode"`
	MenuName     string     `json:"menuName"`
	ParentCode   *string    `json:"parentCode"`
	RoutePath    *string    `json:"routePath"`
	Icon         *string    `json:"icon"`
	SortOrder    int        `json:"sortOrder"`
	MenuType     string     `json:"menuType"`
	ResourceCode *string    `json:"resourceCode"`
	Actions      []string   `json:"actions"`
	Children     []MenuNode `json:"children"`
}

type menuRecord struct {
	MenuNode
}

func NewMenuService(db *pgxpool.Pool) MenuService {
	return MenuService{db: db}
}

func (s MenuService) Tree(ctx context.Context, userID int64) ([]MenuNode, error) {
	if s.db == nil {
		return []MenuNode{}, nil
	}
	permissionMap, err := s.userPermissionMap(ctx, userID)
	if err != nil {
		return nil, err
	}
	visibleCodes := visibleMenuCodes(permissionMap)
	if len(visibleCodes) == 0 {
		return []MenuNode{}, nil
	}
	rows, err := s.db.Query(ctx, `
		SELECT menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type
		  FROM sys_menu
		 WHERE deleted_flag = false
		   AND status = '正常'
		 ORDER BY sort_order ASC, id ASC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	allNodes := []MenuNode{}
	records := []MenuNode{}
	for rows.Next() {
		var node MenuNode
		if err := rows.Scan(&node.MenuCode, &node.MenuName, &node.ParentCode, &node.RoutePath, &node.Icon, &node.SortOrder, &node.MenuType); err != nil {
			return nil, err
		}
		allNodes = append(allNodes, node)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	addVisibleParentMenuCodes(visibleCodes, allNodes)
	for _, node := range allNodes {
		if _, ok := visibleCodes[node.MenuCode]; !ok {
			continue
		}
		if resource, ok := resourceForMenuCode(node.MenuCode); ok {
			resourceValue := resource
			node.ResourceCode = &resourceValue
			node.Actions = permissionMap[resource]
		} else {
			node.Actions = []string{}
		}
		node.Children = []MenuNode{}
		records = append(records, node)
	}
	return buildMenuTree(records), nil
}

func (s MenuService) userPermissionMap(ctx context.Context, userID int64) (map[string][]string, error) {
	rows, err := s.db.Query(ctx, `
		SELECT permission.resource_code, permission.action_code
		  FROM sys_user_role user_role
		  JOIN sys_role role
		    ON role.id = user_role.role_id
		   AND role.deleted_flag = false
		   AND role.status = '正常'
		  JOIN sys_role_permission permission
		    ON permission.role_id = role.id
		   AND permission.deleted_flag = false
		 WHERE user_role.user_id = $1
		   AND user_role.deleted_flag = false
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	actionSets := map[string]map[string]struct{}{}
	for rows.Next() {
		var resource string
		var action string
		if err := rows.Scan(&resource, &action); err != nil {
			return nil, err
		}
		resource = normalizeResourceCode(resource)
		action = normalizeActionCode(action)
		if resource == "" || action == "" {
			continue
		}
		if _, ok := actionSets[resource]; !ok {
			actionSets[resource] = map[string]struct{}{}
		}
		actionSets[resource][action] = struct{}{}
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	result := map[string][]string{}
	for resource, actions := range actionSets {
		for action := range actions {
			result[resource] = append(result[resource], action)
		}
		sort.Strings(result[resource])
	}
	return result, nil
}

func visibleMenuCodes(permissionMap map[string][]string) map[string]struct{} {
	result := map[string]struct{}{}
	for resource, actions := range permissionMap {
		hasRead := false
		for _, action := range actions {
			if action == "read" {
				hasRead = true
				break
			}
		}
		if !hasRead {
			continue
		}
		for _, entry := range permissionCatalog {
			if entry.Code != resource {
				continue
			}
			for _, menuCode := range entry.MenuCodes {
				result[menuCode] = struct{}{}
			}
		}
	}
	return result
}

func addVisibleParentMenuCodes(visibleCodes map[string]struct{}, nodes []MenuNode) {
	parentByCode := map[string]string{}
	for _, node := range nodes {
		if node.ParentCode == nil || *node.ParentCode == "" {
			continue
		}
		parentByCode[node.MenuCode] = *node.ParentCode
	}
	for code := range visibleCodes {
		for parent := parentByCode[code]; parent != ""; parent = parentByCode[parent] {
			if _, ok := visibleCodes[parent]; ok {
				continue
			}
			visibleCodes[parent] = struct{}{}
		}
	}
}

func buildMenuTree(records []MenuNode) []MenuNode {
	childrenByParent := map[string][]MenuNode{}
	roots := []MenuNode{}
	for _, node := range records {
		if node.ParentCode == nil || *node.ParentCode == "" {
			roots = append(roots, node)
			continue
		}
		childrenByParent[*node.ParentCode] = append(childrenByParent[*node.ParentCode], node)
	}
	var attach func(node *MenuNode)
	attach = func(node *MenuNode) {
		children := childrenByParent[node.MenuCode]
		node.Children = append(node.Children, children...)
		for i := range node.Children {
			attach(&node.Children[i])
		}
	}
	for i := range roots {
		attach(&roots[i])
	}
	return roots
}
