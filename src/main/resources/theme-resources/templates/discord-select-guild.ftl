<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "header">
        ${msg("discordGuildSelectTitle")}
    <#elseif section = "form">
        <form action="${url.loginAction}" class="form-vertical" method="post">
            <div id="kc-discord-guilds" class="${properties.kcFormGroupClass!}">
                <p>${msg("discordGuildSelect")}</p>

                <ul class="${properties.kcFormSocialAccountListClass!} <#if guilds?size gt 3>${properties.kcFormSocialAccountListGridClass!}</#if>">
                    <#list guilds as guild>
                        <li class="${properties.kcFormSocialAccountListItemClass!}">
                            <button type="submit" id="discord-guild-${guild.id()}" name="guild" value="${guild.id()}"
                                    class="${properties.kcFormSocialAccountListButtonClass!} <#if guilds?size gt 3>${properties.kcFormSocialAccountGridItem!}</#if>">
                                <span class="${properties.kcFormSocialAccountNameClass!}">${guild.name()}</span>
                            </button>
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
