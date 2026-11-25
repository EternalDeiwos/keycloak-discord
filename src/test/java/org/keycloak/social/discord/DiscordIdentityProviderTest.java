package org.keycloak.social.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscordIdentityProviderTest {

    DiscordIdentityProvider provider;
    ObjectMapper mapper;

    @Mock KeycloakSession session;
    @Mock DiscordIdentityProviderConfig config;

    @BeforeEach
    void setUp() {
        when(config.getAlias()).thenReturn("discord");
        when(config.isEnabled()).thenReturn(true);
        provider = new DiscordIdentityProvider(session, config);
        mapper = new ObjectMapper();
    }

    @Test
    void testNameDiscriminatorHandling() throws Exception {
        String json = """
{
  "id": "80351110224678912",
  "username": "Nelly",
  "discriminator": "1337",
  "email": "nelly@discord.com",
  "verified": true
}
        """;
        BrokeredIdentityContext user = provider.extractIdentityFromProfile(null, mapper.readTree(json));
        assertEquals("80351110224678912", user.getId());
        assertEquals("nelly#1337", user.getUsername());
    }

    @Test
    void testNameDiscriminatorHandling_NoDiscriminator() throws Exception {
        String json = """
{
  "id": "80351110224678912",
  "username": "nelly",
  "discriminator": "0",
  "email": "nelly@discord.com",
  "verified": true
}
        """;
        BrokeredIdentityContext user = provider.extractIdentityFromProfile(null, mapper.readTree(json));
        assertEquals("80351110224678912", user.getId());
        assertEquals("nelly", user.getUsername());
        assertEquals("nelly@discord.com", user.getEmail());
    }

    @Test
    void testExtractIdentity_NoAvatar() throws Exception {
        String json = """
{
  "id": "80351110224678912",
  "username": "nelly",
  "discriminator": "0",
  "email": "nelly@discord.com",
  "verified": true
}
        """;
        BrokeredIdentityContext user = provider.extractIdentityFromProfile(null, mapper.readTree(json));
        assertNull(user.getUserAttribute("picture"));
    }

    @Test
    void testExtractIdentity_WithStaticAvatar() throws Exception {
        String json = """
{
  "id": "80351110224678912",
  "username": "nelly",
  "discriminator": "0",
  "avatar": "8342729096ea3675442027381ff50dfe",
  "email": "nelly@discord.com",
  "verified": true
}
        """;
        BrokeredIdentityContext user = provider.extractIdentityFromProfile(null, mapper.readTree(json));
        assertEquals(
            "https://cdn.discordapp.com/avatars/80351110224678912/8342729096ea3675442027381ff50dfe.png?size=256",
            user.getUserAttribute("picture")
        );
    }

    @Test
    void testExtractIdentity_WithAnimatedAvatar() throws Exception {
        String json = """
{
  "id": "80351110224678912",
  "username": "nelly",
  "discriminator": "0",
  "avatar": "a_8342729096ea3675442027381ff50dfe",
  "email": "nelly@discord.com",
  "verified": true
}
        """;
        BrokeredIdentityContext user = provider.extractIdentityFromProfile(null, mapper.readTree(json));
        assertEquals(
            "https://cdn.discordapp.com/avatars/80351110224678912/a_8342729096ea3675442027381ff50dfe.gif?size=256",
            user.getUserAttribute("picture")
        );
    }

    @Test
    void testExtractIdentity_EmailNotVerified_ShouldThrow() throws Exception {
        String json = """
{
  "id": "80351110224678912",
  "username": "nelly",
  "discriminator": "0",
  "email": "nelly@discord.com",
  "verified": false
}
        """;
        JsonNode profile = mapper.readTree(json);
        assertThrows(IdentityBrokerException.class,
                () -> provider.extractIdentityFromProfile(null, profile),
                "Should reject unverified email");
    }

    @Test
    void testExtractIdentity_EmailWithMissingVerifiedField_ShouldThrow() throws Exception {
        String json = """
{
  "id": "80351110224678912",
  "username": "nelly",
  "discriminator": "0",
  "email": "nelly@discord.com"
}
        """;
        JsonNode profile = mapper.readTree(json);
        assertThrows(IdentityBrokerException.class,
                () -> provider.extractIdentityFromProfile(null, profile),
                "Should reject when verified field is absent");
    }

    @Test
    void testExtractIdentity_NoEmail_ShouldSucceed() throws Exception {
        String json = """
{
  "id": "80351110224678912",
  "username": "nelly",
  "discriminator": "0"
}
        """;
        BrokeredIdentityContext user = provider.extractIdentityFromProfile(null, mapper.readTree(json));
        assertEquals("nelly", user.getUsername());
        assertNull(user.getEmail());
    }

    @Test
    void testDefaultScopes_WithBotToken_ShouldRequestGuilds() {
        assertFalse(provider.getDefaultScopes().contains(DiscordIdentityProvider.GUILDS_SCOPE));

        when(config.hasBotToken()).thenReturn(true);
        assertTrue(provider.getDefaultScopes().contains(DiscordIdentityProvider.GUILDS_SCOPE));
    }

    @Test
    void testFetchGuilds_ShouldFollowPagination() throws Exception {
        ArrayNode fullPage = mapper.createArrayNode();
        for (int i = 1; i <= 200; i++) {
            fullPage.addObject().put("id", String.valueOf(i));
        }
        ArrayNode lastPage = mapper.createArrayNode();
        lastPage.addObject().put("id", "201");

        SimpleHttp http = mock(SimpleHttp.class);
        when(http.header(anyString(), anyString())).thenReturn(http);
        when(http.param(anyString(), anyString())).thenReturn(http);
        when(http.asJson()).thenReturn(fullPage, lastPage);

        try (MockedStatic<SimpleHttp> simpleHttp = mockStatic(SimpleHttp.class)) {
            simpleHttp.when(() -> SimpleHttp.doGet(DiscordIdentityProvider.GROUP_URL, session)).thenReturn(http);

            List<JsonNode> guilds = DiscordIdentityProvider.fetchGuilds(session, "Bot token");

            assertEquals(201, guilds.size());
            verify(http, times(2)).header("Authorization", "Bot token");
            verify(http).param("after", "0");
            verify(http).param("after", "200");
        }
    }

    @Test
    void testExtractIdentity_EmailScopeNotRequested_ShouldIgnoreEmail() throws Exception {
        when(config.getDefaultScope()).thenReturn("identify guilds");
        String json = """
{
  "id": "80351110224678912",
  "username": "nelly",
  "discriminator": "0",
  "email": "nelly@discord.com",
  "verified": false
}
        """;
        JsonNode profile = mapper.readTree(json);
        BrokeredIdentityContext user = provider.extractIdentityFromProfile(null, profile);
        assertNull(user.getEmail());
        assertFalse(profile.has("email"), "Email must not reach the attribute mappers");
        assertFalse(profile.has("verified"));
    }
}
