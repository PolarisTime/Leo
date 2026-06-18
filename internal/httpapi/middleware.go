package httpapi

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/leo-erp/leo/internal/platform"
)

const traceIDContextKey = "traceID"
const principalContextKey = "principal"

type AccessTokenAuthenticator interface {
	AuthenticateAccessToken(ctx context.Context, rawToken string) (platform.AuthenticatedPrincipal, error)
}

func traceMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		traceID := strings.TrimSpace(c.GetHeader("X-Trace-Id"))
		if traceID == "" {
			traceID = newTraceID()
		}
		c.Set(traceIDContextKey, traceID)
		c.Header("X-Trace-Id", traceID)
		c.Next()
	}
}

func accessLogMiddleware(logger *slog.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		started := time.Now()
		c.Next()
		logger.Info("request completed",
			"method", c.Request.Method,
			"path", c.Request.URL.Path,
			"status", c.Writer.Status(),
			"duration_ms", time.Since(started).Milliseconds(),
			"trace_id", traceID(c),
		)
	}
}

func securityHeadersMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Content-Security-Policy", "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'")
		c.Header("X-Content-Type-Options", "nosniff")
		c.Header("Referrer-Policy", "no-referrer")
		c.Next()
	}
}

func recoveryMiddleware(logger *slog.Logger) gin.HandlerFunc {
	return gin.CustomRecovery(func(c *gin.Context, recovered any) {
		logger.Error("request panic recovered", "error", recovered, "trace_id", traceID(c))
		writeFailure(c, http.StatusInternalServerError, codeInternalError, "系统异常")
	})
}

func authRequired(authenticator AccessTokenAuthenticator) gin.HandlerFunc {
	return func(c *gin.Context) {
		token := bearerToken(c.GetHeader("Authorization"))
		if token == "" {
			writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
			c.Abort()
			return
		}
		principal, err := authenticator.AuthenticateAccessToken(c.Request.Context(), token)
		if err != nil {
			writeAuthError(c, err)
			c.Abort()
			return
		}
		c.Request = c.Request.WithContext(platform.ContextWithAuthenticatedPrincipal(c.Request.Context(), principal))
		c.Set(principalContextKey, principal)
		c.Next()
	}
}

func permissionRequired(checker PermissionChecker, resource string, action string) gin.HandlerFunc {
	return func(c *gin.Context) {
		principal, ok := currentPrincipal(c)
		if !ok {
			writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
			c.Abort()
			return
		}
		if checker == nil {
			writeFailure(c, http.StatusForbidden, codeForbidden, "无权访问")
			c.Abort()
			return
		}
		if resolver, ok := checker.(PermissionDataScopeResolver); ok {
			scope, err := resolver.DataScope(c.Request.Context(), principal.UserID, resource, action)
			if err != nil {
				writeAuthError(c, err)
				c.Abort()
				return
			}
			c.Request = c.Request.WithContext(platform.ContextWithDataScope(c.Request.Context(), scope))
		} else {
			if err := checker.Require(c.Request.Context(), principal.UserID, resource, action); err != nil {
				writeAuthError(c, err)
				c.Abort()
				return
			}
			c.Request = c.Request.WithContext(platform.ContextWithDataScope(c.Request.Context(), platform.DataScopeAll(principal.UserID, resource, action)))
		}
		c.Next()
	}
}

func currentPrincipal(c *gin.Context) (platform.AuthenticatedPrincipal, bool) {
	value, ok := c.Get(principalContextKey)
	if !ok {
		return platform.AuthenticatedPrincipal{}, false
	}
	principal, ok := value.(platform.AuthenticatedPrincipal)
	return principal, ok
}

func bearerToken(header string) string {
	header = strings.TrimSpace(header)
	if len(header) <= len("Bearer ") || !strings.EqualFold(header[:len("Bearer")], "Bearer") {
		return ""
	}
	return strings.TrimSpace(header[len("Bearer "):])
}

func traceID(c *gin.Context) string {
	if value, ok := c.Get(traceIDContextKey); ok {
		if traceID, ok := value.(string); ok {
			return traceID
		}
	}
	if value := strings.TrimSpace(c.GetHeader("X-Trace-Id")); value != "" {
		return value
	}
	return ""
}

func newTraceID() string {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return hex.EncodeToString([]byte(time.Now().Format(time.RFC3339Nano)))
	}
	return hex.EncodeToString(raw[:])
}
