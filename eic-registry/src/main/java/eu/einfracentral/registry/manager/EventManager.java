package eu.einfracentral.registry.manager;

import com.google.i18n.phonenumbers.NumberParseException;
import eu.einfracentral.domain.Event;
import eu.einfracentral.domain.InfraService;
import eu.einfracentral.exception.ValidationException;
import eu.einfracentral.registry.service.EventService;
import eu.einfracentral.registry.service.InfraServiceService;
import eu.einfracentral.utils.AuthenticationInfo;
import eu.openminted.registry.core.domain.FacetFilter;
import eu.openminted.registry.core.domain.Paging;
import eu.openminted.registry.core.domain.Resource;
import eu.openminted.registry.core.exception.ResourceNotFoundException;
import eu.openminted.registry.core.service.ParserService;
import eu.openminted.registry.core.service.SearchService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.stream.Collectors;

import static eu.einfracentral.config.CacheConfig.CACHE_EVENTS;
import static eu.einfracentral.config.CacheConfig.CACHE_SERVICE_EVENTS;


@Component
public class EventManager extends ResourceManager<Event> implements EventService {

    private static final Logger logger = LogManager.getLogger(EventManager.class);
    private ParserService parserService;
    private InfraServiceService<InfraService, InfraService> infraServiceService;

    @Autowired
    public EventManager(ParserService parserService,
                        @Lazy InfraServiceService<InfraService, InfraService> infraServiceService) {
        super(Event.class);
        this.parserService = parserService;
        this.infraServiceService = infraServiceService;
    }

    @Scheduled(cron = "0 0 1 * * *")
    private void deleteNullEvents() {
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(maxQuantity);
        List<Event> events = getAll(ff, null).getResults();
        List<Event> toDelete = new ArrayList<>();
        for (Event event : events) {
            if (event.getValue() == null) {
                toDelete.add(event);
                logger.debug("Null event to delete: {}", event);
            }
        }
        int size = toDelete.size();
        deleteEvents(toDelete);
        logger.info("Deleted {} null events", size);
    }

    @Override
    public String getResourceType() {
        return "event";
    }

    @Override
    public void deleteEvents(List<Event> events) {
        if (!events.isEmpty()) {
            for (Event event : events) {
                this.delete(event);
                logger.info("Deleting Event:\n-id: {}\n-Service: {}\n-Type: {}", event.getId(), event.getService(), event.getType());
            }
        }
    }

    @Override
    @CacheEvict(value = {CACHE_EVENTS, CACHE_SERVICE_EVENTS}, allEntries = true)
    public Event add(Event event, Authentication auth) {
        event.setId(UUID.randomUUID().toString());
        event.setInstant(System.currentTimeMillis());
        if (auth != null) {
            event.setUser(AuthenticationInfo.getSub(auth));
        } else {
            event.setUser("-");
        }
        Event ret = super.add(event, auth);
        logger.debug("Adding Event: {}", event);
        return ret;
    }

    @Override
    @CacheEvict(value = {CACHE_EVENTS, CACHE_SERVICE_EVENTS}, allEntries = true)
    public Event update(Event event, Authentication auth) {
        event.setInstant(System.currentTimeMillis());
        Event ret = super.update(event, auth);
        logger.debug("Updating Event: {}", event);
        return ret;
    }

    @Override
    @CacheEvict(value = {CACHE_EVENTS, CACHE_SERVICE_EVENTS}, allEntries = true)
    public Event setFavourite(String serviceId, Float value, Authentication authentication) throws ResourceNotFoundException {
        if (!infraServiceService.exists(new SearchService.KeyValue("infra_service_id", serviceId))) {
            throw new ResourceNotFoundException("infra_service", serviceId);
        }
        if (value != 1 && value != 0) {
            throw new ValidationException("Values of Favoring range between 0 - Unfavorite and 1 - Favorite");
        }
        List<Event> events = getEvents(Event.UserActionType.FAVOURITE.getKey(), serviceId, authentication);
        Event event;
        if (!events.isEmpty() && sameDay(events.get(0).getInstant())) {
            event = events.get(0);
            delete(event);
            logger.debug("Deleting previous FAVORITE Event '{}' because it happened more than once in the same day.", event);
        }
        event = new Event();
        event.setService(serviceId);
        event.setUser(AuthenticationInfo.getSub(authentication));
        event.setType(Event.UserActionType.FAVOURITE.getKey());
        event.setValue(value);
        event = add(event, authentication); // remove auth
        logger.debug("Adding a new FAVORITE Event: {}", event);
        return event;
    }

    @Override
    @CacheEvict(value = {CACHE_EVENTS, CACHE_SERVICE_EVENTS}, allEntries = true)
    public Event setRating(String serviceId, Float value, Authentication authentication) throws ResourceNotFoundException, NumberParseException {
        if (!infraServiceService.exists(new SearchService.KeyValue("infra_service_id", serviceId))) {
            throw new ResourceNotFoundException("infra_service", serviceId);
        }
        if (value <= 0 || value > 5) {
            throw new ValidationException("Values of Rating range between 0 and 5");
        }
        List<Event> events = getEvents(Event.UserActionType.RATING.getKey(), serviceId, authentication);
        Event event;
        if (!events.isEmpty() && sameDay(events.get(0).getInstant())) {
            event = events.get(0);
            event.setValue(value);
            event = update(event, authentication);
            logger.debug("Updating RATING Event: {}", event);
            //
        } else {
            event = new Event();
            event.setService(serviceId);
            event.setUser(AuthenticationInfo.getSub(authentication));
            event.setType(Event.UserActionType.RATING.getKey());
            event.setValue(value);
            event = add(event, authentication); //remove auth
            logger.debug("Adding a new RATING Event: {}", event);
        }
        return event;
    }

    @Override
    public List<Event> getEvents(String eventType) {
        Paging<Resource> eventResources = searchService
                .cqlQuery(String.format("type=\"%s\"", eventType), getResourceType(),
                        maxQuantity, 0, "creation_date", "DESC");
        return pagingToList(eventResources);
    }

    @Override
    @Cacheable(value = CACHE_EVENTS)
    public List<Event> getEvents(String eventType, String serviceId, Authentication authentication) {
        if (authentication == null) {
            return new ArrayList<>();
        }
        Paging<Resource> eventResources = searchService.cqlQuery(
                String.format("type=\"%s\" AND service=\"%s\" AND event_user=\"%s\"",
                        eventType, serviceId, AuthenticationInfo.getSub(authentication)), getResourceType(),
                maxQuantity, 0, "creation_date", "DESC");
        return pagingToList(eventResources);
    }

    @Override
    @Cacheable(value = CACHE_EVENTS)
    public List<Event> getServiceEvents(String eventType, String serviceId) {
        Paging<Resource> eventResources = searchService.cqlQuery(String.format("type=\"%s\" AND service=\"%s\"",
                eventType, serviceId), getResourceType(), maxQuantity, 0, "creation_date", "DESC");
        return pagingToList(eventResources);
    }

    @Override
    @Cacheable(value = CACHE_EVENTS)
    public List<Event> getUserEvents(String eventType, Authentication authentication) {
        if (authentication == null) {
            return new ArrayList<>();
        }
        Paging<Resource> eventResources = searchService.cqlQuery(String.format("type=\"%s\" AND event_user=\"%s\"",
                eventType, AuthenticationInfo.getSub(authentication)), getResourceType(),
                maxQuantity, 0, "creation_date", "DESC");
        return pagingToList(eventResources);
    }

    @Override
    @Cacheable(value = CACHE_SERVICE_EVENTS)
    public Map<String, List<Float>> getAllServiceEventValues(String eventType, Authentication authentication) {
        Map<String, List<Float>> allServiceEvents = new HashMap<>();
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(maxQuantity);
        ff.addFilter("type", eventType);
        Map<String, Object> order = new HashMap<>();
        Map<String, Object> sort = new HashMap<>();
        order.put("order", "desc");
        sort.put("instant", order);
        ff.setOrderBy(sort);
        List<Event> events = getAll(ff, authentication).getResults();
        List<String> serviceList = events.stream().map(Event::getService).distinct().collect(Collectors.toList());

        // for each service
        for (String service : serviceList) {
            List<Event> serviceEvents = events.stream()
                    .filter(e -> e.getService().equals(service))
                    .collect(Collectors.toList());

            Map<String, Event> userEventsMap = new HashMap<>();

            // for each event, save only the latest user events (events order is descending on field 'instant')
            for (Event event : serviceEvents) {
                userEventsMap.putIfAbsent(event.getUser(), event);
            }

            List<Float> eventsValues = userEventsMap.values()
                    .stream()
                    .map(Event::getValue)
                    .collect(Collectors.toList());
            allServiceEvents.put(service, eventsValues);
        }

        return allServiceEvents;
    }

    private List<Event> pagingToList(Paging<Resource> resources) {
        return resources.getResults()
                .stream()
                .map(resource -> parserService.deserialize(resource, Event.class))
                .collect(Collectors.toList());
    }

    private boolean sameDay(Long instant) {
        Calendar midnight = new GregorianCalendar();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        return midnight.getTimeInMillis() < instant;
    }

    public void addVisitsOnDay(Date date, String serviceId, Float noOfVisits, Authentication authentication) {
        List<Event> serviceEvents = getServiceEvents(Event.UserActionType.VISIT.toString(), serviceId);
        for (Event event : serviceEvents) {

            // Compare the event.getInstant(long) to user's give date
            Date eventDate = new Date(event.getInstant());
            Calendar cal1 = Calendar.getInstance();
            Calendar cal2 = Calendar.getInstance();
            cal1.setTime(date);
            cal2.setTime(eventDate);
            boolean sameDay = cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                    cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);

            if (event.getType().equals("AGGREGATED_VISITS") && sameDay) {
                Float oldVisits = event.getValue();
                Float newVisits = oldVisits + noOfVisits;
                event.setValue(newVisits);
                update(event, authentication);
            } else {
                logger.info("Event isn't of type {AGGREGATED_VISITS} and/or didn't happen on the given date.");
            }
        }
    }

    @CacheEvict(value = {CACHE_EVENTS, CACHE_SERVICE_EVENTS}, allEntries = true)
    public Event setVisit(String serviceId, Float value) throws ResourceNotFoundException {
        if (!infraServiceService.exists(new SearchService.KeyValue("infra_service_id", serviceId))) {
            throw new ResourceNotFoundException("infra_service", serviceId);
        }
        Event event;
        event = new Event();
        event.setService(serviceId);
        event.setType(Event.UserActionType.VISIT.getKey());
        event.setValue(value);
        event = add(event, null); // remove auth
        logger.debug("Adding a new VISIT Event: {}", event);
        return event;
    }

    @CacheEvict(value = {CACHE_EVENTS, CACHE_SERVICE_EVENTS}, allEntries = true)
    public Event setAddToProject(String serviceId, Float value) throws ResourceNotFoundException {
        if (!infraServiceService.exists(new SearchService.KeyValue("infra_service_id", serviceId))) {
            throw new ResourceNotFoundException("infra_service", serviceId);
        }
        Event event;
        event = new Event();
        event.setService(serviceId);
        event.setType(Event.UserActionType.ADD_TO_PROJECT.getKey());
        event.setValue(value);
        event = add(event, null); // remove auth
        logger.debug("Adding a new ADD_TO_PROJECT Event: {}", event);
        return event;
    }

    @CacheEvict(value = {CACHE_EVENTS, CACHE_SERVICE_EVENTS}, allEntries = true)
    public Event setOrder(String serviceId, Float value) throws ResourceNotFoundException {
        if (!infraServiceService.exists(new SearchService.KeyValue("infra_service_id", serviceId))) {
            throw new ResourceNotFoundException("infra_service", serviceId);
        }
        Event event;
        event = new Event();
        event.setService(serviceId);
        event.setType(Event.UserActionType.ORDER.getKey());
        event.setValue(value);
        event = add(event, null); // remove auth
        logger.debug("Adding a new ORDER Event: {}", event);
        return event;
    }

    @CacheEvict(value = {CACHE_EVENTS, CACHE_SERVICE_EVENTS}, allEntries = true)
    public Event setScheduledFavourite(String serviceId, Float value) throws ResourceNotFoundException {
        if (!infraServiceService.exists(new SearchService.KeyValue("infra_service_id", serviceId))) {
            throw new ResourceNotFoundException("infra_service", serviceId);
        }
        Event event;
        event = new Event();
        event.setService(serviceId);
        event.setType(Event.UserActionType.FAVOURITE.getKey());
        event.setValue(value);
        event = add(event, null); // remove auth
        logger.debug("Adding a new FAVOURITE Event: {}", event);
        return event;
    }

    @CacheEvict(value = {CACHE_EVENTS, CACHE_SERVICE_EVENTS}, allEntries = true)
    public Event setScheduledRating(String serviceId, Float value) throws ResourceNotFoundException {
        if (!infraServiceService.exists(new SearchService.KeyValue("infra_service_id", serviceId))) {
            throw new ResourceNotFoundException("infra_service", serviceId);
        }
        Event event;
        event = new Event();
        event.setService(serviceId);
        event.setType(Event.UserActionType.RATING.getKey());
        event.setValue(value);
        event = add(event, null); // remove auth
        logger.debug("Adding a new RATING Event: {}", event);
        return event;
    }

    public int getServiceAggregatedVisits(String id) {
        int result = 0;
        List<Event> serviceAggregatedInternals = getServiceEvents(Event.UserActionType.VISIT.getKey(), id);
        for (Event event : serviceAggregatedInternals) {
            result += event.getValue();
        }
        return result;
    }

    public int getServiceAggregatedAddToProject(String id) {
        int result = 0;
        List<Event> serviceAggregatedInternals = getServiceEvents(Event.UserActionType.ADD_TO_PROJECT.getKey(), id);
        for (Event event : serviceAggregatedInternals) {
            result += event.getValue();
        }
        return result;
    }

    public int getServiceAggregatedOrders(String id) {
        int result = 0;
        List<Event> serviceAggregatedInternals = getServiceEvents(Event.UserActionType.ORDER.getKey(), id);
        for (Event event : serviceAggregatedInternals) {
            result += event.getValue();
        }
        return result;
    }

}