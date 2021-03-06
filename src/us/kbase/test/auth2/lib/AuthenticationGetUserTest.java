package us.kbase.test.auth2.lib;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static us.kbase.test.auth2.lib.AuthenticationTester.initTestMocks;

import java.time.Instant;
import java.util.UUID;

import org.junit.Test;

import us.kbase.auth2.lib.Authentication;
import us.kbase.auth2.lib.DisplayName;
import us.kbase.auth2.lib.EmailAddress;
import us.kbase.auth2.lib.Role;
import us.kbase.auth2.lib.UserDisabledState;
import us.kbase.auth2.lib.UserName;
import us.kbase.auth2.lib.ViewableUser;
import us.kbase.auth2.lib.exceptions.DisabledUserException;
import us.kbase.auth2.lib.exceptions.ErrorType;
import us.kbase.auth2.lib.exceptions.InvalidTokenException;
import us.kbase.auth2.lib.exceptions.NoSuchTokenException;
import us.kbase.auth2.lib.exceptions.NoSuchUserException;
import us.kbase.auth2.lib.exceptions.UnauthorizedException;
import us.kbase.auth2.lib.storage.AuthStorage;
import us.kbase.auth2.lib.token.IncomingToken;
import us.kbase.auth2.lib.token.StoredToken;
import us.kbase.auth2.lib.token.TokenType;
import us.kbase.auth2.lib.user.AuthUser;
import us.kbase.test.auth2.TestCommon;
import us.kbase.test.auth2.lib.AuthenticationTester.TestMocks;

public class AuthenticationGetUserTest {
	
	@Test
	public void getUser() throws Exception {
		final AuthUser user = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("foo"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN).build();
		
		getUser(user);
	}
	
	@Test
	public void getUserFailDisabled() throws Exception {
		final AuthUser user = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("foo"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN)
				.withUserDisabledState(
						new UserDisabledState("foo", new UserName("bar"), Instant.now())).build();
		
		failGetUser(user, new DisabledUserException());
	}
	
	@Test
	public void getUserFailNull() throws Exception {
		final Authentication auth = initTestMocks().auth;
		failGetUser(auth, null, new NullPointerException("token"));
	}
	
	@Test
	public void getUserFailBadToken() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");

		when(storage.getToken(token.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failGetUser(auth, token, new InvalidTokenException());
	}
	
	@Test
	public void getUserFailCatastrophic() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");

		when(storage.getToken(token.getHashedToken())).thenReturn(
				StoredToken.getBuilder(TokenType.LOGIN, UUID.randomUUID(), new UserName("foo"))
						.withLifeTime(Instant.now(), Instant.now()).build());
		
		when(storage.getUser(new UserName("foo"))).thenThrow(new NoSuchUserException("foo"));
		
		failGetUser(auth, token, new RuntimeException("There seems to be an error " +
				"in the storage system. Token was valid, but no user"));
	}

	private void getUser(final AuthUser user) throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");

		when(storage.getToken(token.getHashedToken())).thenReturn(
				StoredToken.getBuilder(TokenType.LOGIN, UUID.randomUUID(), user.getUserName())
						.withLifeTime(Instant.now(), Instant.now()).build());
		
		when(storage.getUser(user.getUserName())).thenReturn(user);
		
		try {
			final AuthUser got = auth.getUser(token);
		
			assertThat("incorrect user", got, is(user));
		} catch (Throwable th) {
			if (user.isDisabled()) {
				verify(storage).deleteTokens(user.getUserName());
			} else {
				verify(storage, never()).deleteTokens(user.getUserName());
			}
			throw th;
		}
	}
	
	private void failGetUser(final AuthUser user, final Exception e) {
		try {
			getUser(user);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failGetUser(
			final Authentication auth,
			final IncomingToken token,
			final Exception e)
			throws Exception {
		try {
			auth.getUser(token);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void getOtherUserSameUser() throws Exception {
		final AuthUser user = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("foo"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN).build();
		
		getOtherUser(user, new UserName("admin"), true);
	}
	
	@Test
	public void getOtherUserDiffUser() throws Exception {
		final AuthUser user = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("foo1"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN).build();
		
		getOtherUser(user, new UserName("foo"), false);
	}
	
	@Test
	public void getOtherUserFailDisabledSameUser() throws Exception {
		final AuthUser user = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("foo1"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN)
				.withUserDisabledState(
						new UserDisabledState("foo", new UserName("baz"), Instant.now())).build();
		
		failGetOtherUser(user, new UserName("admin"), new NoSuchUserException("admin"));
	}
	
	@Test
	public void getOtherUserFailDisabledDiffUser() throws Exception {
		final AuthUser user = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("foo1"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN)
				.withUserDisabledState(
						new UserDisabledState("foo", new UserName("baz"), Instant.now())).build();
		
		failGetOtherUser(user, new UserName("foo"), new NoSuchUserException("admin"));
	}
	
	@Test
	public void getOtherUserFailNulls() throws Exception {
		final Authentication auth = initTestMocks().auth;
		
		failGetOtherUser(auth, null, new UserName("foo"), new NullPointerException("token"));
		failGetOtherUser(auth, new IncomingToken("foo"), null,
				new NullPointerException("userName"));
	}
	
	@Test
	public void getOtherUserFailBadToken() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");
		

		when(storage.getToken(token.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failGetOtherUser(auth, token, new UserName("foo"), new InvalidTokenException());
	}
	
	@Test
	public void getOtherUserFailNoSuchUser() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");
		

		when(storage.getToken(token.getHashedToken())).thenReturn(
				StoredToken.getBuilder(TokenType.LOGIN, UUID.randomUUID(), new UserName("bar"))
						.withLifeTime(Instant.now(), Instant.now()).build());
		
		when(storage.getUser(new UserName("bar"))).thenThrow(new NoSuchUserException("bar"));
		
		failGetOtherUser(auth, token, new UserName("bar"), new NoSuchUserException("bar"));
	}

	private void getOtherUser(
			final AuthUser user,
			final UserName tokenName,
			final boolean includeEmail)
			throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");
		

		when(storage.getToken(token.getHashedToken())).thenReturn(
				StoredToken.getBuilder(TokenType.LOGIN, UUID.randomUUID(), tokenName)
						.withLifeTime(Instant.now(), Instant.now()).build());
		
		when(storage.getUser(user.getUserName())).thenReturn(user);
		try {
			final ViewableUser vu = auth.getUser(token, user.getUserName());
		
			assertThat("incorrect user", vu, is(new ViewableUser(user, includeEmail)));
		} catch (Throwable th) {
			if (user.isDisabled() && tokenName.equals(user.getUserName())) {
				verify(storage).deleteTokens(user.getUserName());
			} else {
				verify(storage, never()).deleteTokens(user.getUserName());
			}
			throw th;
		}
	}
	
	private void failGetOtherUser(
			final AuthUser user,
			final UserName tokenName,
			final Exception e) {
		try {
			getOtherUser(user, tokenName, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failGetOtherUser(
			final Authentication auth,
			final IncomingToken token,
			final UserName user,
			final Exception e) {
		try {
			auth.getUser(token, user);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void getUserAsAdmin() throws Exception {
		final AuthUser admin = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("bar"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN).build();
		
		final AuthUser user = AuthUser.getBuilder(
				new UserName("foo"), new DisplayName("baz"), Instant.now())
				.withEmailAddress(new EmailAddress("f@goo.com"))
				.build();
		
		getUserAsAdmin(admin, user);
	}
	
	@Test
	public void getUserAsAdminSelf() throws Exception {
		final AuthUser admin = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("bar"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN).build();
		
		final AuthUser user = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("bar"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN).build();
		
		getUserAsAdmin(admin, user);
	}
	
	
	@Test
	public void getUserAsAdminCreate() throws Exception {
		final AuthUser admin = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("bar"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.CREATE_ADMIN).build();
		
		final AuthUser user = AuthUser.getBuilder(
				new UserName("foo"), new DisplayName("baz"), Instant.now())
				.withEmailAddress(new EmailAddress("f@goo.com"))
				.build();
		
		getUserAsAdmin(admin, user);
	}
	
	@Test
	public void getUserAsAdminRoot() throws Exception {
		final AuthUser admin = AuthUser.getBuilder(
				UserName.ROOT, new DisplayName("bar"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.build();
		
		final AuthUser user = AuthUser.getBuilder(
				new UserName("foo"), new DisplayName("baz"), Instant.now())
				.withEmailAddress(new EmailAddress("f@goo.com"))
				.build();
		
		getUserAsAdmin(admin, user);
	}
	
	@Test
	public void getUserAsAdminFailNotAdmin() throws Exception {
		final AuthUser admin = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("bar"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.SERV_TOKEN).build();
		
		final AuthUser user = AuthUser.getBuilder(
				new UserName("foo"), new DisplayName("baz"), Instant.now())
				.withEmailAddress(new EmailAddress("f@goo.com"))
				.build();
		
		failGetUserAsAdmin(admin, user, new UnauthorizedException(ErrorType.UNAUTHORIZED));
	}
	
	@Test
	public void getUserAsAdminFailDisabled() throws Exception {
		final AuthUser admin = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("bar"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN)
				.withUserDisabledState(
						new UserDisabledState("baz", new UserName("whee"), Instant.now())).build();
		
		final AuthUser user = AuthUser.getBuilder(
				new UserName("foo"), new DisplayName("baz"), Instant.now())
				.withEmailAddress(new EmailAddress("f@goo.com"))
				.build();
		
		failGetUserAsAdmin(admin, user, new DisabledUserException());
	}
	
	@Test
	public void getUserAsAdminFailNulls() throws Exception {
		final Authentication auth = initTestMocks().auth;
		
		failGetUserAsAdmin(auth, null, new UserName("foo"), new NullPointerException("token"));
		failGetUserAsAdmin(auth, new IncomingToken("foo"), null,
				new NullPointerException("userName"));
	}
	
	@Test
	public void getUserAsAdminFailBadToken() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobarbaz");
		
		when(storage.getToken(t.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failGetUserAsAdmin(auth, t, new UserName("bar"), new InvalidTokenException());
	}
	
	@Test
	public void getUserAsAdminFailBadTokenType() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");
		
		when(storage.getToken(token.getHashedToken())).thenReturn(
				StoredToken.getBuilder(TokenType.AGENT, UUID.randomUUID(), new UserName("bar"))
						.withLifeTime(Instant.now(), Instant.now()).build(),
				StoredToken.getBuilder(TokenType.DEV, UUID.randomUUID(), new UserName("bar"))
						.withLifeTime(Instant.now(), Instant.now()).build(),
				StoredToken.getBuilder(TokenType.SERV, UUID.randomUUID(), new UserName("bar"))
						.withLifeTime(Instant.now(), Instant.now()).build(),
				null);
		
		failGetUserAsAdmin(auth, token, new UserName("bar"), new UnauthorizedException(
				ErrorType.UNAUTHORIZED, "Agent tokens are not allowed for this operation"));
		failGetUserAsAdmin(auth, token, new UserName("bar"), new UnauthorizedException(
				ErrorType.UNAUTHORIZED, "Developer tokens are not allowed for this operation"));
		failGetUserAsAdmin(auth, token, new UserName("bar"), new UnauthorizedException(
				ErrorType.UNAUTHORIZED, "Service tokens are not allowed for this operation"));
	}
	
	@Test
	public void getUserAsAdminFailCatastrophic() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobarbaz");
		final StoredToken token = StoredToken.getBuilder(
				TokenType.LOGIN, UUID.randomUUID(), new UserName("foobar"))
				.withLifeTime(Instant.now(), Instant.now()).build();
		
		when(storage.getToken(t.getHashedToken())).thenReturn(token, (StoredToken) null);
		
		when(storage.getUser(new UserName("foobar"))).thenThrow(new NoSuchUserException("foobar"));
		
		failGetUserAsAdmin(auth, t, new UserName("bleah"), new RuntimeException(
				"There seems to be an error " +
				"in the storage system. Token was valid, but no user"));
	}
	
	@Test
	public void getUserAsAdminFailNoSuchUser() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobarbaz");
		final StoredToken token = StoredToken.getBuilder(
				TokenType.LOGIN, UUID.randomUUID(), new UserName("admin"))
				.withLifeTime(Instant.now(), Instant.now()).build();
		
		final AuthUser admin = AuthUser.getBuilder(
				new UserName("admin"), new DisplayName("bar"), Instant.now())
				.withEmailAddress(new EmailAddress("f@g.com"))
				.withRole(Role.ADMIN).build();
		
		when(storage.getToken(t.getHashedToken())).thenReturn(token, (StoredToken) null);
		
		when(storage.getUser(new UserName("admin"))).thenReturn(admin, (AuthUser) null);
		when(storage.getUser(new UserName("bar"))).thenThrow(new NoSuchUserException("bar"));
		
		failGetUserAsAdmin(auth, t, new UserName("bar"), new NoSuchUserException("bar"));
	}

	private void getUserAsAdmin(final AuthUser admin, final AuthUser user) throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken t = new IncomingToken("foobarbaz");
		final StoredToken token = StoredToken.getBuilder(
				TokenType.LOGIN, UUID.randomUUID(), admin.getUserName())
				.withLifeTime(Instant.now(), Instant.now()).build();
		
		when(storage.getToken(t.getHashedToken())).thenReturn(token, (StoredToken) null);
		
		if (user.getUserName().equals(admin.getUserName())) {
			when(storage.getUser(admin.getUserName())).thenReturn(admin, user, (AuthUser) null);
		} else {
			when(storage.getUser(admin.getUserName())).thenReturn(admin, (AuthUser) null);
			when(storage.getUser(user.getUserName())).thenReturn(user, (AuthUser) null);
		}
		
		try {
			final AuthUser gotUser = auth.getUserAsAdmin(t, user.getUserName());
		
			assertThat("incorrect user", gotUser, is(user));
		} catch (Throwable th) {
			if (admin.isDisabled()) {
				verify(storage).deleteTokens(admin.getUserName());
			} else {
				verify(storage, never()).deleteTokens(admin.getUserName());
			}
			throw th;
		}
	}
	
	private void failGetUserAsAdmin(
			final AuthUser admin,
			final AuthUser user,
			final Exception e) {
		try {
			getUserAsAdmin(admin, user);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failGetUserAsAdmin(
			final Authentication auth,
			final IncomingToken token,
			final UserName user,
			final Exception e) {
		try {
			auth.getUserAsAdmin(token, user);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}

}
