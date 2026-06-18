package platform

import (
	"errors"
	"reflect"
	"testing"
)

func TestNormalizePreferencesPayloadTrimsAndDeduplicatesKeys(t *testing.T) {
	got := normalizePreferencesPayload(UserAccountPreferencesPayload{
		Pages: map[string]UserListColumnSettingsPayload{
			" users ": {
				OrderedKeys: []string{" loginName ", "userName", "loginName", ""},
				HiddenKeys:  []string{" mobile ", "mobile", " "},
			},
			" ": {
				OrderedKeys: []string{"ignored"},
			},
		},
	})

	want := UserAccountPreferencesPayload{
		Pages: map[string]UserListColumnSettingsPayload{
			"users": {
				OrderedKeys: []string{"loginName", "userName"},
				HiddenKeys:  []string{"mobile"},
			},
		},
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("payload = %#v, want %#v", got, want)
	}
}

func TestNormalizeApiKeyOptions(t *testing.T) {
	resources, err := normalizeApiKeyResources([]string{"Dashboard", "dashboard", " user-account "})
	if err != nil {
		t.Fatalf("normalizeApiKeyResources returned error: %v", err)
	}
	if want := []string{"dashboard", "user-account"}; !reflect.DeepEqual(resources, want) {
		t.Fatalf("resources = %#v, want %#v", resources, want)
	}

	actions, err := normalizeApiKeyActions([]string{"VIEW", "read", " edit "})
	if err != nil {
		t.Fatalf("normalizeApiKeyActions returned error: %v", err)
	}
	if want := []string{"read", "update"}; !reflect.DeepEqual(actions, want) {
		t.Fatalf("actions = %#v, want %#v", actions, want)
	}
}

func TestNormalizeApiKeyOptionsRejectInvalidValues(t *testing.T) {
	if _, err := normalizeApiKeyResources([]string{"unknown-resource"}); !isValidationError(err) {
		t.Fatalf("resource error = %#v, want validation AuthError", err)
	}
	if _, err := normalizeApiKeyActions([]string{"unknown-action"}); !isValidationError(err) {
		t.Fatalf("action error = %#v, want validation AuthError", err)
	}
}

func isValidationError(err error) bool {
	var authErr AuthError
	return errors.As(err, &authErr) && authErr.Kind == AuthErrorValidation
}
