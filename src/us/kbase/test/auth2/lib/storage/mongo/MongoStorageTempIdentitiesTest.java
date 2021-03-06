package us.kbase.test.auth2.lib.storage.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import static us.kbase.test.auth2.TestCommon.set;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.bson.Document;
import org.junit.Test;

import us.kbase.auth2.lib.exceptions.ErrorType;
import us.kbase.auth2.lib.exceptions.NoSuchTokenException;
import us.kbase.auth2.lib.identity.RemoteIdentityDetails;
import us.kbase.auth2.lib.identity.RemoteIdentityID;
import us.kbase.auth2.lib.identity.RemoteIdentity;
import us.kbase.auth2.lib.storage.exceptions.AuthStorageException;
import us.kbase.auth2.lib.TemporaryIdentities;
import us.kbase.auth2.lib.token.IncomingHashedToken;
import us.kbase.auth2.lib.token.IncomingToken;
import us.kbase.auth2.lib.token.TemporaryHashedToken;
import us.kbase.auth2.lib.token.TemporaryToken;
import us.kbase.test.auth2.TestCommon;

public class MongoStorageTempIdentitiesTest extends MongoStorageTester {
	
	private static final RemoteIdentity REMOTE1 = new RemoteIdentity(
			new RemoteIdentityID("prov", "bar1"),
			new RemoteIdentityDetails("user1", "full1", "email1"));
	
	private static final RemoteIdentity REMOTE2 = new RemoteIdentity(
			new RemoteIdentityID("prov", "bar2"),
			new RemoteIdentityDetails("user2", "full2", "email2"));
	
	@Test
	public void storeAndGetEmpty() throws Exception {
		final UUID id = UUID.randomUUID();
		final Instant now = Instant.now();
		final TemporaryHashedToken tt = new TemporaryToken(
				id, "foobar", now, 10000)
					.getHashedToken();
		storage.storeIdentitiesTemporarily(tt, Collections.emptySet());
		
		assertThat("incorrect identities", storage.getTemporaryIdentities(
				new IncomingToken("foobar").getHashedToken()), is(
						new TemporaryIdentities(id, now, now.plusMillis(10000),
								Collections.emptySet())));
	}
	
	@Test
	public void storeAndGet1() throws Exception {
		final UUID id = UUID.randomUUID();
		final Instant now = Instant.now();
		final TemporaryHashedToken tt = new TemporaryToken(
				id, "foobar", now, 10000)
					.getHashedToken();
		storage.storeIdentitiesTemporarily(tt, set(REMOTE2));
		
		assertThat("incorrect identities", storage.getTemporaryIdentities(
				new IncomingToken("foobar").getHashedToken()), is(
						new TemporaryIdentities(id, now, now.plusMillis(10000), set(REMOTE2))));
	}
	
	@Test
	public void storeAndGet2() throws Exception {
		final UUID id = UUID.randomUUID();
		final Instant now = Instant.now();
		final TemporaryHashedToken tt = new TemporaryToken(id, "foobar", now, 10000)
					.getHashedToken();
		storage.storeIdentitiesTemporarily(tt, set(REMOTE2, REMOTE1));
		
		assertThat("incorrect identities", storage.getTemporaryIdentities(
				new IncomingToken("foobar").getHashedToken()), is(
						new TemporaryIdentities(id, now, now.plusMillis(10000),
								set(REMOTE2, REMOTE1))));
	}
	
	@Test
	public void storeError() throws Exception {
		final UUID id = UUID.randomUUID();
		final Instant now = Instant.now();
		final TemporaryHashedToken tt = new TemporaryToken(id, "foobar", now, 10000)
					.getHashedToken();
		storage.storeErrorTemporarily(tt, "foobarbaz", ErrorType.ID_ALREADY_LINKED);
		assertThat("incorrect temp error", storage.getTemporaryIdentities(
				new IncomingToken("foobar").getHashedToken()),
				is(new TemporaryIdentities(id, now, now.plusMillis(10000), "foobarbaz",
						ErrorType.ID_ALREADY_LINKED)));
	}
	
	@Test
	public void storeTempIDFailNulls() {
		final TemporaryHashedToken tt = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 10000)
					.getHashedToken();
		failStoreTemporaryIdentity(null, Collections.emptySet(),
				new NullPointerException("token"));
		failStoreTemporaryIdentity(tt, null, new NullPointerException("identitySet"));
		failStoreTemporaryIdentity(tt, set(REMOTE1, null),
				new NullPointerException("Null value in identitySet"));
	}
	
	@Test
	public void storeTempErrFailNullsAndEmpties() {
		final TemporaryHashedToken tt = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 10000)
					.getHashedToken();
		failStoreTemporaryError(null, "foobar", ErrorType.ID_PROVIDER_ERROR,
				new NullPointerException("token"));
		failStoreTemporaryError(tt, null, ErrorType.ID_PROVIDER_ERROR,
				new IllegalArgumentException("Missing argument: error"));
		failStoreTemporaryError(tt, "   \t  \n ", ErrorType.ID_PROVIDER_ERROR,
				new IllegalArgumentException("Missing argument: error"));
		failStoreTemporaryError(tt, "foobar", null, new NullPointerException("errorType"));
	}
	
	@Test
	public void storeTempIDFailDuplicateTokenID() throws Exception {
		final TemporaryHashedToken tt = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 10000)
					.getHashedToken();
		final TemporaryHashedToken tt2 = new TemporaryToken(
				UUID.randomUUID(), "foobar2", Instant.now(), 10000)
					.getHashedToken();
		final Field id = tt2.getClass().getDeclaredField("id");
		id.setAccessible(true);
		id.set(tt2, tt.getId());
		
		storage.storeIdentitiesTemporarily(tt, set(REMOTE1));
		failStoreTemporaryIdentity(tt2, set(REMOTE1), new IllegalArgumentException(
				"Temporary token ID " + tt2.getId() + " already exists in the database"));
	}
	
	@Test
	public void storeTempErrorFailDuplicateTokenID() throws Exception {
		final TemporaryHashedToken tt = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 10000)
					.getHashedToken();
		final TemporaryHashedToken tt2 = new TemporaryToken(
				UUID.randomUUID(), "foobar2", Instant.now(), 10000)
					.getHashedToken();
		final Field id = tt2.getClass().getDeclaredField("id");
		id.setAccessible(true);
		id.set(tt2, tt.getId());
		
		storage.storeErrorTemporarily(tt, "foo", ErrorType.AUTHENTICATION_FAILED);
		failStoreTemporaryError(tt2, "bar", ErrorType.DISABLED, new IllegalArgumentException(
				"Temporary token ID " + tt2.getId() + " already exists in the database"));
	}
	
	@Test
	public void storeTempIDFailDuplicateToken() throws Exception {
		final TemporaryHashedToken tt = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 10000)
					.getHashedToken();
		final TemporaryHashedToken tt2 = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 10000)
					.getHashedToken();
		
		storage.storeIdentitiesTemporarily(tt, set(REMOTE1));
		failStoreTemporaryIdentity(tt2, set(REMOTE1), new IllegalArgumentException(
				"Token hash for temporary token ID " + tt2.getId() +
				" already exists in the database"));
	}
	
	@Test
	public void storeTempErrFailDuplicateToken() throws Exception {
		final TemporaryHashedToken tt = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 10000)
					.getHashedToken();
		final TemporaryHashedToken tt2 = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 10000)
					.getHashedToken();
		
		storage.storeErrorTemporarily(tt, "foo", ErrorType.AUTHENTICATION_FAILED);
		failStoreTemporaryError(tt2, "bar", ErrorType.DISABLED, new IllegalArgumentException(
				"Token hash for temporary token ID " + tt2.getId() +
				" already exists in the database"));
	}
	
	private void failStoreTemporaryIdentity(
			final TemporaryHashedToken token,
			final Set<RemoteIdentity> ids,
			final Exception e) {
		try {
			storage.storeIdentitiesTemporarily(token, ids);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failStoreTemporaryError(
			final TemporaryHashedToken token,
			final String error,
			final ErrorType errorType,
			final Exception e) {
		try {
			storage.storeErrorTemporarily(token, error, errorType);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void getTempIDFailNull() {
		failGetTemporaryIdentity(null, new NullPointerException("token"));
	}
	
	@Test
	public void getTempIDFailNoSuchToken() throws Exception {
		failGetTemporaryIdentity(new IncomingToken("foo").getHashedToken(),
				new NoSuchTokenException("Token not found"));
	}
	
	@Test
	public void getTempIDFailExpiredToken() throws Exception {
		/* this test could not cover the intended code if mongo removes the record before the test
		 * concludes.
		 * maybe there's a way of turning token removal off temporarily?
		 * only 1 sweep / min so not very likely
		 */
		final TemporaryHashedToken tt = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 0)
					.getHashedToken();
		storage.storeIdentitiesTemporarily(tt, Collections.emptySet());
		Thread.sleep(1);
		failGetTemporaryIdentity(new IncomingToken("foobar").getHashedToken(),
				new NoSuchTokenException("Token not found"));
	}
	
	@Test
	public void getTempIDFailBadDBData() throws Exception {
		final TemporaryHashedToken tt = new TemporaryToken(
				UUID.randomUUID(), "foobar", Instant.now(), 10000)
					.getHashedToken();
		storage.storeIdentitiesTemporarily(tt, Collections.emptySet());
		db.getCollection("temptokens").updateOne(new Document("id", tt.getId().toString()),
				new Document("$set", new Document("idents", null)));
		failGetTemporaryIdentity(new IncomingToken("foobar").getHashedToken(),
				new AuthStorageException(String.format(
						"Temporary token %s has no associated IDs field", tt.getId())));
	}
	
	private void failGetTemporaryIdentity(
			final IncomingHashedToken token,
			final Exception e) {
		try {
			storage.getTemporaryIdentities(token);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void deleteTempIDs() throws Exception {
		final UUID id = UUID.randomUUID();
		final Instant now = Instant.now();
		final TemporaryHashedToken tt = new TemporaryToken(
				id, "foobar", now, 10000)
					.getHashedToken();
		storage.storeIdentitiesTemporarily(tt, set(REMOTE2));
		
		// check token is there
		storage.getTemporaryIdentities(new IncomingToken("foobar").getHashedToken());
		
		storage.deleteTemporaryIdentities(new IncomingToken("foobar").getHashedToken());
		
		failGetTemporaryIdentity(new IncomingToken("foobar").getHashedToken(),
				new NoSuchTokenException("Token not found"));
	}
	
	@Test
	public void deleteTempIDsFailNull() throws Exception {
		try {
			storage.deleteTemporaryIdentities(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("token"));
		}
	}

}
