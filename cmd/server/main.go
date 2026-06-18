package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/leo-erp/leo/internal/config"
	"github.com/leo-erp/leo/internal/httpapi"
	"github.com/leo-erp/leo/internal/platform"
)

func main() {
	cfg := config.Load()
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: cfg.LogLevel}))
	ctx := context.Background()

	db, err := platform.NewPostgresPool(ctx, cfg.Database)
	if err != nil {
		logger.Error("postgres pool initialization failed", "error", err)
		os.Exit(1)
	}
	defer db.Close()

	redisClient := platform.NewRedisClient(cfg.Redis)
	defer func() {
		if err := redisClient.Close(); err != nil {
			logger.Warn("redis close failed", "error", err)
		}
	}()

	healthService := platform.NewHealthService(db, redisClient, logger)
	setupService := platform.NewSetupService(db, logger, cfg.MachineID)
	captchaService := platform.NewCaptchaService(db, redisClient)
	authService := platform.NewAuthService(
		db,
		redisClient,
		cfg.JWT.Secret,
		cfg.JWT.Issuer,
		cfg.JWT.AccessExpiration,
		cfg.JWT.RefreshExpiration,
		cfg.MachineID,
	)
	permissionService := platform.NewPermissionService(db)
	dashboardService := platform.NewDashboardService(db, cfg.AppName)
	departmentService := platform.NewDepartmentService(db, cfg.MachineID)
	operationLogService := platform.NewOperationLogService(db)
	generalSettingService := platform.NewGeneralSettingService(db, cfg.MachineID)
	uploadRuleService := platform.NewUploadRuleService(db, cfg.MachineID)
	securityKeyService := platform.NewSecurityKeyService(db, cfg.JWT.Secret, cfg.MachineID)
	menuService := platform.NewMenuService(db)
	companyService := platform.NewCompanyService(db, cfg.MachineID)
	databaseStatusService := platform.NewDatabaseStatusService(db, redisClient, cfg.Database, cfg.Redis)
	globalSearchService := platform.NewGlobalSearchService(db)
	permissionEntryService := platform.NewPermissionEntryService()
	roleSettingService := platform.NewRoleSettingService(db, cfg.MachineID)
	userAccountService := platform.NewUserAccountAdminService(db, authService, cfg.MachineID)
	refreshTokenService := platform.NewRefreshTokenAdminService(db, redisClient)
	apiKeyService := platform.NewApiKeyAdminService(db, cfg.MachineID)
	printTemplateService := platform.NewPrintTemplateService(db, cfg.MachineID)
	materialCategoryService := platform.NewMaterialCategoryService(db, cfg.MachineID)
	supplierService := platform.NewSupplierService(db, cfg.MachineID)
	customerService := platform.NewCustomerService(db, cfg.MachineID)
	projectService := platform.NewProjectService(db, cfg.MachineID)
	ioReportService := platform.NewIoReportService(db)
	inventoryReportService := platform.NewInventoryReportService(db)
	pendingInvoiceReceiptReportService := platform.NewPendingInvoiceReceiptReportService(db)
	carrierService := platform.NewCarrierService(db, cfg.MachineID)
	warehouseService := platform.NewWarehouseService(db, cfg.MachineID)
	rateLimitService := platform.NewRateLimitAdminService(db)

	server := &http.Server{
		Addr: cfg.Addr(),
		Handler: httpapi.NewRouterWithServices(cfg, logger, healthService, setupService, captchaService, authService, httpapi.RouterServices{
			SetupInitializer:            setupService,
			Permission:                  permissionService,
			Dashboard:                   dashboardService,
			Department:                  departmentService,
			OperationLog:                operationLogService,
			GeneralSetting:              generalSettingService,
			UploadRule:                  uploadRuleService,
			SecurityKey:                 securityKeyService,
			Menu:                        menuService,
			Company:                     companyService,
			Database:                    databaseStatusService,
			GlobalSearch:                globalSearchService,
			PermissionEntry:             permissionEntryService,
			RoleSetting:                 roleSettingService,
			UserAccount:                 userAccountService,
			RefreshToken:                refreshTokenService,
			ApiKey:                      apiKeyService,
			PrintTemplate:               printTemplateService,
			MaterialCategory:            materialCategoryService,
			Supplier:                    supplierService,
			Customer:                    customerService,
			Project:                     projectService,
			IoReport:                    ioReportService,
			InventoryReport:             inventoryReportService,
			PendingInvoiceReceiptReport: pendingInvoiceReceiptReportService,
			Carrier:                     carrierService,
			Warehouse:                   warehouseService,
			RateLimit:                   rateLimitService,
		}),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	errc := make(chan error, 1)
	go func() {
		logger.Info("leo go backend listening", "addr", server.Addr)
		errc <- server.ListenAndServe()
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)

	select {
	case sig := <-stop:
		logger.Info("shutdown requested", "signal", sig.String())
	case err := <-errc:
		if !errors.Is(err, http.ErrServerClosed) {
			logger.Error("server stopped", "error", err)
			os.Exit(1)
		}
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := server.Shutdown(ctx); err != nil {
		logger.Error("graceful shutdown failed", "error", err)
		os.Exit(1)
	}
}
