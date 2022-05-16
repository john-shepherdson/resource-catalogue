package eu.einfracentral.registry.manager;

import eu.einfracentral.domain.*;
import eu.einfracentral.exception.ResourceNotFoundException;
import eu.einfracentral.exception.ValidationException;
import eu.einfracentral.registry.service.InfraServiceService;
import eu.einfracentral.registry.service.MonitoringService;
import eu.einfracentral.registry.service.ProviderService;
import eu.einfracentral.service.SecurityService;
import eu.openminted.registry.core.domain.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static eu.einfracentral.config.CacheConfig.CACHE_MONITORINGS;

@org.springframework.stereotype.Service("monitoringManager")
public class MonitoringManager<T extends Identifiable> extends ResourceManager<MonitoringBundle> implements MonitoringService<MonitoringBundle, Authentication> {

    private static final Logger logger = LogManager.getLogger(MonitoringManager.class);
    private final InfraServiceService<InfraService, InfraService> infraServiceService;
    private final ProviderService<ProviderBundle, Authentication> providerService;
    private final JmsTemplate jmsTopicTemplate;
    private final SecurityService securityService;

    public MonitoringManager(InfraServiceService<InfraService, InfraService> infraServiceService,
                             ProviderService<ProviderBundle, Authentication> providerService,
                             JmsTemplate jmsTopicTemplate, @Lazy SecurityService securityService) {
        super(MonitoringBundle.class);
        this.infraServiceService = infraServiceService;
        this.providerService = providerService;
        this.jmsTopicTemplate = jmsTopicTemplate;
        this.securityService = securityService;
    }

    @Override
    public String getResourceType() {
        return "monitoring";
    }

    @Override
    @CacheEvict(value = CACHE_MONITORINGS, allEntries = true)
    public MonitoringBundle add(MonitoringBundle monitoring, Authentication auth) {

        // check if Service exists and if User belongs to Service's Provider Admins
        serviceConsistency(monitoring.getMonitoring().getService(), monitoring.getCatalogueId());

        // validate serviceType
        serviceTypeValidation(monitoring.getMonitoring());

        monitoring.setId(UUID.randomUUID().toString());
        logger.trace("User '{}' is attempting to add a new Monitoring: {}", auth, monitoring);

        monitoring.setMetadata(Metadata.createMetadata(User.of(auth).getFullName(), User.of(auth).getEmail()));
        LoggingInfo loggingInfo = LoggingInfo.createLoggingInfoEntry(User.of(auth).getEmail(), User.of(auth).getFullName(), securityService.getRoleName(auth),
                LoggingInfo.Types.ONBOARD.getKey(), LoggingInfo.ActionType.REGISTERED.getKey());
        List<LoggingInfo> loggingInfoList = new ArrayList<>();
        loggingInfoList.add(loggingInfo);
        monitoring.setLoggingInfo(loggingInfoList);
        monitoring.setActive(true);
        // latestOnboardingInfo
        monitoring.setLatestOnboardingInfo(loggingInfo);

        MonitoringBundle ret;
        ret = super.add(monitoring, null);
        logger.debug("Adding Monitoring: {}", monitoring);

        //TODO: send emails
        jmsTopicTemplate.convertAndSend("monitoring.create", monitoring);

        return ret;
    }

    @Override
    @CacheEvict(value = CACHE_MONITORINGS, allEntries = true)
    public MonitoringBundle update(MonitoringBundle monitoring, Authentication auth) {

        logger.trace("User '{}' is attempting to update the Monitoring with id '{}'", auth, monitoring.getId());
        monitoring.setMetadata(Metadata.updateMetadata(monitoring.getMetadata(), User.of(auth).getFullName(), User.of(auth).getEmail()));
        List<LoggingInfo> loggingInfoList = new ArrayList<>();
        LoggingInfo loggingInfo;
        loggingInfo = LoggingInfo.createLoggingInfoEntry(User.of(auth).getEmail(), User.of(auth).getFullName(), securityService.getRoleName(auth),
                LoggingInfo.Types.UPDATE.getKey(), LoggingInfo.ActionType.UPDATED.getKey());
        if (monitoring.getLoggingInfo() != null) {
            loggingInfoList = monitoring.getLoggingInfo();
            loggingInfoList.add(loggingInfo);
        } else {
            loggingInfoList.add(loggingInfo);
        }
        monitoring.setLoggingInfo(loggingInfoList);

        // latestUpdateInfo
        monitoring.setLatestUpdateInfo(loggingInfo);

        Resource existing = whereID(monitoring.getId(), true);
        MonitoringBundle ex = deserialize(existing);
        monitoring.setActive(ex.isActive());
        existing.setPayload(serialize(monitoring));
        existing.setResourceType(resourceType);
        resourceService.updateResource(existing);
        logger.debug("Updating Monitoring: {}", monitoring);

        //TODO: send emails
        jmsTopicTemplate.convertAndSend("monitoring.update", monitoring);

        return monitoring;
    }

    @CacheEvict(value = CACHE_MONITORINGS, allEntries = true)
    public void delete(MonitoringBundle monitoring, Authentication auth) {
        logger.trace("User '{}' is attempting to delete the Monitoring with id '{}'", auth, monitoring.getId());

        super.delete(monitoring);
        logger.debug("Deleting Monitoring: {}", monitoring);

        //TODO: send emails
        jmsTopicTemplate.convertAndSend("monitoring.delete", monitoring);

    }

    public void serviceConsistency(String serviceId, String catalogueId){
        // check if Service exists
        try{
            infraServiceService.get(serviceId, catalogueId);
        } catch(ResourceNotFoundException e){
            throw new ValidationException(String.format("There is no Service with id '%s' in the '%s' Catalogue", serviceId, catalogueId));
        }
    }

    public void serviceTypeValidation(Monitoring monitoring){
        List<String> serviceTypeList = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-api-key", "71553f86c28d296daa4b997bd140015cdeacdb66659aae8b2661c098235ef5ff");
        headers.add("Accept", "application/json");
        String url = "https://api.devel.argo.grnet.gr/api/v2/topology/service-types";
        HttpEntity<String> entity = new HttpEntity<>("body", headers);
        String response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
        JSONObject obj = new JSONObject(response);
        JSONArray arr =  obj.getJSONArray("data");
        for (int i = 0; i < arr.length(); i++)
        {
            serviceTypeList.add(arr.getJSONObject(i).getString("name"));
        }
        for (MonitoringGroup monitoringGroup : monitoring.getMonitoringGroups()){
            String serviceType = monitoringGroup.getServiceType();
            if (!serviceTypeList.contains(serviceType)){
                throw new ValidationException(String.format("The serviceType you provided is wrong. Available serviceTypes are: '%s'", serviceTypeList));
            }
        }
    }
}
