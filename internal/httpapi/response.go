package httpapi

import (
	"time"

	"github.com/gin-gonic/gin"
)

const dateTimeLayout = "2006-01-02 15:04:05"

type ErrorCode struct {
	Name    string `json:"name,omitempty"`
	Code    int    `json:"code"`
	Message string `json:"message"`
}

var (
	codeSuccess                   = ErrorCode{Name: "SUCCESS", Code: 0, Message: "OK"}
	codeValidationError           = ErrorCode{Name: "VALIDATION_ERROR", Code: 4000, Message: "请求参数不合法"}
	codeUnauthorized              = ErrorCode{Name: "UNAUTHORIZED", Code: 4010, Message: "未登录或登录已失效"}
	codeForbidden                 = ErrorCode{Name: "FORBIDDEN", Code: 4030, Message: "无权访问"}
	codeNotFound                  = ErrorCode{Name: "NOT_FOUND", Code: 4040, Message: "资源不存在"}
	codeBusinessError             = ErrorCode{Name: "BUSINESS_ERROR", Code: 4220, Message: "业务处理失败"}
	codeSessionEvicted            = ErrorCode{Name: "SESSION_EVICTED", Code: 4011, Message: "您的账号已在其他设备登录，当前会话已被登出"}
	codeRefreshTokenReuseConflict = ErrorCode{Name: "REFRESH_TOKEN_REUSE_CONFLICT", Code: 4091, Message: "登录状态正在刷新，请稍后重试"}
	codeRateLimited               = ErrorCode{Name: "RATE_LIMITED", Code: 4290, Message: "请求过于频繁，请稍后再试"}
	codeInternalError             = ErrorCode{Name: "INTERNAL_ERROR", Code: 5000, Message: "系统内部错误"}
)

type apiResponse struct {
	Code      int         `json:"code"`
	Message   string      `json:"message"`
	Data      any         `json:"data,omitempty"`
	Timestamp string      `json:"timestamp"`
	TraceID   string      `json:"traceId,omitempty"`
	RateLimit interface{} `json:"rateLimit,omitempty"`
}

func writeSuccess(c *gin.Context, status int, message string, data any) {
	if message == "" {
		message = codeSuccess.Message
	}
	c.JSON(status, apiResponse{
		Code:      codeSuccess.Code,
		Message:   message,
		Data:      data,
		Timestamp: nowText(),
		TraceID:   traceID(c),
	})
}

func writeFailure(c *gin.Context, status int, code ErrorCode, message string) {
	if message == "" {
		message = code.Message
	}
	c.JSON(status, apiResponse{
		Code:      code.Code,
		Message:   message,
		Timestamp: nowText(),
		TraceID:   traceID(c),
	})
}

func nowText() string {
	return time.Now().Format(dateTimeLayout)
}

func publicErrorCodes() []ErrorCode {
	return []ErrorCode{
		codeValidationError,
		codeUnauthorized,
		codeForbidden,
		codeNotFound,
		codeBusinessError,
		codeSessionEvicted,
		codeRefreshTokenReuseConflict,
		codeRateLimited,
		codeInternalError,
	}
}
