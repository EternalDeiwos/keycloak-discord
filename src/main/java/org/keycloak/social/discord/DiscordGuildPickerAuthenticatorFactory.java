package org.keycloak.social.discord;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

public class DiscordGuildPickerAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "discord-guild-picker";
    public static final String GUILD_REQUIRED = "guildRequired";

    private static final DiscordGuildPickerAuthenticator SINGLETON = new DiscordGuildPickerAuthenticator();
    private static final Requirement[] REQUIREMENT_CHOICES = {
            Requirement.REQUIRED, Requirement.ALTERNATIVE, Requirement.DISABLED
    };

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Discord Guild Picker";
    }

    @Override
    public String getReferenceCategory() {
        return "discord-guild";
    }

    @Override
    public String getHelpText() {
        return "Lets the user pick an active Discord guild from the guilds that both the user and the bot have joined. " +
                "Stores it in the user session notes '" + DiscordGuildPickerAuthenticator.GUILD_ID_NOTE + "' and '" +
                DiscordGuildPickerAuthenticator.GUILD_NAME_NOTE + "'. Use it in the post login flow of a Discord identity provider.";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(GUILD_REQUIRED)
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .label("Guild required")
                .helpText("If enabled, the user must pick a guild and login fails when no guild is available. " +
                        "If disabled, the user can skip the choice.")
                .defaultValue(Boolean.FALSE)
                .add()
                .build();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
