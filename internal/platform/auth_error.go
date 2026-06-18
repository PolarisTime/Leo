package platform

type AuthErrorKind string

const (
	AuthErrorValidation   AuthErrorKind = "validation"
	AuthErrorUnauthorized AuthErrorKind = "unauthorized"
	AuthErrorForbidden    AuthErrorKind = "forbidden"
	AuthErrorConflict     AuthErrorKind = "conflict"
	AuthErrorNotFound     AuthErrorKind = "not_found"
	AuthErrorBusiness     AuthErrorKind = "business"
	AuthErrorInternal     AuthErrorKind = "internal"
)

type AuthError struct {
	Kind    AuthErrorKind
	Message string
}

func NewAuthError(kind AuthErrorKind, message string) AuthError {
	return AuthError{Kind: kind, Message: message}
}

func (e AuthError) Error() string {
	return e.Message
}
