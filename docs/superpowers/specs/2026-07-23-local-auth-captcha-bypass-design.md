# Local Auth Captcha Bypass Design

## Goal

Allow local development to log in and request email verification codes without a graphical captcha, while production keeps captcha enforcement enabled. Large-download captcha protection is outside this change.

## Configuration

- `application.yaml` defines `app.login.captcha-required: true` as the secure default.
- `application-local.yaml` overrides it to `false`.
- `application-prod.yaml` explicitly sets it to `true`, so production remains protected even if the default changes later.

## Backend behavior

`AuthService` reads the flag and treats graphical captcha validation as a no-op only when it is disabled. Password login and sending email verification codes continue to call the same validation method, so all existing production paths retain their enforcement through one decision point.

Captcha request DTO fields become optional at bean-validation time. When captcha is required, `AuthService` remains responsible for rejecting missing, expired, or incorrect values. This is necessary because DTO-level `@NotBlank` validation cannot vary by Spring profile.

`GET /api/auth/captcha` includes a `captchaRequired` signal. When captcha is disabled it returns the signal without creating a captcha image; when enabled it returns the existing key and image plus the signal.

## Frontend behavior

The login and registration pages consume `captchaRequired` from the existing captcha endpoint. If false, they hide graphical-captcha inputs, skip their client-side required checks, and submit email-code requests without captcha values. If true or unknown, they keep the existing UI and validation, favoring the secure path during a transient API failure.

## Scope boundaries

- Do not alter the email verification-code requirement for registration or email login.
- Do not alter login lockout, email send cooldown, rate limits, or password validation.
- Do not alter download captcha behavior or `DownloadVerificationService`.

## Testing

- Add a backend regression test proving absent captcha values succeed only with `captcha-required=false` and fail when enabled.
- Add frontend source-level tests proving the pages conditionally render and validate captcha controls from the server signal.
- Run the focused backend test, the frontend test suite, and the frontend production build.
