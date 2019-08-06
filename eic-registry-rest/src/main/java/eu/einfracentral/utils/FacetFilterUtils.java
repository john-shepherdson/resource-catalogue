package eu.einfracentral.utils;

import eu.openminted.registry.core.domain.FacetFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.MultiValueMap;

import java.util.*;
import java.util.stream.Collectors;

public class FacetFilterUtils {

    private static final Logger logger = LogManager.getLogger(FacetFilterUtils.class);

    private FacetFilterUtils() {}

    // Gets all given filters
    public static Map<String, List<String>> getFacetFilterFilters(FacetFilter ff) {
        Map<String, Object> filters = ff.getFilter();
        Map<String, List<String>> allFilters = new HashMap<>();

        // check if a MultiValueMap filter exists inside the filter
        if (filters.get("multi-filter") != null) {
            MultiValueMap<String, String> multiFilter = (MultiValueMap<String, String>) filters.remove("multi-filter");

            for (Map.Entry<String, List<String>> entry : multiFilter.entrySet()) {
                // fill the variable with the multiple filters
                allFilters.put(entry.getKey(), entry.getValue());
            }
        }

        // fill the variable with the rest of the filters
        for (Map.Entry<String, Object> ffEntry : filters.entrySet()) {
            allFilters.put(ffEntry.getKey(), Collections.singletonList(ffEntry.getValue().toString()));
        }

        return allFilters;
    }

    // Creates a Query consisted of all given filters and keywords
    public static String createQuery(Map<String, List<String>> filters, String keyword) {
        List<String> searchFields = filters.remove("searchFields");
        if (searchFields == null || searchFields.isEmpty()) {
            searchFields = Collections.singletonList("searchableArea");
        }
        final List<String> fields = searchFields;
        StringBuilder query = new StringBuilder();

        if (keyword != null && !keyword.replaceAll(" ", "").equals("")) {
            String keywordQuery;
            List<String> searchKeywords = Arrays.asList(keyword.split(" "));
            List<String> allSearchKeywords = new ArrayList<>();
            // filter search keywords, trim whitespace and create search statements
            for (String f : fields) {
                allSearchKeywords.addAll(searchKeywords
                        .stream()
                        .map(k -> k.replaceAll(" ", ""))
                        .filter(k -> !k.equals(""))
                        .map(k -> String.format("%s=%s", f, k))
                        .collect(Collectors.toList()));
            }
            keywordQuery = String.join(" OR ", allSearchKeywords);
            query.append(String.format("( %s )", keywordQuery));

            if (!filters.isEmpty()) {
                query.append(" AND ");
            }
        }

        for (Iterator iter = filters.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<String, List<String>> filter = (Map.Entry<String, List<String>>) iter.next();
            List<String> entries = new ArrayList<>();
            filter.getValue().forEach(e -> entries.add(String.format("%s=%s", filter.getKey(), e)));
            if (entries.size() > 1) {
                query.append(String.format("( %s )", String.join(" OR ", entries)));
            } else { // this is important to skip adding parentheses when we have zero or only 1 filter
                query.append(String.join("", entries));
            }

            if (iter.hasNext()) {
                query.append(" AND ");
            }
        }

        return query.toString();
    }

    public static FacetFilter createMultiFacetFilter(MultiValueMap<String, Object> allRequestParams) {
        logger.debug("Request params: " + allRequestParams);
        FacetFilter facetFilter = new FacetFilter();
        facetFilter.setKeyword(allRequestParams.get("query") != null ? (String) allRequestParams.remove("query").get(0) : "");
        facetFilter.setFrom(allRequestParams.get("from") != null ? Integer.parseInt((String) allRequestParams.remove("from").get(0)) : 0);
        facetFilter.setQuantity(allRequestParams.get("quantity") != null ? Integer.parseInt((String) allRequestParams.remove("quantity").get(0)) : 10);
        Map<String, Object> sort = new HashMap<>();
        Map<String, Object> order = new HashMap<>();
        String orderDirection = allRequestParams.get("order") != null ? (String) allRequestParams.remove("order").get(0) : "asc";
        String orderField = allRequestParams.get("orderField") != null ? (String) allRequestParams.remove("orderField").get(0) : null;
        if (orderField != null) {
            order.put("order", orderDirection);
            sort.put(orderField, order);
            facetFilter.setOrderBy(sort);
        }
        if (!allRequestParams.isEmpty()) {
            Set<Map.Entry<String, List<Object>>> filterSet = allRequestParams.entrySet();
            for (Map.Entry<String, List<Object>> entry : filterSet) {
                // split values separated by comma to entries and replace existing <key,value> pair with the new one
                allRequestParams.replace(entry.getKey(), new LinkedList<>(
                        entry.getValue()
                                .stream()
                                .flatMap(e -> Arrays.stream(e.toString().split(",")))
                                .distinct()
                                .collect(Collectors.toList()))
                );
            }
            Map<String, Object> multiFilter = new HashMap<>();
            multiFilter.put("multi-filter", allRequestParams);
            facetFilter.setFilter(multiFilter);
        }
        return facetFilter;
    }
}
