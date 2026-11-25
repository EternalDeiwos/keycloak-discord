<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "header">
        ${msg("discordGuildSelectTitle")}
    <#elseif section = "form">
        <form action="${url.loginAction}" class="form-vertical" method="post">
            <div id="kc-discord-guilds" class="${properties.kcFormGroupClass!}">
                <p>${msg("discordGuildSelect")}</p>

                <ul class="discord-guild-list" style="list-style: none; padding: 0; display: flex; flex-wrap: wrap; justify-content: center; gap: 1rem;">
                    <#list guilds as guild>
                        <li>
                            <#if (guild.icon())??>
                                <button type="submit" id="discord-guild-${guild.id()}" name="guild" value="${guild.id()}" title="${guild.name()}"
                                        class="discord-guild-button ${properties.kcButtonClass!}" style="padding: 0; border-radius: 50%;">
                                    <img class="discord-guild-icon" src="${guild.icon()}" alt="${guild.name()}" width="64" height="64"
                                         style="display: block; border-radius: 50%;">
                                </button>
                            <#else>
                                <button type="submit" id="discord-guild-${guild.id()}" name="guild" value="${guild.id()}" title="${guild.name()}"
                                        class="discord-guild-button ${properties.kcButtonClass!} ${properties.kcButtonSecondaryClass!}"
                                        style="min-width: 64px; height: 64px; border-radius: 32px;">
                                    ${guild.name()}
                                </button>
                            </#if>
                        </li>
                    </#list>
                </ul>

                <#if !guildRequired>
                    <button type="submit" id="discord-guild-skip" name="skip" value="true"
                            class="${properties.kcButtonClass!} ${properties.kcButtonSecondaryClass!} ${properties.kcButtonBlockClass!}">
                        ${msg("discordGuildSkip")}
                    </button>
                </#if>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
