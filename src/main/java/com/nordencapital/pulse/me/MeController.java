package com.nordencapital.pulse.me;

import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nordencapital.pulse.auth.AuthUser;
import com.nordencapital.pulse.auth.SessionLogin;

/**
 * The logged-in user's own profile, login history and password. Every write needs the
 * X-XSRF-TOKEN header.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

	private final MeService meService;

	public MeController(MeService meService) {
		this.meService = meService;
	}

	/** lastLoginAt = this session's login; previousLoginAt = the successful login before it. */
	@GetMapping
	public ProfileResponse profile(@AuthenticationPrincipal AuthUser principal, HttpServletRequest request) {
		return this.meService.profile(principal, SessionLogin.loginAt(request));
	}

	/**
	 * Days with at least one login attempt, oldest first.
	 * @param from first day (YYYY-MM-DD); default today minus 6 months
	 * @param zone IANA zone used to cut days; default UTC
	 */
	@GetMapping("/sign-ins")
	public List<SignInDay> signIns(@AuthenticationPrincipal AuthUser principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) String zone) {
		return this.meService.signIns(principal, zone, from);
	}

	/** Needs the current password. This session stays; the other sessions are logged out. */
	@PostMapping("/password")
	public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser principal,
			@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		this.meService.changePassword(principal, body.currentPassword(), body.newPassword(),
				(session != null) ? session.getId() : null);
		return ResponseEntity.noContent().build();
	}

	public record ChangePasswordRequest(@NotNull String currentPassword, @NotNull String newPassword) {
	}

}
