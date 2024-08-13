<p>Dear ${project} Portal Onboarding Team,</p>
<p>
    <#if providerBundle.templateStatus == "no template status">
        <#if providerBundle.status == "pending provider">
            A new application by [${user.fullName}] – [${user.email}] has been received for registering
            [${providerBundle.provider.name}] - ([${providerBundle.provider.id}]) as a new ${project} Provider in
            ${project} Portal.
            <br>
            You can review the application here
            ${endpoint}/dashboard/${project?lower_case}/${providerBundle.provider.id}/info
            and approve or reject it.
        </#if>
        <#if providerBundle.status == "approved provider">
            <#if providerBundle.active == true>
                The application by [${user.fullName}] – [${user.email}] for registering
                [${providerBundle.provider.name}] - ([${providerBundle.provider.id}]) has been approved.
                <br>
                You can view the application status here
                ${endpoint}/dashboard/${project?lower_case}/${providerBundle.provider.id}/info.
            <#else>
                The Provider [${providerBundle.provider.name}] - ([${providerBundle.provider.id}]) has been set to
                inactive.
                <br>
                You can view the application status here
                ${endpoint}/dashboard/${project?lower_case}/${providerBundle.provider.id}/info.
            </#if>
        </#if>
        <#if providerBundle.status == "rejected provider">
            The application by [${user.fullName}] – [${user.email}] for registering
            [${providerBundle.provider.name}] - ([${providerBundle.provider.id}]) has been rejected.
            <br>
            You can view the application status here
            ${endpoint}/dashboard/${project?lower_case}/${providerBundle.provider.id}/info.
        </#if>
    <#else>
        <#if providerBundle.templateStatus == "pending template">
            A new application by [${user.fullName}] – [${user.email}] has been received for registering
            [${resourceName}] - [${resourceId}], as a new Resource of
            [${providerBundle.provider.name}] - ([${providerBundle.provider.id}]).
            <br>
            You can review the application here
            ${endpoint}/dashboard/${project?lower_case}/${providerBundle.provider.id}/info and approve or reject it.
        </#if>
        <#if providerBundle.templateStatus == "approved template">
            The application by [${user.fullName}] – [${user.email}] for registering
            [${resourceName}] - ([${resourceId}]) of
            [${providerBundle.provider.name}] - ([${providerBundle.provider.id}]) has been approved.
            <br>
            You can view the application status here
            ${endpoint}/dashboard/${project?lower_case}/${providerBundle.provider.id}/info.
        </#if>
        <#if providerBundle.templateStatus == "rejected template">
            The Resource: [${resourceId}] provided by [${user.fullName}] – [${user.email}] has been rejected.
            <br>
            You can view the application status
            ${endpoint}/dashboard/${project?lower_case}/${providerBundle.provider.id}/info.
        </#if>
    </#if>
</p>
<p>
    Best Regards,
    <br>
    the ${project} Team
</p>