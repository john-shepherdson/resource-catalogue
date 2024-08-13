<p>Dear ${project} Portal Onboarding Team,</p>
<p>
    <#if interoperabilityRecordBundle.status == "pending interoperability record">
        A new application by [${registrant.fullName}] - [${registrant.email}] has been received for registering
        [${interoperabilityRecordBundle.interoperabilityRecord.title}] -
        [${interoperabilityRecordBundle.interoperabilityRecord.id}] as a new ${project} Interoperability Record
        in ${project} Portal.
        <br>
        You can review the application here ${endpoint}/guidelines/all and approve or reject it.
    <#elseif interoperabilityRecordBundle.status == "approved interoperability record">
        The application by [${registrant.fullName}] - [${registrant.email}] for registering
        [${interoperabilityRecordBundle.interoperabilityRecord.title}] -
        [${interoperabilityRecordBundle.interoperabilityRecord.id}] of
        [${interoperabilityRecordBundle.interoperabilityRecord.providerId}] has been approved.
        <br>
        You can view the application status here ${endpoint}/guidelines/all.
    <#else>
        The Interoperability Record: [${interoperabilityRecordBundle.interoperabilityRecord.title}] -
        [${interoperabilityRecordBundle.interoperabilityRecord.id}] provided
        by [${registrant.fullName}] - [${registrant.email}] has been rejected.
        <br>
        You can view the application status here ${endpoint}/guidelines/all.
    </#if>
</p>
<p>
    Best Regards,
    <br>
    the ${project} Team
</p>