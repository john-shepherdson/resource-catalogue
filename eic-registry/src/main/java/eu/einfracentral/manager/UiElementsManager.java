package eu.einfracentral.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import eu.einfracentral.annotation.FieldValidation;
import eu.einfracentral.annotation.VocabularyValidation;
import eu.einfracentral.domain.DynamicField;
import eu.einfracentral.domain.InfraService;
import eu.einfracentral.domain.ProviderBundle;
import eu.einfracentral.domain.Vocabulary;
import eu.einfracentral.dto.UiService;
import eu.einfracentral.dto.Value;
import eu.einfracentral.registry.service.InfraServiceService;
import eu.einfracentral.registry.service.ProviderService;
import eu.einfracentral.registry.service.VocabularyService;
import eu.einfracentral.service.UiElementsService;
import eu.einfracentral.ui.*;
import eu.einfracentral.utils.ListUtils;
import eu.openminted.registry.core.domain.Facet;
import eu.openminted.registry.core.domain.FacetFilter;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static eu.einfracentral.config.CacheConfig.CACHE_EXTRA_FACETS;
import static eu.einfracentral.config.CacheConfig.CACHE_FOR_UI;


@Service
public class UiElementsManager implements UiElementsService {

    private static final Logger logger = Logger.getLogger(UiElementsManager.class);

    private static final String FILENAME_GROUPS = "groups.json";
    private static final String FILENAME_FIELDS = "fields.json";

    private final String directory;
    private String jsonObject;

    @org.springframework.beans.factory.annotation.Value("${elastic.index.max_result_window:10000}")
    protected int maxQuantity;

    private final VocabularyService vocabularyService;
    private final ProviderService<ProviderBundle, Authentication> providerService;
    private final InfraServiceService<InfraService, InfraService> infraServiceService;

    @Autowired
    public UiElementsManager(@org.springframework.beans.factory.annotation.Value("${ui.elements.json.dir}") String directory,
                             VocabularyService vocabularyService,
                             ProviderService<ProviderBundle, Authentication> providerService,
                             InfraServiceService<InfraService, InfraService> infraServiceService) {
        this.vocabularyService = vocabularyService;
        this.providerService = providerService;
        this.infraServiceService = infraServiceService;
        if ("".equals(directory)) {
            directory = "catalogue/uiElements";
            logger.warn("'ui.elements.json.dir' was not set. Using default: " + directory);
        }
        this.directory = directory;
        File dir = new File(directory);
        if (dir.mkdirs()) {
            logger.error("Directory for UI elements has been created. Please place the necessary files inside...");
        }
    }

    @Scheduled(fixedRate = 3600000)
    @CachePut(value = CACHE_FOR_UI)
    public Map<String, List<Value>> cacheVocabularies() {
        return getControlValuesMap();
    }

    protected String readFile(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            return sb.toString();
        }
    }

    protected List<Group> readGroups(String filepath) {
        List<Group> groups = null;
        try {
            jsonObject = readFile(filepath);
            ObjectMapper objectMapper = new ObjectMapper();
            Group[] groupsArray = objectMapper.readValue(jsonObject, Group[].class);
            groups = new ArrayList<>(Arrays.asList(groupsArray));
        } catch (IOException e) {
            logger.error(e);
        }

        return groups;
    }

    protected List<Field> readFields(String filepath) {
        List<Field> fields = null;
        try {
            jsonObject = readFile(filepath);
            ObjectMapper objectMapper = new ObjectMapper();
            Field[] fieldsArray = objectMapper.readValue(jsonObject, Field[].class);
            fields = new ArrayList<>(Arrays.asList(fieldsArray));
        } catch (IOException e) {
            logger.error(e);
        }

        return fields;
    }

    public Field getField(int id) {
        List<Field> allFields = readFields(directory + "/" + FILENAME_FIELDS);
        for (Field field : allFields) {
            if (field.getId() == id) {
                return field;
            }
        }
        return null;
    }

    private Field getExtraField(String name) {
        List<Field> allFields = getFields();
        for (Field field : allFields) {
            if (field.getParent() != null && field.getAccessPath().startsWith("extras") && name.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<DynamicField> createExtras(Map<String, ?> extraFields) {
        List<DynamicField> extras = new ArrayList<>();

        for (Map.Entry<String, ?> entry : extraFields.entrySet()) {
            DynamicField field = new DynamicField();
            field.setName(entry.getKey());
            if (!Collection.class.isAssignableFrom(entry.getValue().getClass())) {
                List<Object> temp = new ArrayList<>();
                temp.add(entry.getValue());
                field.setValue(temp);
            } else {
                if (!((List<?>) entry.getValue()).isEmpty()
                        && Map.class.isAssignableFrom(((List<?>) entry.getValue()).get(0).getClass())) {
                    List<DynamicField> subFields = new ArrayList<>();
                    for (Object item : ((List<?>) entry.getValue())) {
                        subFields.addAll(createExtras((Map<String, ?>) item));
                    }
                    field.setValue(subFields);
                } else {
                    field.setValue((List<Object>) entry.getValue());
                }
            }

            Field fieldInfo = getExtraField(entry.getKey());
            if (fieldInfo != null) {
                field.setFieldId(fieldInfo.getId());
            }

            extras.add(field);
        }
        return extras;
    }

    @Override
    public InfraService createService(UiService service) {
        List<DynamicField> extras = createExtras(service.getExtras());

        InfraService infraService = new InfraService();
        infraService.setService(service.getService());
        infraService.setExtras(extras);
        return infraService;
    }


    @Override
    public Map<String, Object> createServiceSnippet(InfraService service) {
        Map<String, Object> snippet = new HashMap<>();
        List<Field> allFields = getFields();

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> serviceMap = null;
        JsonElement el = new Gson().toJsonTree(service);
        try {
            serviceMap = mapper.readValue(new Gson().toJson(createUiService(service)), Map.class);
        } catch (JsonProcessingException e) {
            logger.error(e);
        }

        if (serviceMap != null) {
            for (Field field : allFields) {
                if (field.isIncludedInSnippet()) {
                    snippet.put(field.getName(), getMultiMap(serviceMap, field.getAccessPath()));
                }
            }
        }
        return snippet;
    }

    private Object getMultiMap(Map<String, Object> map, String path) {
        if (path != null && path.contains(".")) {
            int index = path.indexOf(".");
            String newPath = path.substring(index + 1); // +1 to exclude the . from the substring
            path = path.substring(0, index);
            Object mapValue = map.get(path);
            if (mapValue != null && List.class.isAssignableFrom(mapValue.getClass())) {
                List<Object> values = new ArrayList<>();
                for (Object item : (List<Object>) mapValue) {
                    values.add(getMultiMap((Map<String, Object>) item, newPath));
                }
                return values;
            } else {
                return getMultiMap((Map<String, Object>) mapValue, newPath);
            }
        }
        return map != null ? map.get(path) : null;
    }

    @Override
    public UiService createUiService(InfraService service) {
        UiService uiService = new UiService();
        uiService.setService(service.getService());
        uiService.setExtras(new HashMap<>());
        if (service.getExtras() != null) {
            for (DynamicField field : service.getExtras()) {
                uiService.getExtras().put(field.getName(), getFieldValues(field));
            }
        }
        return uiService;
    }

    @Override
    public Map<String, List<UiService>> getUiServicesByExtraVoc(String vocabularyType, String value) {
        Map<String, List<InfraService>> serviceMap = getServicesByExtraVoc(vocabularyType, value);
        Map<String, List<UiService>> valuesMap = new HashMap<>();

        for (Map.Entry<String, List<InfraService>> v : serviceMap.entrySet()) {
            List<UiService> services = new ArrayList<>();
            for (InfraService service : v.getValue()) {
                services.add(createUiService(service));
            }
            valuesMap.put(v.getKey(), services);
        }
        return valuesMap;
    }

    @Scheduled(initialDelay = 25000, fixedRate = 3600000)
    @Cacheable(CACHE_EXTRA_FACETS)
    @Override
    public List<Facet> createExtraFacets() {
        List<Facet> facetsList = new ArrayList<>();
        Map<Field, Vocabulary.Type> vocabularyTypes = getExtraVocabularyFieldsAndTypes();
        for (Map.Entry<Field, Vocabulary.Type> fieldTypeEntry : vocabularyTypes.entrySet()) {
            // TODO: possible optimization -> "servicesByExtraVoc = getByExtraVoc(null, null)" before loop.
            Map<Vocabulary, List<InfraService>> servicesByExtraVoc = getByExtraVoc(fieldTypeEntry.getValue().getKey(), null);
            Facet facet = new Facet();
            facet.setField(fieldTypeEntry.getKey().getName());
            facet.setLabel(fieldTypeEntry.getKey().getLabel());
            List<eu.openminted.registry.core.domain.Value> valuesList = new ArrayList<>();
            for (Map.Entry<Vocabulary, List<InfraService>> entry : servicesByExtraVoc.entrySet()) {
                eu.openminted.registry.core.domain.Value value = new eu.openminted.registry.core.domain.Value();
                value.setValue(entry.getKey().getId());
                value.setLabel(entry.getKey().getName());
                value.setCount(entry.getValue().size());
                valuesList.add(value);
            }
            facet.setValues(valuesList);
            facetsList.add(facet);
        }
        return facetsList;
    }

    private Map<Field, Vocabulary.Type> getExtraVocabularyFieldsAndTypes() {
        Map<Field, Vocabulary.Type> vocabularyFieldsAndTypes = new HashMap<>();

        for (Field field : getFields()) {
            String vocabularyName = field.getForm().getVocabulary();
            if (field.getParent() != null && field.getAccessPath().startsWith("extras") && vocabularyName != null && !"".equals(vocabularyName)) {
                try {
                    vocabularyFieldsAndTypes.put(field, Vocabulary.Type.fromString(vocabularyName));
                } catch (IllegalArgumentException e) {
                    logger.debug("vocabulary '" + vocabularyName + "' does not exist, skipping this value", e);
                }
            }
        }
        return vocabularyFieldsAndTypes;
    }

    @Override
    public List<Facet> createExtraFacets(List<UiService> services) { // TODO: probably delete this
        List<Field> fieldsList = getFields();
        List<Field> vocabularyFields = new ArrayList<>();
//        Map<String, Map<String, Integer>> valuesMap = new HashMap<>();
        // A map of Vocabulary types containing maps of vocabulary ids and their occurrence number.
        Map<String, Map<String, Integer>> valuesMap = new HashMap<>();

        for (Field field : fieldsList) {
            String vocabularyName = field.getForm().getVocabulary();
            if (field.getParent() != null && field.getAccessPath().startsWith("extras") && vocabularyName != null && !"".equals(vocabularyName)) {
                try {
                    List<Vocabulary> vocabularyValues = vocabularyService.getByType(Vocabulary.Type.fromString(vocabularyName));
                    vocabularyFields.add(field);
                    valuesMap.put(field.getName(), new HashMap<>());
                    if (vocabularyValues != null) {
                        for (Vocabulary value : vocabularyValues) {
                            valuesMap.get(field.getName()).put(value.getId(), 0);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    logger.debug("vocabulary '" + vocabularyName + "' does not exist, skipping this value", e);
                }
            }
        }
//        valuesMap.values().forEach(vocabularyValuesEnsemble::putAll);

        for (UiService service : services) {
            if (service.getExtras() != null) {

                for (Map.Entry<String, Map<String, Integer>> entry : valuesMap.entrySet()) {
                    Object extraValues = service.getExtras().get(entry.getKey());
                    if (extraValues == null) {
                        continue;
                    }
                    if (List.class.isAssignableFrom(extraValues.getClass())) {
                        for (Object item : (List<?>) extraValues) {
                            if (item.equals(item.toString())) {
                                entry.getValue().put((String) item, entry.getValue().get(item) + 1);
                            }
                        }
                    } else {
                        if (extraValues.equals(extraValues.toString())) {
                            entry.getValue().put((String) extraValues, entry.getValue().get(extraValues) + 1);
                        }
                    }
                }
            }
        }

        List<Facet> facets = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> entry : valuesMap.entrySet()) {
            Facet facet = new Facet();
            facet.setField(entry.getKey());
            facet.setLabel(getExtraField(entry.getKey()).getLabel());
            List<eu.openminted.registry.core.domain.Value> values = new ArrayList<>();
            for (Map.Entry<String, Integer> vocabularyValue : entry.getValue().entrySet()) {
                eu.openminted.registry.core.domain.Value value = new eu.openminted.registry.core.domain.Value();
                value.setValue(vocabularyValue.getKey());
                value.setCount(vocabularyValue.getValue());
                value.setLabel(vocabularyService.get(vocabularyValue.getKey()).getName());
                values.add(value);
            }
            facet.setValues(values);
            facets.add(facet);
        }

        return facets;
    }

    private Map<Vocabulary, List<InfraService>> getByExtraVoc(String vocabularyType, String value) {
        Map<Vocabulary, List<InfraService>> serviceMap = new HashMap<>();
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);
        List<InfraService> services = this.infraServiceService.getAll(ff, null).getResults();

        List<Vocabulary> values = new ArrayList<>();
        if (vocabularyType == null && value == null) {
            for (Map.Entry<Field, Vocabulary.Type> entry : getExtraVocabularyFieldsAndTypes().entrySet()) {
                values.addAll(vocabularyService.getByType(entry.getValue()));
            }
        } else if (value == null) {
            values = vocabularyService.getByType(Vocabulary.Type.fromString(vocabularyType));
        } else {
            values.add(vocabularyService.get(value));
        }
        for (Vocabulary v : values) {
            serviceMap.put(v, new ArrayList<>());

            for (InfraService service : services) {
                if (service.getExtras() == null) {
                    continue;
                }
                // TODO: try using field info (from getExtraVocabularyFieldsAndTypes()) to help with optimization
                //       when searching for a value in extra fields.
                for (DynamicField field : service.getExtras()) {
                    if (valueExists(field, v)) {
                        serviceMap.get(v).add(service);
                        break;
                    }
                }
            }
        }
        return serviceMap;
    }

    @Override
    public Map<String, List<Map<String, Object>>> getServicesSnippetsByExtraVoc(String vocabularyType, String value) {
        Map<String, List<InfraService>> serviceMap = getServicesByExtraVoc(vocabularyType, value);
        Map<String, List<Map<String, Object>>> valuesMap = new HashMap<>();

        for (Map.Entry<String, List<InfraService>> v : serviceMap.entrySet()) {
            List<Map<String, Object>> serviceValues = new ArrayList<>();
            for (InfraService service : v.getValue()) {
                serviceValues.add(createServiceSnippet(service));
            }
            valuesMap.put(v.getKey(), serviceValues);
        }
        return valuesMap;
    }

    @Override
    public Map<String, List<InfraService>> getServicesByExtraVoc(String vocabularyType, String value) {
        Map<String, List<InfraService>> serviceMap = new HashMap<>();
        for (Map.Entry<Vocabulary, List<InfraService>> entry : getByExtraVoc(vocabularyType, value).entrySet()) {
            serviceMap.put(entry.getKey().getName(), entry.getValue());
        }
        return serviceMap;
    }

    private Object getFieldValues(DynamicField field) {
        Set<String> innerFieldNames = new HashSet<>();
        Field fieldInfo = getField(field.getFieldId());

        try {
            if (field.getValue().size() > 1) {
                innerFieldNames = field.getValue()
                        .stream()
                        .map(o -> (DynamicField) o)
                        .map(DynamicField::getName)
                        .collect(Collectors.toSet());
            }
        } catch (ClassCastException e) {
            logger.debug("DynamicField contains string values");
            return field.getValue();
        }

        // when a field's value is an object (with its own inner fields), a key-value map is created
        if (!innerFieldNames.isEmpty() && field.getValue().size() >= innerFieldNames.size()) {
            List<Map<String, Object>> mapList = new ArrayList<>();
            for (int k = 0; k < field.getValue().size() / innerFieldNames.size(); k++) {
                Map<String, Object> keyValues = new HashMap<>();
                for (int i = 0; i < innerFieldNames.size(); i++) {
                    DynamicField innerField = (DynamicField) field.getValue().get(k * innerFieldNames.size() + i);
                    if (innerField.getValue().size() == 1) {
                        keyValues.put(innerField.getName(), innerField.getValue().get(0));
                    } else { // recurse here for more complex objects
                        keyValues.put(innerField.getName(), getFieldValues(innerField));
                    }
                }
                mapList.add(keyValues);
            }
            return mapList;
        } else {
            if (fieldInfo != null && !fieldInfo.getMultiplicity() && field.getValue() != null && !field.getValue().isEmpty()) {
                return field.getValue().get(0);
            }
        }
        return field.getValue();
    }

    @Override // TODO: refactoring
    public List<GroupedFields<FieldGroup>> getModel() {
        List<GroupedFields<FieldGroup>> groupedFieldGroups = new ArrayList<>();
        List<GroupedFields<Field>> groupedFieldsList = getFlatModel();

        for (GroupedFields<Field> groupedFields : groupedFieldsList) {
            GroupedFields<FieldGroup> groupedFieldGroup = new GroupedFields<>();

            groupedFieldGroup.setGroup(groupedFields.getGroup());
            List<FieldGroup> fieldGroups = createFieldGroups(groupedFields.getFields());
            groupedFieldGroup.setFields(fieldGroups);

            int total = 0;
            for (Field f : groupedFields.getFields()) {
                if (f.getForm().getMandatory() != null && f.getForm().getMandatory()
                        && f.getType() != null && !f.getType().equals("composite")) {
                    total += 1;
                }
            }

            int topLevel = 0;
            for (FieldGroup fg : fieldGroups) {
                if (fg.getField().getForm().getMandatory() != null && fg.getField().getForm().getMandatory()) {
                    topLevel += 1;
                }
            }
            RequiredFields requiredFields = new RequiredFields(topLevel, total);
            groupedFieldGroup.setRequired(requiredFields);

            groupedFieldGroups.add(groupedFieldGroup);
        }


        return groupedFieldGroups;
    }

    // TODO: recurse until starting list length == returned list length
    List<FieldGroup> createFieldGroups(List<Field> fields) {
        Map<Integer, FieldGroup> fieldGroupMap = new HashMap<>();
        Map<Integer, List<FieldGroup>> groups = new HashMap<>();
        Set<Integer> ids = fields
                .stream()
                .map(Field::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Integer id : ids) {
            groups.put(id, new ArrayList<>());
        }

        for (Iterator<Field> it = fields.iterator(); it.hasNext(); ) {
            Field field = it.next();
            FieldGroup fieldGroup = new FieldGroup(field);
            if (ids.contains(field.getParentId())) {
                groups.get(field.getParentId()).add(fieldGroup);
            } else {
                fieldGroupMap.put(field.getId(), fieldGroup);
            }
        }

        for (Map.Entry<Integer, List<FieldGroup>> entry : groups.entrySet()) {
            fieldGroupMap.get(entry.getKey()).setSubFieldGroups(entry.getValue());
        }


        return new ArrayList<>(fieldGroupMap.values());

    }

    @Override
    public List<GroupedFields<Field>> getFlatModel() {
        List<Group> groups = getGroups();
        List<GroupedFields<Field>> groupedFieldsList = new ArrayList<>();

        if (groups != null) {
            for (Group group : groups) {
                GroupedFields<Field> groupedFields = new GroupedFields<>();

                groupedFields.setGroup(group);
                groupedFields.setFields(getFieldsByGroup(group.getId()));

                groupedFieldsList.add(groupedFields);
            }
        }

        return groupedFieldsList;
    }

    @Override
    public List<Group> getGroups() {
        return readGroups(directory + "/" + FILENAME_GROUPS);
    }

    @Override
    public List<Field> getFields() { // TODO: refactor
        List<Field> allFields = readFields(directory + "/" + FILENAME_FIELDS);

        Map<Integer, Field> fieldMap = new HashMap<>();
        for (Field field : allFields) {
            fieldMap.put(field.getId(), field);
        }
        for (Field f : allFields) {
            if (f.getForm().getDependsOn() != null) {
                // f -> dependsOn
                FieldIdName dependsOn = f.getForm().getDependsOn();

                // affectingField is the field that 'f' dependsOn
                // meaning the field that affects 'f'
                Field affectingField = fieldMap.get(dependsOn.getId());
                dependsOn.setName(affectingField.getName());

                FieldIdName affects = new FieldIdName(f.getId(), f.getName());
                if (affectingField.getForm().getAffects() == null) {
                    affectingField.getForm().setAffects(new ArrayList<>());
                }
                affectingField.getForm().getAffects().add(affects);

            }
        }

        for (Field field : allFields) {
            String accessPath = field.getName();
            Field parentField = field;
            int counter = 0;
            while (parentField.getParent() != null && counter < allFields.size()) {
                counter++;
                accessPath = String.join(".", parentField.getParent(), accessPath);
                for (Field temp : allFields) {
                    if (temp.getName().equals(parentField.getParent())) {
                        parentField = temp;
                        break;
                    }
                }
            }
            if (counter >= allFields.size()) {
                throw new RuntimeException("The json model located at '" + directory + "/" + FILENAME_FIELDS +
                        "' contains errors in the 'parent' fields...\nPlease fix it and try again.");
            }

            // FIXME: implement this properly. Check if infraService is needed or not and decide what to do.
            accessPath = accessPath.replaceFirst("\\w+\\.", ""); // used to remove infraService entry

            field.setAccessPath(accessPath);
        }
        return allFields;
    }


    public List<Field> getFieldsByGroup(String groupId) {
        List<Field> allFields = getFields();

        return allFields
                .stream()
                .filter(field -> field.getForm() != null)
                .filter(field -> field.getForm().getGroup() != null)
                .filter(field -> field.getForm().getGroup().equals(groupId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Field> createFields(String className, String parent) throws ClassNotFoundException {
        List<Field> fields = new LinkedList<>();
        Class<?> clazz = Class.forName("eu.einfracentral.domain." + className);


        if (clazz.getSuperclass().getName().startsWith("eu.einfracentral.domain.Bundle")) {
            String name = clazz.getGenericSuperclass().getTypeName();
            name = name.replaceFirst(".*\\.", "").replace(">", "");
            List<Field> subfields = createFields(name, name);
            fields.addAll(subfields);
        }

        if (clazz.getSuperclass().getName().startsWith("eu.einfracentral.domain")) {
            String name = clazz.getSuperclass().getName().replaceFirst(".*\\.", "");
            List<Field> subfields = createFields(name, name);
            fields.addAll(subfields);
        }

        java.lang.reflect.Field[] classFields = clazz.getDeclaredFields();
        for (java.lang.reflect.Field field : classFields) {
            Field uiField = new Field();

//            field.setAccessible(true);
            uiField.setName(field.getName());
            uiField.setParent(parent);

            FieldValidation annotation = field.getAnnotation(FieldValidation.class);

            if (annotation != null) {
                uiField.getForm().setMandatory(!annotation.nullable());

                if (annotation.containsId() && Vocabulary.class.equals(annotation.idClass())) {
                    VocabularyValidation vvAnnotation = field.getAnnotation(VocabularyValidation.class);
                    if (vvAnnotation != null) {
                        uiField.getForm().setVocabulary(vvAnnotation.type().getKey());
                    }
                    uiField.setType("VOCABULARY");
                } else if (!field.getType().getName().contains("eu.einfracentral.domain.Identifiable")) {
                    String type = field.getType().getName();

                    if (Collection.class.isAssignableFrom(field.getType())) {
                        uiField.setMultiplicity(true);
                        type = field.getGenericType().getTypeName();
                        type = type.replaceFirst(".*<", "");
                        type = type.substring(0, type.length() - 1);
                    }
                    String typeName = type.replaceFirst(".*\\.", "").replaceAll("[<>]", "");
                    uiField.setType(typeName);

                    if (type.startsWith("eu.einfracentral.domain")) {
//                        uiField.getForm().setSubgroup(typeName);
                        List<Field> subfields = createFields(typeName, field.getName());
                        fields.addAll(subfields);
                    }
                }

            }
            fields.add(uiField);
        }
        return fields;
    }

    @Override
    @Cacheable(value = CACHE_FOR_UI)
    public Map<String, List<Value>> getControlValuesMap() {
        Map<String, List<Value>> controlValues = new HashMap<>();
        List<Value> values;
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(maxQuantity);

        // add providers
        values = this.providerService.getAll(ff, null).getResults()
                .parallelStream()
                .map(value -> new Value(value.getId(), value.getProvider().getName()))
                .collect(Collectors.toList());
        controlValues.put("Provider", values);

        // add services
        ff.addFilter("active", true);
        ff.addFilter("latest", true);
        values = this.infraServiceService.getAll(ff, null).getResults()
                .parallelStream()
                .map(value -> new Value(value.getId(), value.getService().getName()))
                .collect(Collectors.toList());
        controlValues.put("Service", values);


        // add all vocabularies
        for (Map.Entry<Vocabulary.Type, List<Vocabulary>> entry : vocabularyService.getAllVocabulariesByType().entrySet()) {
            values = entry.getValue()
                    .parallelStream()
                    .map(v -> new Value(v.getId(), v.getName(), v.getParentId()))
                    .collect(Collectors.toList());
            controlValues.put(entry.getKey().getKey(), values);
        }

        return controlValues;
    }

    @Override
    @Cacheable(cacheNames = CACHE_FOR_UI, key = "#type")
    public List<Value> getControlValues(String type) {
        List<Value> values = new ArrayList<>();
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(maxQuantity);
        if (Vocabulary.Type.exists(type)) {
            List<Vocabulary> vocabularies = this.vocabularyService.getByType(Vocabulary.Type.fromString(type));
            vocabularies.forEach(v -> values.add(new Value(v.getId(), v.getName())));
        } else if (type.equalsIgnoreCase("provider")) {
            List<ProviderBundle> providers = this.providerService.getAll(ff, null).getResults();
            providers.forEach(v -> values.add(new Value(v.getId(), v.getProvider().getName())));
        } else if (type.equalsIgnoreCase("service") || type.equalsIgnoreCase("resource")) {
            List<InfraService> services = this.infraServiceService.getAll(ff, null).getResults();
            services.forEach(v -> values.add(new Value(v.getId(), v.getService().getName())));
        }
        return values;
    }

    // TODO: optimize
    @Override
    public List<Value> getControlValues(String type, Boolean used) {
        List<Value> usedValues = new ArrayList<>();

        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);
        List<InfraService> services = this.infraServiceService.getAll(ff, null).getResults();
        List<Vocabulary> values = vocabularyService.getByType(Vocabulary.Type.fromString(type));

        if (used == null) {
            return values
                    .stream()
                    .map(vocabulary -> new Value(vocabulary.getId(), vocabulary.getName(), vocabulary.getParentId()))
                    .collect(Collectors.toList());
        }

        for (Vocabulary v : values) {
            boolean found = false;

            for (InfraService service : services) {
                if (service.getExtras() == null) {
                    continue;
                }
                for (DynamicField field : service.getExtras()) {
                    if (valueExists(field, v)) {
                        usedValues.add(new Value(v.getId(), v.getName(), v.getParentId()));
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
        }

        if (!used) {
            List<Value> vocValues = values
                    .stream()
                    .map(vocabulary -> new Value(vocabulary.getId(), vocabulary.getName(), vocabulary.getParentId()))
                    .collect(Collectors.toList());
            return ListUtils.remainingItems(vocValues, usedValues);
        }

        return usedValues;
    }

    // TODO: optimize
    private boolean valueExists(DynamicField field, Vocabulary vocabulary) {
        boolean result = false;
//        Field fieldDesc = getField(field.getFieldId());
        Field fieldDesc = getExtraField(field.getName());
        if (fieldDesc != null
                && fieldDesc.getType().equals("vocabulary")
                && fieldDesc.getForm().getVocabulary() != null
                && fieldDesc.getForm().getVocabulary().equals(vocabulary.getType())) {
            for (Object value : field.getValue()) {
                if (value.equals(vocabulary.getId())) {
                    result = true;
                    break;
                }
            }
        } else {
            for (Object df : field.getValue()) {
                try {
                    result = valueExists((DynamicField) df, vocabulary);
                } catch (Exception e) {
                    result = false;
                }
            }
        }
        return result;
    }
}
