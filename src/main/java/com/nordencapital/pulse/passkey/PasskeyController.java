package com.nordencapital.pulse.passkey;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nordencapital.pulse.auth.AuthController.MeResponse;
import com.nordencapital.pulse.auth.AuthUser;
import com.nordencapital.pulse.auth.SessionLogin;
import com.nordencapital.pulse.common.Names;

/**
 * Log in with Face ID / Touch ID / fingerprint (WebAuthn). Options are WebAuthn JSON
 * (binary values in base64url): give them to
 * PublicKeyCredential.parseCreationOptionsFromJSON / parseRequestOptionsFromJSON (or
 * {@code @simplewebauthn/browser}), and send back credential.toJSON(). Every POST needs
 * the X-XSRF-TOKEN header.
 */
@RestController
public class PasskeyController {

	private static final int MAX_NAME_LENGTH = 60;

	private final PasskeyService service;

	private final SessionLogin sessionLogin;

	public PasskeyController(PasskeyService service, SessionLogin sessionLogin) {
		this.service = service;
		this.sessionLogin = sessionLogin;
	}

	/** Returns PublicKeyCredentialCreationOptionsJSON. Valid 5 minutes, once. */
	@PostMapping(path = "/api/me/passkeys/register/options", produces = MediaType.APPLICATION_JSON_VALUE)
	public String registrationOptions(@AuthenticationPrincipal AuthUser actor, HttpServletRequest request) {
		return this.service.registrationOptions(actor, request.getSession());
	}

	/** credential = RegistrationResponseJSON. Name: max 60, default "Passkey". */
	@PostMapping("/api/me/passkeys/register")
	public ResponseEntity<PasskeyResponse> register(@AuthenticationPrincipal AuthUser actor,
			@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
		PasskeyResponse created = this.service.register(actor, request.getSession(), body.name(),
				body.credential().toString(), SessionLogin.loginPasskey(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	/** current = true only for the passkey used to log in this session. */
	@GetMapping("/api/me/passkeys")
	public List<PasskeyResponse> list(@AuthenticationPrincipal AuthUser actor, HttpServletRequest request) {
		return this.service.list(actor, SessionLogin.loginPasskey(request));
	}

	@PatchMapping("/api/me/passkeys/{id}")
	public PasskeyResponse rename(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
			@Valid @RequestBody RenameRequest body, HttpServletRequest request) {
		return this.service.rename(actor, id, body.name(), SessionLogin.loginPasskey(request));
	}

	@DeleteMapping("/api/me/passkeys/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
		this.service.delete(actor, id);
		return ResponseEntity.noContent().build();
	}

	/** Public. Returns PublicKeyCredentialRequestOptionsJSON (no email needed). Valid 5 minutes, once. */
	@PostMapping(path = "/api/auth/passkey/options", produces = MediaType.APPLICATION_JSON_VALUE)
	public String loginOptions(HttpServletRequest request) {
		return this.service.loginOptions(request.getSession());
	}

	/** credential = AuthenticationResponseJSON. Starts the same session as password login. */
	@PostMapping("/api/auth/passkey/login")
	public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		PasskeyService.LoggedIn loggedIn = this.service.login(request.getSession(), body.credential().toString(),
				request.getRemoteAddr());
		this.sessionLogin.start(loggedIn.user(), loggedIn.passkeyId(), request, response);
		return MeResponse.from(loggedIn.user());
	}

	/** The name is trimmed before validation; empty = "Passkey". */
	public record RegisterRequest(@Size(max = MAX_NAME_LENGTH) @Pattern(regexp = Names.NO_CONTROL_CHARS,
			message = "must not contain control characters") String name, @NotNull JsonNode credential) {

		public RegisterRequest {
			name = (name == null || name.isBlank()) ? null : name.strip();
		}

	}

	/** The name is trimmed before validation. */
	public record RenameRequest(@NotBlank @Size(max = MAX_NAME_LENGTH) @Pattern(regexp = Names.NO_CONTROL_CHARS,
			message = "must not contain control characters") String name) {

		public RenameRequest {
			name = Names.clean(name);
		}

	}

	public record LoginRequest(@NotNull JsonNode credential) {
	}

}
