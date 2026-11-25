package org.keycloak.social.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.broker.util.PostBrokerLoginConstants;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.UserAuthenticationIdentityProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.IdentityProviderStorageProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.util.JsonSerialization;
import org.keycloak.vault.VaultStringSecret;
import org.keycloak.vault.VaultTranscriber;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscordGuildPickerAuthenticatorTest {

    static final String ALIAS = "discord";
    static final String USER_TOKEN = "user-token";
    static final String BOT_TOKEN = "bot-token";
    static final String GUILD_A = "111111111111111111";
    static final String GUILD_B = "222222222222222222";
    static final String GUILD_C = "333333333333333333";

    final ObjectMapper mapper = new ObjectMapper();
    final DiscordGuildPickerAuthenticator authenticator = new DiscordGuildPickerAuthenticator();
    final Map<String, String> authNotes = new HashMap<>();
    final Map<String, String> config = new HashMap<>();
    final IdentityProviderModel idp = new IdentityProviderModel();

    @Mock AuthenticationFlowContext context;
    @Mock KeycloakSession session;
    @Mock RealmModel realm;
    @Mock AuthenticationSessionModel authSession;
    @Mock IdentityProviderStorageProvider identityProviders;
    @Mock VaultTranscriber vault;
    @Mock VaultStringSecret vaultSecret;
    @Mock LoginFormsProvider form;
    @Mock Response formResponse;
    @Mock HttpRequest httpRequest;

    MockedStatic<DiscordIdentityProvider> discord;

    @BeforeEach
    void setUp() throws Exception {
        idp.setAlias(ALIAS);
        idp.setProviderId(DiscordIdentityProviderFactory.PROVIDER_ID);
        idp.getConfig().put(DiscordIdentityProviderConfig.BOT_TOKEN, BOT_TOKEN);

        when(context.getSession()).thenReturn(session);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        AuthenticatorConfigModel configModel = new AuthenticatorConfigModel();
        configModel.setConfig(config);
        when(context.getAuthenticatorConfig()).thenReturn(configModel);
        when(context.form()).thenReturn(form);
        when(context.getHttpRequest()).thenReturn(httpRequest);

        when(session.identityProviders()).thenReturn(identityProviders);
        when(identityProviders.getByAlias(ALIAS)).thenReturn(idp);
        when(session.vault()).thenReturn(vault);
        when(vault.getStringSecret(BOT_TOKEN)).thenReturn(vaultSecret);
        when(vaultSecret.get()).thenReturn(Optional.of(BOT_TOKEN));

        when(authSession.getRealm()).thenReturn(realm);
        when(authSession.getAuthNote(anyString())).thenAnswer(i -> authNotes.get(i.<String>getArgument(0)));
        doAnswer(i -> authNotes.put(i.getArgument(0), i.getArgument(1))).when(authSession).setAuthNote(anyString(), anyString());

        when(form.setAttribute(anyString(), any())).thenReturn(form);
        when(form.setError(anyString())).thenReturn(form);
        when(form.createForm(anyString())).thenReturn(formResponse);
        when(form.createErrorPage(any())).thenReturn(formResponse);

        SerializedBrokeredIdentityContext brokered = new SerializedBrokeredIdentityContext();
        brokered.setIdentityProviderId(ALIAS);
        brokered.getContextData().put(UserAuthenticationIdentityProvider.FEDERATED_ACCESS_TOKEN,
                SerializedBrokeredIdentityContext.ContextDataEntry.create(String.class.getName(), USER_TOKEN));
        authNotes.put(PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT, JsonSerialization.writeValueAsString(brokered));

        discord = mockStatic(DiscordIdentityProvider.class);
        discord.when(() -> DiscordIdentityProvider.cdnImageUrl(any(), any(), any())).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() {
        discord.close();
    }

    @Test
    void candidates_shouldKeepUserOrderAndOnlyBotGuilds() throws Exception {
        List<DiscordGuildPickerAuthenticator.Guild> candidates = DiscordGuildPickerAuthenticator.candidates(
                guilds(GUILD_C, GUILD_A, GUILD_B), guilds(GUILD_A, GUILD_C));

        assertEquals(List.of(guild(GUILD_C), guild(GUILD_A)), candidates);
    }

    @Test
    void candidates_shouldIncludeIconUrl() throws Exception {
        JsonNode staticIcon = mapper.readTree("{\"id\":\"" + GUILD_A + "\",\"name\":\"A\",\"icon\":\"8342729096ea3675442027381ff50dfe\"}");
        JsonNode animatedIcon = mapper.readTree("{\"id\":\"" + GUILD_B + "\",\"name\":\"B\",\"icon\":\"a_8342729096ea3675442027381ff50dfe\"}");
        JsonNode invalidIcon = mapper.readTree("{\"id\":\"" + GUILD_C + "\",\"name\":\"C\",\"icon\":\"../evil\"}");

        List<DiscordGuildPickerAuthenticator.Guild> candidates = DiscordGuildPickerAuthenticator.candidates(
                List.of(staticIcon, animatedIcon, invalidIcon), guilds(GUILD_A, GUILD_B, GUILD_C));

        assertEquals("https://cdn.discordapp.com/icons/" + GUILD_A + "/8342729096ea3675442027381ff50dfe.png?size=128", candidates.get(0).icon());
        assertEquals("https://cdn.discordapp.com/icons/" + GUILD_B + "/a_8342729096ea3675442027381ff50dfe.gif?size=128", candidates.get(1).icon());
        assertNull(candidates.get(2).icon());
    }

    @Test
    void authenticate_withoutBrokeredContext_shouldBeAttempted() {
        authNotes.clear();

        authenticator.authenticate(context);

        verify(context).attempted();
        discord.verifyNoInteractions();
    }

    @Test
    void authenticate_withNonDiscordProvider_shouldBeAttempted() {
        idp.setProviderId("github");

        authenticator.authenticate(context);

        verify(context).attempted();
    }

    @Test
    void authenticate_withoutBotToken_shouldFail() {
        idp.getConfig().remove(DiscordIdentityProviderConfig.BOT_TOKEN);

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INTERNAL_ERROR);
    }

    @Test
    void authenticate_noCandidates_optional_shouldSucceedWithoutNote() throws Exception {
        givenGuilds(guilds(GUILD_A), guilds(GUILD_B));

        authenticator.authenticate(context);

        verify(context).success();
        verify(authSession, never()).setUserSessionNote(anyString(), anyString());
    }

    @Test
    void authenticate_noCandidates_required_shouldDenyAccess() throws Exception {
        config.put(DiscordGuildPickerAuthenticatorFactory.GUILD_REQUIRED, "true");
        givenGuilds(guilds(GUILD_A), guilds(GUILD_B));

        authenticator.authenticate(context);

        verify(form).setError("discordGuildRequired");
        verify(context).failure(AuthenticationFlowError.ACCESS_DENIED, formResponse);
    }

    @Test
    void authenticate_oneCandidate_shouldSelectWithoutPrompt() throws Exception {
        givenGuilds(guilds(GUILD_A, GUILD_B), guilds(GUILD_B, GUILD_C));

        authenticator.authenticate(context);

        verifySelected(GUILD_B);
        verify(context, never()).challenge(any());
    }

    @Test
    void authenticate_manyCandidates_shouldShowForm() throws Exception {
        givenGuilds(guilds(GUILD_A, GUILD_B), guilds(GUILD_A, GUILD_B));

        authenticator.authenticate(context);

        verify(form).setAttribute("guilds", List.of(guild(GUILD_A), guild(GUILD_B)));
        verify(form).createForm(DiscordGuildPickerAuthenticator.FORM);
        verify(context).challenge(formResponse);
    }

    @Test
    void action_validGuild_shouldSelect() throws Exception {
        givenCandidatesShown();
        givenFormParam("guild", GUILD_B);

        authenticator.action(context);

        verifySelected(GUILD_B);
    }

    @Test
    void action_unknownGuild_shouldShowFormAgain() throws Exception {
        givenCandidatesShown();
        givenFormParam("guild", GUILD_C);

        authenticator.action(context);

        verify(form).setError("discordGuildInvalid");
        verify(context).failureChallenge(AuthenticationFlowError.INVALID_USER, formResponse);
        verify(authSession, never()).setUserSessionNote(anyString(), anyString());
    }

    @Test
    void action_skip_optional_shouldSucceedWithoutNote() throws Exception {
        givenCandidatesShown();
        givenFormParam("skip", "true");

        authenticator.action(context);

        verify(context).success();
        verify(authSession, never()).setUserSessionNote(anyString(), anyString());
    }

    @Test
    void action_skip_required_shouldShowFormAgain() throws Exception {
        config.put(DiscordGuildPickerAuthenticatorFactory.GUILD_REQUIRED, "true");
        givenCandidatesShown();
        givenFormParam("skip", "true");

        authenticator.action(context);

        verify(context, never()).success();
        verify(context).failureChallenge(AuthenticationFlowError.INVALID_USER, formResponse);
    }

    private void givenGuilds(List<JsonNode> userGuilds, List<JsonNode> botGuilds) {
        discord.when(() -> DiscordIdentityProvider.fetchGuilds(session, "Bearer " + USER_TOKEN)).thenReturn(userGuilds);
        discord.when(() -> DiscordIdentityProvider.fetchGuilds(session, "Bot " + BOT_TOKEN)).thenReturn(botGuilds);
    }

    private void givenCandidatesShown() throws Exception {
        givenGuilds(guilds(GUILD_A, GUILD_B), guilds(GUILD_A, GUILD_B));
        authenticator.authenticate(context);
        clearInvocations(context, form);
    }

    private void givenFormParam(String name, String value) {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add(name, value);
        when(httpRequest.getDecodedFormParameters()).thenReturn(params);
    }

    private void verifySelected(String guildId) {
        verify(authSession).setUserSessionNote(DiscordGuildPickerAuthenticator.GUILD_ID_NOTE, guildId);
        verify(authSession).setUserSessionNote(DiscordGuildPickerAuthenticator.GUILD_NAME_NOTE, "Guild " + guildId);
        verify(context).success();
    }

    private static DiscordGuildPickerAuthenticator.Guild guild(String id) {
        return new DiscordGuildPickerAuthenticator.Guild(id, "Guild " + id, null);
    }

    private List<JsonNode> guilds(String... ids) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        for (String id : ids) {
            result.add(mapper.readTree("{\"id\":\"" + id + "\",\"name\":\"Guild " + id + "\"}"));
        }
        return result;
    }
}
