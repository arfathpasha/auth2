package us.kbase.auth2.lib.storage;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.base.Optional;

import us.kbase.auth2.lib.CustomRole;
import us.kbase.auth2.lib.DisplayName;
import us.kbase.auth2.lib.PasswordHashAndSalt;
import us.kbase.auth2.lib.PolicyID;
import us.kbase.auth2.lib.Role;
import us.kbase.auth2.lib.TemporaryIdentities;
import us.kbase.auth2.lib.UserName;
import us.kbase.auth2.lib.UserSearchSpec;
import us.kbase.auth2.lib.UserUpdate;
import us.kbase.auth2.lib.config.AuthConfigSet;
import us.kbase.auth2.lib.config.AuthConfigUpdate;
import us.kbase.auth2.lib.config.ExternalConfig;
import us.kbase.auth2.lib.config.ExternalConfigMapper;
import us.kbase.auth2.lib.exceptions.ErrorType;
import us.kbase.auth2.lib.exceptions.ExternalConfigMappingException;
import us.kbase.auth2.lib.exceptions.IdentityLinkedException;
import us.kbase.auth2.lib.exceptions.IllegalParameterException;
import us.kbase.auth2.lib.exceptions.LinkFailedException;
import us.kbase.auth2.lib.exceptions.MissingParameterException;
import us.kbase.auth2.lib.exceptions.NoSuchIdentityException;
import us.kbase.auth2.lib.exceptions.NoSuchLocalUserException;
import us.kbase.auth2.lib.exceptions.NoSuchRoleException;
import us.kbase.auth2.lib.exceptions.NoSuchTokenException;
import us.kbase.auth2.lib.exceptions.NoSuchUserException;
import us.kbase.auth2.lib.exceptions.UnLinkFailedException;
import us.kbase.auth2.lib.exceptions.UserExistsException;
import us.kbase.auth2.lib.identity.RemoteIdentity;
import us.kbase.auth2.lib.storage.exceptions.AuthStorageException;
import us.kbase.auth2.lib.token.IncomingHashedToken;
import us.kbase.auth2.lib.token.StoredToken;
import us.kbase.auth2.lib.token.TemporaryHashedToken;
import us.kbase.auth2.lib.user.AuthUser;
import us.kbase.auth2.lib.user.LocalUser;
import us.kbase.auth2.lib.user.NewUser;

/** A storage system for the auth server.
 * 
 * Note that although inputs and outputs from the storage system are or may contain Instants, only
 * millisecond accuracy is guaranteed.
 * 
 * @author gaprice@lbl.gov
 *
 */
public interface AuthStorage {
	
	/** Create a new local account.
	 * @param local the user to create.
	 * @param creds the credentials to assign to the user.
	 * @throws UserExistsException if the user already exists.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 * @throws NoSuchRoleException if a custom role provided with the user doesn't exist.
	 */
	void createLocalUser(LocalUser local, PasswordHashAndSalt creds)
			throws AuthStorageException, UserExistsException, NoSuchRoleException;
	
	/** Get the user's credentials.
	 * @param userName the name of the user.
	 * @return the user credentials.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 * @throws NoSuchLocalUserException if the local user does not exist.
	 */
	PasswordHashAndSalt getPasswordHashAndSalt(UserName userName)
			throws AuthStorageException, NoSuchLocalUserException;
	
	/** Change a local user's password.
	 * @param name the name of the user.
	 * @param creds the new hashed password and salt.
	 * @param forceReset whether the user should be forced to reset their password on the next
	 * login.
	 * @throws NoSuchUserException if the user doesn't exist or is not a local user.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void changePassword(UserName name, PasswordHashAndSalt creds, boolean forceReset)
			throws NoSuchUserException, AuthStorageException;
	
	/** Force a local user to reset their password on the next login.
	 * @param name the name of the user.
	 * @throws NoSuchUserException if the user doesn't exist or is not a local user.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void forcePasswordReset(UserName name) throws NoSuchUserException, AuthStorageException;
	
	/** Force all local users to reset their passwords on the next login.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void forcePasswordReset() throws AuthStorageException;
	
	/** Create a non-local account.
	 * @param newUser the user to create.
	 * @throws UserExistsException if the user already exists.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 * @throws IdentityLinkedException if the remote identity provided with the user is already
	 * linked to a different user.
	 * @throws NoSuchRoleException if a role provided with the user doesn't exist.
	 */
	void createUser(NewUser newUser)
			throws UserExistsException, AuthStorageException, IdentityLinkedException,
				NoSuchRoleException;
	
	/** Disable a user account.
	 * @param user the name of the account to be disabled.
	 * @param admin the admin disabling the account.
	 * @param reason the reason the account is being disabled.
	 * @throws NoSuchUserException if the user does not exist.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void disableAccount(UserName user, UserName admin, String reason)
			throws NoSuchUserException, AuthStorageException;

	/** Enable a disabled user account.
	 * @param user the name of the account to be enabled.
	 * @param admin the admin enabling the account.
	 * @throws NoSuchUserException if the user does not exist.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void enableAccount(UserName user, UserName admin)
			throws NoSuchUserException, AuthStorageException;
	
	/** Get a local or non-local user.
	 * @param userName the user to get.
	 * @return the user.
	 * @throws NoSuchUserException if the user does not exist.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	AuthUser getUser(UserName userName)
			throws AuthStorageException, NoSuchUserException;
	
	/** Gets a user linked to a remote identity. Returns an empty Optional if the user doesn't
	 * exist. If the provider details (provider username, email address, and full name) are
	 * different, the details are updated in the storage system.
	 * @param remoteID a remote identity linked to a user.
	 * @return the user linked to the remote identity or null if there is no such user.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	Optional<AuthUser> getUser(RemoteIdentity remoteID) throws AuthStorageException;
	
	/** Get the display names for a set of users. Any non-existent users are left out of the
	 * returned map. Disabled users are never returned.
	 * @param usernames the usernames for which to get display names.
	 * @return a mapping of username to display name.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	Map<UserName, DisplayName> getUserDisplayNames(Set<UserName> usernames)
			throws AuthStorageException;
	
	//TODO ZLATER CODE could make a wrapper class for UserSearchSpec that doesn't include the root user stuff.
	/** Search for users based on a search specification.
	 * 
	 * Note that auth storage implementations have no knowledge of root users and therefore
	 * ignore the root user selection in the search specification.
	 * 
	 * @param spec the specification for the search.
	 * @param maxReturnedUsers the maximum number of users to return.
	 * @return a mapping of user name to display name for the discovered users.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	Map<UserName, DisplayName> getUserDisplayNames(
			UserSearchSpec spec,
			int maxReturnedUsers)
			throws AuthStorageException;

	/** Get a local user.
	 * @param userName the user to get.
	 * @return a local user.
	 * @throws NoSuchLocalUserException if the local user does not exist.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	LocalUser getLocalUser(UserName userName)
			throws AuthStorageException, NoSuchLocalUserException;
	
	/** Update the display name and/or email address for a user.
	 * @param userName the user to update.
	 * @param update the update to apply.
	 * @throws NoSuchUserException if the user does not exist.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void updateUser(UserName userName, UserUpdate update)
			throws NoSuchUserException, AuthStorageException;
	
	/** Set the last login date for a user.
	 * @param userName the user to modify.
	 * @param lastLogin the last login date for the user.
	 * @throws NoSuchUserException if the user does not exist.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void setLastLogin(UserName userName, Instant lastLogin)
			throws NoSuchUserException, AuthStorageException;
	
	/** Add policy IDs to the set of policy IDs already associated with a user.
	 * @param userName the name of the user to modify.
	 * @param policyIDs the policy IDs to add to the user.
	 * @throws NoSuchUserException if the user does not exist.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void addPolicyIDs(UserName userName, Set<PolicyID> policyIDs)
			throws NoSuchUserException, AuthStorageException;
	
	/** Remove a policy ID from all users in the database.
	 * @param policyID the policy ID to remove.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void removePolicyID(PolicyID policyID) throws AuthStorageException;
	
	/** Store a token in the database. No checking is done on the validity
	 * of the token - passing in tokens with bad data is a programming error.
	 * @param token the token to store.
	 * @param hash the hash of the token. This value will be used to look up the token in
	 * {@link #getToken(IncomingHashedToken)}
	 * @throws IllegalArgumentException if the token or the token ID already exists in the
	 * database.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void storeToken(StoredToken token, String hash) throws AuthStorageException;

	/** Get a token from the database based on the hash of the token.
	 * @param token the hashed token from which to retrieve details.
	 * @return the token.
	 * @throws NoSuchTokenException if no token matches the incoming token hash.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	StoredToken getToken(IncomingHashedToken token)
			throws AuthStorageException, NoSuchTokenException;

	/** Get all the tokens for a user.
	 * @param userName the user for which to retrieve tokens.
	 * @return the tokens that the user possesses.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	Set<StoredToken> getTokens(UserName userName) throws AuthStorageException;

	/** Deletes a token from the database.
	 * @param userName the user that owns the token.
	 * @param tokenId the ID of the token.
	 * @throws NoSuchTokenException if the user does not possess a token with the given ID.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void deleteToken(UserName userName, UUID tokenId)
			throws AuthStorageException, NoSuchTokenException;

	/** Deletes all tokens for a user.
	 * @param userName the user whose tokens will be deleted.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void deleteTokens(UserName userName) throws AuthStorageException;
	
	/** Delete all tokens in the database.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void deleteTokens() throws AuthStorageException;

	/** Update roles for a user.
	 * If a role is in addRoles and removeRoles it will be removed.
	 * Removing non-existent roles has no effect.
	 * @param userName the user to update.
	 * @param addRoles the roles to add the the user.
	 * @param removeRoles the roles to remove from the user.
	 * @throws NoSuchUserException if the user does not exist.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void updateRoles(UserName userName, Set<Role> addRoles, Set<Role> removeRoles)
			throws AuthStorageException, NoSuchUserException;

	/** Add a custom role if it does not already exist, or modify it if it does.
	 * @param role the role to add or modify.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void setCustomRole(CustomRole role) throws AuthStorageException;
	
	/** Deletes a custom role from the database and removes it from all users.
	 * @param roleId the ID of the role.
	 * @throws NoSuchRoleException if there is no such role.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 * @throws IllegalParameterException if the roleId is illegal.
	 * @throws MissingParameterException if the roleId is null or the empty string.
	 */
	void deleteCustomRole(String roleId) throws NoSuchRoleException, AuthStorageException,
			MissingParameterException, IllegalParameterException;

	/** Get all the custom roles in the database.
	 * @return the custom roles.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	Set<CustomRole> getCustomRoles() throws AuthStorageException;

	/** Updates custom roles for a user.
	 * If a role is in addRoles and removeRoles it will be removed.
	 * Removing non-existent roles has no effect.
	 * @param userName the user to modify.
	 * @param addRoles the roles to add to the user.
	 * @param removeRoles the roles to remove from the user. 
	 * @throws NoSuchUserException if the user doesn't exist.
	 * @throws NoSuchRoleException if one or more of the input roles do not exist in the database.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs. 
	 */
	void updateCustomRoles(UserName userName, Set<String> addRoles, Set<String> removeRoles)
			throws NoSuchUserException, AuthStorageException, NoSuchRoleException;

	/** Store an error string associated with a temporary token.
	 * @param token the temporary token.
	 * @param error the error.
	 * @param errorType the type of the error.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void storeErrorTemporarily(TemporaryHashedToken token, String error, ErrorType errorType) 
			throws AuthStorageException;
	
	/** Store a temporary token with a set of remote identities.
	 * Storing an empty set is allowed.
	 * No checking is done on the validity of the token - passing in tokens with bad data is a
	 * programming error.
	 * @param token the temporary token.
	 * @param ids the set of remote identities.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void storeIdentitiesTemporarily(
			TemporaryHashedToken token,
			Set<RemoteIdentity> ids)
			throws AuthStorageException;

	/** Get a set of identities associated with a token.
	 * @param token the token.
	 * @return the set of identities associated with the token.
	 * @throws NoSuchTokenException if the token does not exist in the storage system.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	TemporaryIdentities getTemporaryIdentities(
			IncomingHashedToken token)
			throws AuthStorageException, NoSuchTokenException;

	/** Delete the set of identities stored with a token.
	 * @param token the temporary token.
	 * @throws AuthStorageException if a problem connecting with the storage system occurs.
	 */
	void deleteTemporaryIdentities(IncomingHashedToken token)
			throws AuthStorageException;
	
	/** Link an account to a remote identity.
	 * If the account is already linked to the identity, the method proceeds without error, but
	 * if the provider details (provider username, email address, and full name) are different,
	 * the details in the database are overwritten by the passed in identity details. 
	 * @param userName the user to which the remote identity will be linked. 
	 * @param remoteID the remote identity.
	 * @throws NoSuchUserException if the user does not exist.
	 * @throws LinkFailedException if the user is a local user.
	 * @throws IdentityLinkedException if the identity is already linked to another user.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	void link(UserName userName, RemoteIdentity remoteID)
			throws NoSuchUserException, AuthStorageException,
			LinkFailedException, IdentityLinkedException;

	/** Remove a remote identity from a user.
	 * @param userName the user.
	 * @param id the remote identity to remove from the user.
	 * @throws NoSuchUserException if the user does not exist.
	 * @throws UnLinkFailedException if the user only has one identity or is a local user.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 * @throws NoSuchIdentityException if the user does not possess the identity.
	 */
	void unlink(UserName userName, String id)
			throws AuthStorageException, UnLinkFailedException, NoSuchUserException,
			NoSuchIdentityException;

	/** Update the system configuration.
	 * @param authConfigUpdate the configuration update.
	 * @param overwrite whether the new configuration should overwrite the current configuration.
	 * If false, only new values are stored.
	 * @param <T> the type of ExternalConfig included in the update.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	<T extends ExternalConfig> void updateConfig(
			AuthConfigUpdate<T> authConfigUpdate,
			boolean overwrite)
			throws AuthStorageException;

	/** Get the system configuration.
	 * @param mapper a mapper to transform a map of the external config into an external config
	 * class.
	 * @param <T> the type of ExternalConfig to which the external configuration will be mapped.
	 * @return the sysetem configuration.
	 * @throws ExternalConfigMappingException if the mapper failed to transform the external config
	 * map into the external config class.
	 * @throws AuthStorageException if a problem connecting with the storage
	 * system occurs.
	 */
	<T extends ExternalConfig> AuthConfigSet<T> getConfig(
			ExternalConfigMapper<T> mapper)
			throws AuthStorageException, ExternalConfigMappingException;
}
