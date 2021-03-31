package eu.einfracentral.manager;

import eu.einfracentral.domain.Event;
import eu.einfracentral.domain.InfraService;
import eu.einfracentral.domain.ProviderBundle;
import eu.einfracentral.domain.Service;
import eu.einfracentral.dto.MapValues;
import eu.einfracentral.dto.PlaceCount;
import eu.einfracentral.dto.Value;
import eu.einfracentral.registry.manager.InfraServiceManager;
import eu.einfracentral.registry.service.ProviderService;
import eu.einfracentral.registry.service.VocabularyService;
import eu.einfracentral.service.AnalyticsService;
import eu.einfracentral.service.StatisticsService;
import eu.openminted.registry.core.domain.FacetFilter;
import eu.openminted.registry.core.domain.Paging;
import eu.openminted.registry.core.domain.Resource;
import eu.openminted.registry.core.service.ParserService;
import eu.openminted.registry.core.service.SearchService;
import eu.openminted.registry.core.service.ServiceException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.PipelineAggregatorBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.pipeline.SimpleValue;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTime;
import org.postgresql.jdbc.PgArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static eu.einfracentral.config.CacheConfig.CACHE_VISITS;

@Component
@EnableScheduling
public class StatisticsManager implements StatisticsService {

    private static final Logger logger = LogManager.getLogger(StatisticsManager.class);
    private final RestHighLevelClient client;
    private final AnalyticsService analyticsService;
    private final ProviderService<ProviderBundle, Authentication> providerService;
    private final SearchService searchService;
    private final ParserService parserService;
    private final InfraServiceManager infraServiceManager;
    private final VocabularyService vocabularyService;
    private final DataSource dataSource;

    @org.springframework.beans.factory.annotation.Value("${platform.root:}")
    String url;

    @Autowired
    StatisticsManager(RestHighLevelClient client, AnalyticsService analyticsService,
                      ProviderService<ProviderBundle, Authentication> providerService,
                      SearchService searchService, ParserService parserService,
                      InfraServiceManager infraServiceManager, VocabularyService vocabularyService,
                      DataSource dataSource) {
        this.client = client;
        this.analyticsService = analyticsService;
        this.providerService = providerService;
        this.searchService = searchService;
        this.parserService = parserService;
        this.infraServiceManager = infraServiceManager;
        this.vocabularyService = vocabularyService;
        this.dataSource = dataSource;
    }

    @Override
    public Map<String, Float> ratings(String id, Interval by) {

        String dateFormat;
        String aggregationName;
        DateHistogramInterval dateHistogramInterval;

        switch (StatisticsService.Interval.fromString(by.getKey())) {
            case DAY:
                dateFormat = "yyyy-MM-dd";
                aggregationName = "day";
                dateHistogramInterval = DateHistogramInterval.DAY;
                break;
            case WEEK:
                dateFormat = "yyyy-MM-dd";
                aggregationName = "week";
                dateHistogramInterval = DateHistogramInterval.WEEK;
                break;
            case YEAR:
                dateFormat = "yyyy";
                aggregationName = "year";
                dateHistogramInterval = DateHistogramInterval.YEAR;
                break;
            default:
                dateFormat = "yyyy-MM";
                aggregationName = "month";
                dateHistogramInterval = DateHistogramInterval.MONTH;
        }

        DateHistogramAggregationBuilder dateHistogramAggregationBuilder = AggregationBuilders.dateHistogram(aggregationName)
                .field("instant")
                .calendarInterval(dateHistogramInterval)
                .format(dateFormat)
                .subAggregation(AggregationBuilders.sum("rating").field("value"))
                .subAggregation(AggregationBuilders.count("rating_count").field("value"))
                .subAggregation(PipelineAggregatorBuilders.cumulativeSum("cum_sum", "rating"))
                .subAggregation(PipelineAggregatorBuilders.cumulativeSum("ratings_num", "rating_count"));

        SearchRequest search = new SearchRequest("event");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        search.searchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchSourceBuilder.query(getEventQueryBuilder(id, Event.UserActionType.RATING.getKey()));
        searchSourceBuilder.aggregation(dateHistogramAggregationBuilder);
        search.source(searchSourceBuilder);

        SearchResponse response = null;
        try {
            response = client.search(search, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ServiceException(e.getMessage());
        }

        List<? extends Histogram.Bucket> bucketsDay = ((ParsedDateHistogram) response
                .getAggregations()
                .get(aggregationName))
                .getBuckets();

        Map<String, Float> bucketMap = bucketsDay.stream().collect(Collectors.toMap(
                MultiBucketsAggregation.Bucket::getKeyAsString,
                e -> Float.parseFloat(((SimpleValue) e.getAggregations().get("cum_sum")).getValueAsString()) / Float.parseFloat(((SimpleValue) e.getAggregations().get("ratings_num")).getValueAsString())
        ));

        return new TreeMap<>(bucketMap);
    }

    @Override
    public Map<String, Integer> favourites(String id, Interval by) {
        final long[] totalDocCounts = new long[2]; //0 - false documents, ie unfavourites, 1 - true documents, ie favourites
        List<? extends Histogram.Bucket> buckets = histogram(id, Event.UserActionType.FAVOURITE.getKey(), by).getBuckets();
        return new TreeMap<>(buckets.stream().collect(
                Collectors.toMap(
                        MultiBucketsAggregation.Bucket::getKeyAsString,
                        bucket -> {
                            Terms subTerm = bucket.getAggregations().get("value");
                            if (subTerm.getBuckets() != null) {
                                totalDocCounts[0] += subTerm.getBuckets().stream().mapToLong(
                                        subBucket -> subBucket.getKeyAsNumber().intValue() == 0 ? subBucket.getDocCount() : 0
                                ).sum();
                                totalDocCounts[1] += subTerm.getBuckets().stream().mapToLong(
                                        subBucket -> subBucket.getKeyAsNumber().intValue() == 1 ? subBucket.getDocCount() : 0
                                ).sum();
                            }
//                            logger.warn(String.format("Favs: %s - Unfavs: %s", totalDocCounts[1], totalDocCounts[0]));
                            return (int) Math.max(totalDocCounts[1] - totalDocCounts[0], 0);
                        }
                )
        ));
    }

    private ParsedDateHistogram histogram(String id, String eventType, Interval by) {

        String dateFormat;
        String aggregationName;
        DateHistogramInterval dateHistogramInterval;

        switch (StatisticsService.Interval.fromString(by.getKey())) {
            case DAY:
                dateFormat = "yyyy-MM-dd";
                aggregationName = "day";
                dateHistogramInterval = DateHistogramInterval.DAY;
                break;
            case WEEK:
                dateFormat = "yyyy-MM-dd";
                aggregationName = "week";
                dateHistogramInterval = DateHistogramInterval.WEEK;
                break;
            case YEAR:
                dateFormat = "yyyy";
                aggregationName = "year";
                dateHistogramInterval = DateHistogramInterval.YEAR;
                break;
            default:
                dateFormat = "yyyy-MM";
                aggregationName = "month";
                dateHistogramInterval = DateHistogramInterval.MONTH;
        }

        DateHistogramAggregationBuilder dateHistogramAggregationBuilder = AggregationBuilders
                .dateHistogram(aggregationName)
                .field("instant")
                .calendarInterval(dateHistogramInterval)
                .format(dateFormat)
                .subAggregation(AggregationBuilders.terms("value").field("value"));

        SearchRequest search = new SearchRequest("event");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        search.searchType(SearchType.DEFAULT);
        searchSourceBuilder.query(getEventQueryBuilder(id, eventType));
        searchSourceBuilder.aggregation(dateHistogramAggregationBuilder);
        search.source(searchSourceBuilder);

        SearchResponse response = null;
        try {
            response = client.search(search, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ServiceException(e.getMessage());
        }

        return response
                .getAggregations()
                .get(aggregationName);
    }

    private QueryBuilder getEventQueryBuilder(String serviceId, String eventType) {
        Date date = new Date();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(0);
        return QueryBuilders.boolQuery()
                .filter(QueryBuilders.termsQuery("service", serviceId))
                .filter(QueryBuilders.rangeQuery("instant").from(c.getTime().getTime()).to(date.getTime()))
                .filter(QueryBuilders.termsQuery("type", eventType));
    }

    @Override
    public Map<String, Float> providerRatings(String id, Interval by) {
        Map<String, Float> providerRatings = providerService.getServices(id)
                .stream()
                .flatMap(s -> ratings(s.getId(), by).entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.averagingDouble(e -> (double) e.getValue())))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> (float) v.getValue().doubleValue()));
        //The above 4 lines should be just
        //.collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingFloat(Map.Entry::getValue)));
        //but Collectors don't offer a summingFloat for some reason
        //if they ever offer that, you know what to do

        return new TreeMap<>(providerRatings);
    }

    @Override
    public Map<String, Integer> providerFavourites(String id, Interval by) {
        Map<String, Integer> providerFavorites = providerService.getServices(id)
                .stream()
                .flatMap(s -> favourites(s.getId(), by).entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)));

        return new TreeMap<>(providerFavorites);
    }

    @Override
    public Map<String, Integer> addToProject(String id, Interval by) {
        final long[] totalDocCounts = new long[2]; //0 - not added, 1 - added
        List<? extends Histogram.Bucket> buckets = histogram(id, Event.UserActionType.ADD_TO_PROJECT.getKey(), by).getBuckets();
        return new TreeMap<>(buckets.stream().collect(
                Collectors.toMap(
                        MultiBucketsAggregation.Bucket::getKeyAsString,
                        bucket -> {
                            Terms subTerm = bucket.getAggregations().get("value");
                            if (subTerm.getBuckets() != null) {
                                totalDocCounts[0] += subTerm.getBuckets().stream().mapToLong(
                                        subBucket -> subBucket.getKeyAsNumber().intValue() == 0 ? subBucket.getDocCount() : 0
                                ).sum();
                                totalDocCounts[1] += subTerm.getBuckets().stream().mapToLong(
                                        subBucket -> subBucket.getKeyAsNumber().intValue() == 1 ? subBucket.getDocCount() : 0
                                ).sum();
                            }
                            return (int) Math.max(totalDocCounts[1] - totalDocCounts[0], 0);
                        }
                )
        ));
    }

    @Override
    public Map<String, Integer> providerAddToProject(String id, Interval by) {
        Map<String, Integer> providerAddToProject = providerService.getServices(id)
                .stream()
                .flatMap(s -> addToProject(s.getId(), by).entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)));

        return new TreeMap<>(providerAddToProject);
    }

    @Override
    @Cacheable(cacheNames = CACHE_VISITS, key = "#id+#by.getKey()")
    public Map<String, Integer> visits(String id, Interval by) {
        final long[] totalDocCounts = new long[1];
        List<? extends Histogram.Bucket> buckets = histogram(id, Event.UserActionType.VISIT.getKey(), by).getBuckets();
        return new TreeMap<>(buckets.stream().collect(
                Collectors.toMap(
                        MultiBucketsAggregation.Bucket::getKeyAsString,
                        bucket -> {
                            Terms subTerm = bucket.getAggregations().get("value");
                            if (subTerm.getBuckets() != null) {
                                for (int i=0; i<subTerm.getBuckets().size(); i++){
                                    Double key = (Double)subTerm.getBuckets().get(i).getKey();
                                    Integer keyToInt = key.intValue();
                                    int totalVistisOnBucket = keyToInt * Integer.parseInt(String.valueOf(subTerm.getBuckets().get(i).getDocCount()));
                                    totalDocCounts[0] += totalVistisOnBucket;
                                }
                            }
                            return (int) Math.max(totalDocCounts[0], 0);
                        }
                )
        ));

        // alternatively - fetching data from matomo
//        try {
//            return analyticsService.getVisitsForLabel("/service/" + id, by);
//        } catch (Exception e) {
//            logger.error("Could not find Matomo analytics", e);
//        }
//        return new HashMap<>();
    }

    @Override
    public Map<String, Integer> providerVisits(String id, Interval by) {
        Map<String, Integer> results = new HashMap<>();
        for (Service service : providerService.getServices(id)){
            Set<Map.Entry<String, Integer>> entrySet = visits(service.getId(),by).entrySet();
            for (Map.Entry<String, Integer> entry : entrySet){
                if (!results.containsKey(entry.getKey())){
                    results.put(entry.getKey(), entry.getValue());
                } else {
                    results.put(entry.getKey(), results.get(entry.getKey())+entry.getValue());
                }
            }
        }
        return results;
    }

    @Override
    public Map<String, Float> providerVisitation(String id, Interval by) {
        Map<String, Integer> counts = providerService.getServices(id).stream().collect(Collectors.toMap(
                Service::getName,
                s -> visits(s.getId(), by).values().stream().mapToInt(Integer::intValue).sum()
        ));
        int grandTotal = counts.values().stream().mapToInt(Integer::intValue).sum();
        return counts.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v -> ((float) v.getValue()) / grandTotal));
    }

    public Map<DateTime, Map<String, Long>> events(Event.UserActionType type, Date from, Date to, Interval by) {
        Map<DateTime, Map<String, Long>> results = new LinkedHashMap<>();
        Paging<Resource> resources = searchService.cqlQuery(
                String.format("type=\"%s\" AND creation_date > %s AND creation_date < %s",
                        type, from.toInstant().toEpochMilli(), to.toInstant().toEpochMilli()), "event",
                10000, 0, "creation_date", "ASC");
        List<Event> events = resources
                .getResults()
                .stream()
                .map(resource -> parserService.deserialize(resource, Event.class))
                .collect(Collectors.toList());


        DateTime start = new DateTime(from);
        DateTime stop = new DateTime(to);

        Map<DateTime, List<Event>> eventsByDate = new LinkedHashMap<>();

        start.plusWeeks(1);
        while (start.getMillis() <= stop.getMillis()) {
            DateTime endDate = addInterval(start, by);
            List<Event> weekEvents = new LinkedList<>();

            events = events
                    .stream()
                    .map(event -> {
                        if (endDate.isAfter(event.getInstant())) {
                            weekEvents.add(event);
                            return null;
                        } else
                            return event;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
//            weekEvents.sort(Comparator.comparing(Event::getService));
            eventsByDate.put(start, weekEvents);
            start = endDate;
        }

        for (Map.Entry<DateTime, List<Event>> weekEntry : eventsByDate.entrySet()) {
            Map<String, Long> weekResults = weekEntry.getValue()
                    .stream()
                    .collect(Collectors.groupingBy(Event::getService, Collectors.counting()));

            weekResults = weekResults.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, LinkedHashMap::new));

            results.put(weekEntry.getKey(), weekResults);
        }


        return results;

    }

    private DateTime addInterval(DateTime date, Interval by) {
        DateTime duration;
        switch (by) {
            case DAY:
                duration = date.plusDays(1);
                break;
            case WEEK:
                duration = date.plusWeeks(1);
                break;
            case MONTH:
                duration = date.plusMonths(1);
                break;
            case YEAR:
                duration = date.plusYears(1);
                break;
            default:
                duration = date;
        }
        return duration;
    }

    @Override
    public List<PlaceCount> servicesPerPlace(String providerId) {
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        MapSqlParameterSource in = new MapSqlParameterSource();

        in.addValue("resource_organisation", providerId);
        String query = "SELECT unnest(geographical_availabilities) AS geographical_availability, count(unnest(geographical_availabilities)) AS count FROM infra_service_view WHERE latest=true AND active = true ";

        if (providerId != null) {
            query += " AND :resource_organisation=resource_organisation";
        }
        query += " GROUP BY unnest(geographical_availabilities);";

        List<Map<String, Object>> records = namedParameterJdbcTemplate.queryForList(query, in);
        Map<String, Integer> mapCounts = new HashMap<>();
        List<PlaceCount> placeCounts = new ArrayList<>();

        for (Map<String, Object> record : records) {
            if (record.get("geographical_availability").toString().equalsIgnoreCase("EU")) {
                for (String geographical_availability : vocabularyService.getRegion("EU")) {
                    int count = Integer.parseInt(record.get("count").toString());
                    if (mapCounts.containsKey(geographical_availability)) {
                        count += mapCounts.get(geographical_availability);
                    }
                    mapCounts.put(geographical_availability, count);
                }
            } else if (record.get("geographical_availability").toString().equalsIgnoreCase("WW")) {
                for (String geographical_availability : vocabularyService.getRegion("WW")) {
                    int count = Integer.parseInt(record.get("count").toString());
                    if (mapCounts.containsKey(geographical_availability)) {
                        count += mapCounts.get(geographical_availability);
                    }
                    mapCounts.put(geographical_availability, count);
                }
            } else {
                mapCounts.put(record.get("geographical_availability").toString(), Integer.parseInt(record.get("count").toString()));
            }
        }

        for (Map.Entry<String, Integer> entry : mapCounts.entrySet()) {
            placeCounts.add(new PlaceCount(entry.getKey(), entry.getValue()));
        }

        return placeCounts;
    }

    @Override
    public List<Value> servicesByPlace(String providerId, String place) {
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        MapSqlParameterSource in = new MapSqlParameterSource();

        in.addValue("resource_organisation", providerId);
        in.addValue("geographical_availabilities", place);
        String query = "SELECT infra_service_id, name FROM infra_service_view WHERE latest=true AND active=true ";

        if (providerId != null) {
            query += " AND :resource_organisation=resource_organisation";
        }

        if (place != null) {
            Set<String> geographical_availabilities = new HashSet<>(Arrays.asList(vocabularyService.getRegion("EU")));

            if (!place.equalsIgnoreCase("WW")) {
                query += " AND ( :geographical_availabilities=ANY(geographical_availabilities) ";

                // if Place belongs to EU then search for EU as well
                if (geographical_availabilities.contains(place) || place.equalsIgnoreCase("EU")) {
                    query += " OR 'EU'=ANY(geographical_availabilities) ";
                }
                // always search for WW (because every Place belongs to WW)
                query += " OR 'WW'=ANY(geographical_availabilities) )";
            }
        }

        List<Map<String, Object>> records = namedParameterJdbcTemplate.queryForList(query, in);
        List<Value> placeServices;

        placeServices = records
                .stream()
                .map(record -> new Value(record.get("infra_service_id").toString(), record.get("name").toString()))
                .collect(Collectors.toList());
        return placeServices;
    }

    @Override
    public List<MapValues> mapServicesToGeographicalAvailability(String providerId) {
        Map<String, Set<Value>> placeServices = new HashMap<>();
        String[] world = vocabularyService.getRegion("WW");
        String[] eu = vocabularyService.getRegion("EU");
        for (String place : world) {
            placeServices.put(place, new HashSet<>());
        }
        placeServices.put("OT", new HashSet<>());
        placeServices.put("EL", new HashSet<>());
        placeServices.put("UK", new HashSet<>());

        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        MapSqlParameterSource in = new MapSqlParameterSource();
        in.addValue("resource_organisation", providerId);

        String query = "SELECT infra_service_id, name, geographical_availabilities FROM infra_service_view WHERE latest=true AND active=true ";
        if (providerId != null) {
            query += " AND :resource_organisation=resource_organisation";
        }

        List<Map<String, Object>> records = namedParameterJdbcTemplate.queryForList(query, in);

        try {
            for (Map<String, Object> entry : records) {
                Value value = new Value();
                value.setId(entry.get("infra_service_id").toString());
                value.setName(entry.get("name").toString());
                PgArray pgArray = ((PgArray) entry.get("geographical_availabilities"));

                for (String place : ((String[]) pgArray.getArray())) {
                    String[] expandedPlaces;
                    if (place.equalsIgnoreCase("WW")) {
                        expandedPlaces = world;
                    } else if (place.equalsIgnoreCase("EU")) {
                        expandedPlaces = eu;
                    } else {
                        expandedPlaces = new String[]{place};
                    }
                    for (String p : expandedPlaces) {
                        try{
                            Set<Value> values = placeServices.get(p);
                            values.add(value);
                            placeServices.put(p, values);
                        } catch(NullPointerException e){
                            logger.info(p);
                        }
                    }
                }
            }
        } catch (SQLException throwables) {
            logger.error(throwables);
        }

        return toListMapValues(placeServices);
    }

    @Override
    public List<MapValues> mapServicesToProviderCountry() {
        Map<String, Set<Value>> mapValues = new HashMap<>();
        for (String place : vocabularyService.getRegion("WW")) {
            mapValues.put(place, new HashSet<>());
        }
        mapValues.put("OT", new HashSet<>());
        mapValues.put("EL", new HashSet<>());
        mapValues.put("UK", new HashSet<>());
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);

        Map<String, Set<String>> providerCountries = providerCountriesMap();

        List<InfraService> allServices = infraServiceManager.getAll(ff, null).getResults();
        for (InfraService infraService : allServices) {
            Value value = new Value(infraService.getId(), infraService.getService().getName());

            try {
                Set<String> countries = new HashSet<>(providerCountries.get(infraService.getService().getResourceOrganisation()));
                for (String country : countries) {
                    Set<Value> values = mapValues.get(country);
                    values.add(value);
                    mapValues.put(country, values);
                }
            } catch (NullPointerException e){
                logger.info(e);
            }
        }

        return toListMapValues(mapValues);
    }

    @Override
    public List<MapValues> mapServicesToVocabulary(String providerId, Vocabulary vocabulary) {
        Map<String, Set<Value>> vocabularyServices = new HashMap<>();

        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        MapSqlParameterSource in = new MapSqlParameterSource();
        in.addValue("resource_organisation", providerId);

        String query = "SELECT infra_service_id, name, " + vocabulary.getKey()
                + " FROM infra_service_view WHERE latest=true AND active=true ";
        if (providerId != null) {
            query += " AND :resource_organisation=resource_organisation";
        }

        List<Map<String, Object>> records = namedParameterJdbcTemplate.queryForList(query, in);

        try {
            for (Map<String, Object> entry : records) {
                Value value = new Value();
                value.setId(entry.get("infra_service_id").toString());
                value.setName(entry.get("name").toString());

                // TODO: refactor this code and Vocabulary enum
                String[] vocabularyValues;
                if (vocabulary != Vocabulary.ORDER_TYPE) { // because order type is not multivalued
                    PgArray pgArray = ((PgArray) entry.get(vocabulary.getKey()));
                    vocabularyValues = ((String[]) pgArray.getArray());
                } else {
                    vocabularyValues = new String[]{((String) entry.get(vocabulary.getKey()))};
                }

                for (String voc : vocabularyValues) {
                    Set<Value> values;
                    if (vocabularyServices.containsKey(voc)) {
                        values = vocabularyServices.get(voc);
                    } else {
                        values = new HashSet<>();
                    }
                    values.add(value);
                    vocabularyServices.put(voc, values);
                }
            }
        } catch (SQLException throwables) {
            logger.error(throwables);
        }

        return toListMapValues(vocabularyServices);
    }

    private Map<String, Set<String>> providerCountriesMap() {
        Map<String, Set<String>> providerCountries = new HashMap<>();
        String[] world = vocabularyService.getRegion("WW");
        String[] eu = vocabularyService.getRegion("EU");

        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);

        for (ProviderBundle providerBundle : providerService.getAll(ff, null).getResults()) {
            Set<String> countries = new HashSet<>();
            String country = providerBundle.getProvider().getLocation().getCountry();
            if (country.equalsIgnoreCase("WW")) {
                countries.addAll(Arrays.asList(world));
            } else if (country.equalsIgnoreCase("EU")) {
                countries.addAll(Arrays.asList(eu));
            } else {
                countries.add(country);
            }
            providerCountries.put(providerBundle.getId(), countries);
        }
        return providerCountries;
    }

    private List<MapValues> toListMapValues(Map<String, Set<Value>> mapSetValues) {
        List<MapValues> mapValuesList = new ArrayList<>();
        for (Map.Entry<String, Set<Value>> entry : mapSetValues.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                MapValues mapValues = new MapValues();
                mapValues.setKey(entry.getKey());
                mapValues.setValues(new ArrayList<>(entry.getValue()));
                mapValuesList.add(mapValues);
            }
        }
        return mapValuesList;
    }
}
