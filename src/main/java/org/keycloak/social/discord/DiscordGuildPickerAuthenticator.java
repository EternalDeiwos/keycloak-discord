package org.keycloak.social.discord;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.broker.util.PostBrokerLoginConstants;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.UserAuthenticationIdentityProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.util.JsonSerialization;
import org.keycloak.vault.VaultStringSecret;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lets the user pick an active Discord guild in a post login flow of a Discord identity provider.
 * The choices are the guilds that both the user and the bot have joined. The result is stored as user session notes.
 */
public class DiscordGuildPickerAuthenticator implements Authenticator {

    public static final String GUILD_ID_NOTE = "discord.guild.id";
    public static final String GUILD_NAME_NOTE = "discord.guild.name";
    static final String CANDIDATES_NOTE = "discord.guild.candidates";
    static final String FORM = "discord-select-guild.ftl";

    private static final Logger log = Logger.getLogger(DiscordGuildPickerAuthenticator.class);

    /**
     * @param icon the guild icon URL on the Discord CDN, or null if the guild has no icon
     */
    public record Guild(String id, String name, String icon) {
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        SerializedBrokeredIdentityContext brokered = SerializedBrokeredIdentityContext
                .readFromAuthenticationSession(authSession, PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT);
        IdentityProviderModel idp = brokered == null ? null
                : context.getSession().identityProviders().getByAlias(brokered.getIdentityProviderId());

        if (idp == null || !DiscordIdentityProviderFactory.PROVIDER_ID.equals(idp.getProviderId())) {
            log.warn("Discord guild picker skipped: use it only in the post login flow of a Discord identity provider");
            context.attempted();
            return;
        }

        DiscordIdentityProviderConfig config = new DiscordIdentityProviderConfig(idp);
        if (!config.hasBotToken()) {
            log.errorf("Discord guild picker: identity provider '%s' has no bot token", idp.getAlias());
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        // The Discord provider uses the default marshaller, which stores String context data as is.
        SerializedBrokeredIdentityContext.ContextDataEntry userToken =
                brokered.getContextData().get(UserAuthenticationIdentityProvider.FEDERATED_ACCESS_TOKEN);
        if (userToken == null) {
            log.error("Discord guild picker: no Discord access token in the brokered context");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        List<Guild> candidates = candidates(
                DiscordIdentityProvider.fetchGuilds(context.getSession(), "Bearer " + userToken.getData()),
                DiscordIdentityProvider.fetchGuilds(context.getSession(), "Bot " + resolveSecret(context.getSession(), config.getBotToken())));

        switch (candidates.size()) {
            case 0 -> {
                if (isGuildRequired(context)) {
                    context.failure(AuthenticationFlowError.ACCESS_DENIED,
                            context.form().setError("discordGuildRequired").createErrorPage(Response.Status.FORBIDDEN));
                } else {
                    context.success();
                }
            }
            case 1 -> select(context, candidates.get(0));
            default -> {
                writeCandidates(authSession, candidates);
                context.challenge(createForm(context, candidates, null));
            }
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        List<Guild> candidates = readCandidates(context.getAuthenticationSession());

        if (form.containsKey("skip") && !isGuildRequired(context)) {
            context.success();
            return;
        }

        String guildId = form.getFirst("guild");
        candidates.stream()
                .filter(guild -> guild.id().equals(guildId))
                .findFirst()
                .ifPresentOrElse(guild -> select(context, guild),
                        () -> context.failureChallenge(AuthenticationFlowError.INVALID_USER,
                                createForm(context, candidates, "discordGuildInvalid")));
    }

    /**
     * Returns the guilds of the user that the bot has also joined, in the order of the user guild list.
     */
    static List<Guild> candidates(List<JsonNode> userGuilds, List<JsonNode> botGuilds) {
        Set<String> botGuildIds = botGuilds.stream()
                .map(guild -> guild.path("id").asText())
                .collect(Collectors.toSet());
        return userGuilds.stream()
                .map(guild -> {
                    String id = guild.path("id").asText();
                    String icon = DiscordIdentityProvider.cdnImageUrl(DiscordIdentityProvider.GUILD_ICON_URL, id, guild.path("icon").asText(null));
                    return new Guild(id, guild.path("name").asText(), icon);
                })
                .filter(guild -> botGuildIds.contains(guild.id()))
                .toList();
    }

    private static void select(AuthenticationFlowContext context, Guild guild) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        authSession.setUserSessionNote(GUILD_ID_NOTE, guild.id());
        authSession.setUserSessionNote(GUILD_NAME_NOTE, guild.name());
        authSession.removeAuthNote(CANDIDATES_NOTE);
        context.success();
    }

    private static boolean isGuildRequired(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        return config != null && config.getConfig() != null
                && Boolean.parseBoolean(config.getConfig().get(DiscordGuildPickerAuthenticatorFactory.GUILD_REQUIRED));
    }

    private static Response createForm(AuthenticationFlowContext context, List<Guild> candidates, String error) {
        var form = context.form()
                .setAttribute("guilds", candidates)
                .setAttribute("guildRequired", isGuildRequired(context));
        if (error != null) {
            form.setError(error);
        }
        return form.createForm(FORM);
    }

    private static String resolveSecret(KeycloakSession session, String value) {
        try (VaultStringSecret secret = session.vault().getStringSecret(value)) {
            return secret.get().orElse(value);
        }
    }

    private static void writeCandidates(AuthenticationSessionModel authSession, List<Guild> candidates) {
        try {
            authSession.setAuthNote(CANDIDATES_NOTE, JsonSerialization.writeValueAsString(candidates));
        } catch (IOException e) {
            throw new IllegalStateException("Could not store Discord guild candidates", e);
        }
    }

    private static List<Guild> readCandidates(AuthenticationSessionModel authSession) {
        String note = authSession.getAuthNote(CANDIDATES_NOTE);
        if (note == null) {
            return List.of();
        }
        try {
            return JsonSerialization.readValue(note, new TypeReference<List<Guild>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Could not read Discord guild candidates", e);
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
