package com.nordencapital.pulse.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

import com.nordencapital.pulse.user.User;

/**
 * Starts the logged-in session, the same way for password and passkey login: new
 * session id (stops session fixation), user saved in the session, new CSRF token.
 */
@Component
public class SessionLogin {

	/** Session attribute: id of the passkey used to log in (missing after a password login). */
	public static final String PASSKEY_ATTRIBUTE = "pulse.loginPasskeyId";

	/** Session attribute: when this session's login happened (ISO-8601 text). */
	public static final String LOGIN_AT_ATTRIBUTE = "pulse.loginAt";

	/** Every user has the same rights; there are no roles yet. */
	private static final List<SimpleGrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

	private final SecurityContextRepository securityContextRepository;

	private final CsrfTokenRepository csrfTokenRepository;

	private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
		.getContextHolderStrategy();

	public SessionLogin(SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) {
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	public void start(User loggedIn, HttpServletRequest request, HttpServletResponse response) {
		start(loggedIn, null, request, response);
	}

	/**
	 * @param passkeyId the passkey used to log in, or null (password login)
	 */
	public void start(User loggedIn, UUID passkeyId, HttpServletRequest request, HttpServletResponse response) {
		AuthUser user = new AuthUser(loggedIn.getId(), loggedIn.getEmail());
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = this.securityContextHolderStrategy.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, AUTHORITIES));
		this.securityContextHolderStrategy.setContext(context);
		this.securityContextRepository.saveContext(context, request, response);
		this.csrfTokenRepository.saveToken(this.csrfTokenRepository.generateToken(request), request, response);
		if (loggedIn.getLastLoginAt() != null) {
			request.getSession().setAttribute(LOGIN_AT_ATTRIBUTE, loggedIn.getLastLoginAt().toString());
		}
		if (passkeyId != null) {
			request.getSession().setAttribute(PASSKEY_ATTRIBUTE, passkeyId.toString());
		}
		else {
			request.getSession().removeAttribute(PASSKEY_ATTRIBUTE);
		}
	}

	/** When this session's login happened, or null. */
	public static Instant loginAt(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		Object value = (session != null) ? session.getAttribute(LOGIN_AT_ATTRIBUTE) : null;
		return (value instanceof String at) ? Instant.parse(at) : null;
	}

	/** The passkey used to log in this session, or null. */
	public static UUID loginPasskey(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		Object value = (session != null) ? session.getAttribute(PASSKEY_ATTRIBUTE) : null;
		return (value instanceof String id) ? UUID.fromString(id) : null;
	}

}
