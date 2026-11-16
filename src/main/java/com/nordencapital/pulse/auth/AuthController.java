package com.nordencapital.pulse.auth;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nordencapital.pulse.common.ApiException;
import com.nordencapital.pulse.user.User;

/**
 * Register, email verification, login, logout, current user, forgot and set password.
 * Every POST needs the X-XSRF-TOKEN header (value of the XSRF-TOKEN cookie, from GET
 * /api/auth/csrf). Contract: docs/api/auth.md.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	private final SessionLogin sessionLogin;

	private final CsrfTokenRepository csrfTokenRepository;

	private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
		.getContextHolderStrategy();

	public AuthController(AuthService authService, SessionLogin sessionLogin, CsrfTokenRepository csrfTokenRepository) {
		this.authService = authService;
		this.sessionLogin = sessionLogin;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	/** Sets the XSRF-TOKEN cookie. Call it once before the first POST. */
	@GetMapping("/csrf")
	public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
		// Reading the token makes Spring Security write the XSRF-TOKEN cookie.
		csrfToken.getToken();
		return ResponseEntity.noContent().build();
	}

	/** Always 202, whether the email has an account or not. Does not log in. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
		try {
			this.authService.register(body.email(), body.password(), request.getRemoteAddr());
		}
		catch (DataIntegrityViolationException ex) {
			// Two registers for the same new email at once: the other one won. Same quiet
			// answer as for any existing email.
		}
	}

	/** Always 202. Sends a new link only to a PENDING_VERIFICATION account. */
	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void resendVerification(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
		this.authService.resendVerification(body.email(), request.getRemoteAddr());
	}

	/** Confirms the email with a VERIFY token. Does not log in. */
	@PostMapping("/verify-email")
	public ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest body) {
		this.authService.verifyEmail(body.token());
		return ResponseEntity.noContent().build();
	}

	/** Starts a session (SESSION cookie) and sends a new XSRF-TOKEN cookie. */
	@PostMapping("/login")
	public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User loggedIn = this.authService.login(body.email(), body.password(), request.getRemoteAddr());
		this.sessionLogin.start(loggedIn, request, response);
		return MeResponse.from(loggedIn);
	}

	/** Deletes the session. Works even if not logged in. */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		this.securityContextHolderStrategy.clearContext();
		this.csrfTokenRepository.saveToken(null, request, response);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal AuthUser principal, HttpServletRequest request) {
		return this.authService.current(principal).map(MeResponse::from).orElseThrow(() -> {
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Please log in");
		});
	}

	/** Always 202, whether the email exists or not. */
	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void forgotPassword(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
		this.authService.forgotPassword(body.email(), request.getRemoteAddr());
	}

	/** Checks a VERIFY or RESET token without using it (410 token_invalid). */
	@GetMapping("/token/{token}")
	public AuthService.TokenInfo checkToken(@PathVariable String token) {
		return this.authService.checkToken(token);
	}

	/** Sets a new password with a RESET token. Does not log in. */
	@PostMapping("/set-password")
	public ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest body) {
		this.authService.setPassword(body.token(), body.password());
		return ResponseEntity.noContent().build();
	}

	/** The email is trimmed before validation (like login, which also ignores spaces). */
	public record RegisterRequest(@NotBlank @Size(max = 320) @Email String email, @NotNull String password) {

		public RegisterRequest {
			email = (email != null) ? email.strip() : null;
		}

	}

	public record EmailRequest(@NotBlank @Size(max = 320) String email) {
	}

	public record LoginRequest(@NotBlank @Size(max = 320) String email, @NotBlank String password) {
	}

	public record TokenRequest(@NotBlank String token) {
	}

	public record SetPasswordRequest(@NotBlank String token, @NotNull String password) {
	}

	/** The logged-in user, as login and GET /api/auth/me return it. */
	public record MeResponse(UUID id, String email) {

		public static MeResponse from(User user) {
			return new MeResponse(user.getId(), user.getEmail());
		}

	}

}
